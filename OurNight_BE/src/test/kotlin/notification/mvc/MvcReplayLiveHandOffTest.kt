package notification.mvc

import com.ournight.api_server.domain.notification.service.SseOrchestrator
import com.ournight.api_server.domain.notification.service.streamKey
import notification.contract.NotificationMessage
import notification.contract.ReplayLiveHandOffContract
import notification.contract.ReplayLiveHandOffDriver
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.data.redis.connection.stream.StreamRecords
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@Testcontainers
@SpringBootTest(
    classes = [MvcSlowClientTestApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "pos.sse.outbound-queue-size=32",
        "pos.sse.replay-queue-size=32",
    ],
)
class MvcReplayLiveHandOffTest : ReplayLiveHandOffContract() {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var orchestrator: SseOrchestrator

    @Autowired
    private lateinit var redisTemplate: StringRedisTemplate

    override fun createDriver(): ReplayLiveHandOffDriver =
        MvcReplayLiveHandOffDriver(
            port = port,
            orchestrator = orchestrator,
            redisTemplate = redisTemplate,
        )

    companion object {
        @Container
        @JvmStatic
        val redis = KotlinGenericContainer(DockerImageName.parse("redis:7.2-alpine"))
            .withExposedPorts(6379)

        @DynamicPropertySource
        @JvmStatic
        fun redisProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.firstMappedPort }
        }
    }
}

private class MvcReplayLiveHandOffDriver(
    private val port: Int,
    private val orchestrator: SseOrchestrator,
    private val redisTemplate: StringRedisTemplate,
) : ReplayLiveHandOffDriver {
    private val storeId = UUID.randomUUID()
    private val keeperIds = CopyOnWriteArrayList<String>()
    private val replayIds = CopyOnWriteArrayList<String>()
    private val replayPaused = CountDownLatch(1)
    private val replayResume = CountDownLatch(1)
    private val firstReplaySend = AtomicBoolean(true)
    private val replayCompletion = AtomicReference<Runnable>()
    private val replayExecutor = Executors.newSingleThreadExecutor()
    private val http = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var keeperSource: EventSource? = null
    private var replayTask: Future<*>? = null

    override fun append(message: NotificationMessage): String {
        val record = StreamRecords.newRecord()
            .ofMap(
                mapOf(
                    "eventId" to "outbox-${message.id}",
                    "type" to message.name,
                    "payload" to message.data,
                ),
            )
            .withStreamKey(streamKey(storeId.toString()))

        return checkNotNull(redisTemplate.opsForStream<String, String>().add(record)).value
    }

    override fun connectKeeper() {
        val opened = CountDownLatch(1)
        val ticket = orchestrator.createTicket(storeId)
        val url = "http://127.0.0.1:$port/test/sse/$storeId" +
            "?ticket=$ticket&connectionId=keeper"
        val request = Request.Builder().url(url).build()

        keeperSource = EventSources.createFactory(http).newEventSource(
            request,
            RecordingListener(opened, keeperIds),
        )

        check(opened.await(5, TimeUnit.SECONDS)) {
            "Keeper SSE connection did not open"
        }
    }

    override fun awaitKeeperReceived(expectedIds: List<String>, timeout: Duration) {
        val deadline = System.nanoTime() + timeout.toNanos()

        while (keeperIds.size < expectedIds.size && System.nanoTime() < deadline) {
            Thread.sleep(5)
        }

        check(keeperIds.toList() == expectedIds) {
            "Keeper did not observe the expected store-reader progress. " +
                "expected=$expectedIds actual=${keeperIds.toList()}"
        }
    }

    override fun connectReplayFrom(lastEventId: String) {
        val ticket = orchestrator.createTicket(storeId)

        replayTask = replayExecutor.submit {
            Mockito.mockConstruction(SseEmitter::class.java) { emitter, _ ->
                Mockito.doAnswer { invocation ->
                    replayCompletion.set(invocation.getArgument(0))
                    null
                }.`when`(emitter).onCompletion(Mockito.any(Runnable::class.java))

                Mockito.doAnswer { invocation ->
                    val event = ParsedSseEvent.from(
                        invocation.getArgument<SseEmitter.SseEventBuilder>(0),
                    )

                    if (
                        event.name == "TEST_EVENT" &&
                        firstReplaySend.compareAndSet(true, false)
                    ) {
                        replayPaused.countDown()
                        replayResume.await()
                    }

                    if (event.name == "TEST_EVENT" && event.id != null) {
                        replayIds.add(event.id)
                    }
                    null
                }.`when`(emitter).send(
                    Mockito.any(SseEmitter.SseEventBuilder::class.java),
                )
            }.use {
                orchestrator.subscribe(
                    expectedStoreId = storeId,
                    ticket = ticket,
                    connectionId = "replay",
                    lastEventId = lastEventId,
                )
            }
        }
    }

    override fun awaitReplayPaused(timeout: Duration) {
        check(replayPaused.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            "Replay connection did not pause on its first replay event"
        }
    }

    override fun resumeReplay() {
        replayResume.countDown()
    }

    override fun replayReceivedIds(): List<String> {
        replayTask?.let { task ->
            if (task.isDone) {
                task.get()
            }
        }
        return replayIds.toList()
    }

    override fun close() {
        replayResume.countDown()
        replayCompletion.get()?.run()
        keeperSource?.cancel()
        replayTask?.cancel(true)
        replayExecutor.shutdownNow()
        http.dispatcher.executorService.shutdownNow()
    }
}

private class RecordingListener(
    private val opened: CountDownLatch,
    private val receivedIds: CopyOnWriteArrayList<String>,
) : EventSourceListener() {
    override fun onOpen(eventSource: EventSource, response: Response) {
        opened.countDown()
    }

    override fun onEvent(
        eventSource: EventSource,
        id: String?,
        type: String?,
        data: String,
    ) {
        if (type == "TEST_EVENT" && id != null) {
            receivedIds.add(id)
        }
    }
}

private data class ParsedSseEvent(
    val name: String?,
    val id: String?,
) {
    companion object {
        private val eventPattern = Regex("""event:([^\n]+)""")
        private val idPattern = Regex("""id:([^\n]+)""")

        fun from(builder: SseEmitter.SseEventBuilder): ParsedSseEvent {
            val encoded = builder.build()
                .joinToString(separator = "") { part -> part.data.toString() }

            return ParsedSseEvent(
                name = eventPattern.find(encoded)?.groupValues?.get(1),
                id = idPattern.find(encoded)?.groupValues?.get(1),
            )
        }
    }
}
