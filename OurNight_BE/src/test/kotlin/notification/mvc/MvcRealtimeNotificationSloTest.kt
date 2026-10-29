package notification.mvc

import com.ournight.api_server.domain.notification.service.SseOrchestrator
import com.ournight.api_server.domain.notification.service.streamKey
import notification.contract.NotificationMessage
import notification.contract.RealtimeNotificationSloContract
import notification.contract.RealtimeNotificationSloDriver
import notification.contract.ReceivedNotification
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.data.redis.connection.stream.StreamRecords
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@Testcontainers
@SpringBootTest(
    classes = [MvcSlowClientTestApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "pos.sse.outbound-queue-size=500",
        "pos.sse.replay-queue-size=500",
    ],
)
class MvcRealtimeNotificationSloTest : RealtimeNotificationSloContract() {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var orchestrator: SseOrchestrator

    @Autowired
    private lateinit var redisTemplate: StringRedisTemplate

    override fun createDriver(): RealtimeNotificationSloDriver =
        MvcRealtimeNotificationSloDriver(port, orchestrator, redisTemplate)

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

private class MvcRealtimeNotificationSloDriver(
    private val port: Int,
    private val orchestrator: SseOrchestrator,
    private val redisTemplate: StringRedisTemplate,
) : RealtimeNotificationSloDriver {
    private val storeId = UUID.randomUUID()
    private val http = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private val deliveries = CopyOnWriteArrayList<ReceivedNotification>()
    private var source: EventSource? = null
    private var connectionSequence = 0

    init {
        val seed = StreamRecords.newRecord()
            .ofMap(mapOf("eventId" to "seed", "type" to "PING", "payload" to "{}"))
            .withStreamKey(streamKey(storeId.toString()))
        redisTemplate.opsForStream<String, String>().add(seed)
    }

    override fun connect(lastEventId: String?) {
        val opened = CountDownLatch(1)
        val ticket = orchestrator.createTicket(storeId)
        val connectionId = "slo-${++connectionSequence}"
        val lastEventQuery = lastEventId?.let { "&lastEventId=$it" }.orEmpty()
        val url = "http://127.0.0.1:$port/test/sse/$storeId" +
            "?ticket=$ticket&connectionId=$connectionId$lastEventQuery"
        val request = Request.Builder().url(url).build()

        source = EventSources.createFactory(http).newEventSource(
            request,
            SloRecordingListener(opened, deliveries),
        )

        check(opened.await(5, TimeUnit.SECONDS)) { "SLO SSE client did not connect" }
    }

    override fun disconnect() {
        source?.cancel()
        source = null

        // A TCP close is not visible to MVC until the server writes again.
        // Force that observation before creating the offline replay gap.
        orchestrator.sendHeartbeat()

        val deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos()
        while (
            orchestrator.hasConnection(storeId, "slo-$connectionSequence") &&
            System.nanoTime() < deadline
        ) {
            Thread.sleep(5)
        }
        check(!orchestrator.hasConnection(storeId, "slo-$connectionSequence")) {
            "SLO SSE client did not disconnect"
        }
    }

    override fun publish(message: NotificationMessage): String {
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

    override fun clearReceived() {
        deliveries.clear()
    }

    override fun received(): List<ReceivedNotification> = deliveries.toList()

    override fun close() {
        source?.cancel()
        redisTemplate.delete(streamKey(storeId.toString()))
        http.dispatcher.executorService.shutdownNow()
    }
}

private class SloRecordingListener(
    private val opened: CountDownLatch,
    private val deliveries: CopyOnWriteArrayList<ReceivedNotification>,
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
            deliveries += ReceivedNotification(id, System.nanoTime())
        }
    }
}
