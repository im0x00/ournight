package notification.mvc

import com.ournight.api_server.domain.notification.service.SseOrchestrator
import com.ournight.api_server.domain.notification.service.SseRuntimeProperties
import com.ournight.api_server.domain.notification.service.streamKey
import notification.contract.ClientDemand
import notification.contract.ClientObservation
import notification.contract.NotificationMessage
import notification.contract.RealtimeNotificationDriver
import notification.contract.SlowClientContract
import notification.contract.TestClient
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.connection.stream.StreamRecords
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.io.BufferedInputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@Testcontainers
@SpringBootTest(
    classes = [MvcSlowClientTestApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "pos.sse.outbound-queue-size=3",
        "pos.sse.replay-queue-size=3",
    ],
)

class MvcSlowClientTest : SlowClientContract() {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var orchestrator: SseOrchestrator

    @Autowired
    private lateinit var redisTemplate: StringRedisTemplate

    override fun createDriver(outboundCapacity: Int): RealtimeNotificationDriver =
        MvcHttpRedisDriver(port, orchestrator, redisTemplate)

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

class KotlinGenericContainer(image: DockerImageName) :
    GenericContainer<KotlinGenericContainer>(image)

@SpringBootConfiguration
@EnableAutoConfiguration(
    exclude = [
        DataSourceAutoConfiguration::class,
        HibernateJpaAutoConfiguration::class,
        SecurityAutoConfiguration::class,
        ManagementWebSecurityAutoConfiguration::class,
    ],
)
@Import(
    SseOrchestrator::class,
    SseRuntimeProperties::class,
    TestSseController::class,
)
class MvcSlowClientTestApplication

@RestController
@RequestMapping("/test/sse")
class TestSseController(
    private val orchestrator: SseOrchestrator,
) {
    @GetMapping("/{storeId}", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun subscribe(
        @PathVariable storeId: UUID,
        @RequestParam ticket: String,
        @RequestParam connectionId: String,
        @RequestParam(required = false) lastEventId: String?,
    ): SseEmitter = orchestrator.subscribe(
        expectedStoreId = storeId,
        ticket = ticket,
        connectionId = connectionId,
        lastEventId = lastEventId,
    )
}

/**
 * The future WebFlux implementation can reuse the contract and this HTTP/Redis
 * workload. Only the server bootstrap changes.
 */
private class MvcHttpRedisDriver(
    private val port: Int,
    private val orchestrator: SseOrchestrator,
    private val redisTemplate: StringRedisTemplate,
) : RealtimeNotificationDriver {
    private val storeId = UUID.randomUUID()
    private val http = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private val clients = ConcurrentHashMap<String, ConnectedClient>()

    init {
        val seed = StreamRecords.newRecord()
            .ofMap(
                mapOf(
                    "eventId" to "seed",
                    "type" to "PING",
                    "payload" to "{}",
                ),
            )
            .withStreamKey(streamKey(storeId.toString()))
        redisTemplate.opsForStream<String, String>().add(seed)
    }

    override fun connect(client: TestClient) {
        val ticket = orchestrator.createTicket(storeId)
        val url = "http://127.0.0.1:$port/test/sse/$storeId" +
            "?ticket=$ticket&connectionId=${client.id}"

        clients[client.id] = when (client.demand) {
            ClientDemand.PAUSED -> ConnectedClient.Paused(openPausedSocket(url))
            ClientDemand.CONTINUOUS -> openEventSource(url)
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

    override fun observe(clientId: String): ClientObservation {
        val client = checkNotNull(clients[clientId]) { "Unknown client $clientId" }
        return ClientObservation(
            connected = orchestrator.hasConnection(storeId, clientId),
            receivedMessageIds = when (client) {
                is ConnectedClient.Paused -> emptyList()
                is ConnectedClient.Continuous -> client.listener.receivedIds.toList()
            },
        )
    }

    override fun close() {
        clients.values.forEach { client ->
            when (client) {
                is ConnectedClient.Paused -> client.socket.close()
                is ConnectedClient.Continuous -> client.eventSource.cancel()
            }
        }
        http.dispatcher.executorService.shutdownNow()
    }

    private fun openPausedSocket(url: String): Socket {
        val uri = java.net.URI(url)
        val socket = Socket().apply {
            receiveBufferSize = 1024
            connect(InetSocketAddress(uri.host, uri.port))
        }

        val writer = socket.getOutputStream().bufferedWriter()
        writer.write("GET ${uri.rawPath}?${uri.rawQuery} HTTP/1.1\r\n")
        writer.write("Host: ${uri.host}:${uri.port}\r\n")
        writer.write("Accept: text/event-stream\r\n")
        writer.write("Connection: keep-alive\r\n\r\n")
        writer.flush()

        val input = BufferedInputStream(socket.getInputStream())
        val headerEnd = byteArrayOf(
            '\r'.code.toByte(),
            '\n'.code.toByte(),
            '\r'.code.toByte(),
            '\n'.code.toByte(),
        )
        var matched = 0
        while (matched < headerEnd.size) {
            val next = input.read()
            check(next >= 0) { "SSE server closed before sending HTTP headers" }
            matched = if (next.toByte() == headerEnd[matched]) matched + 1 else 0
        }
        return socket
    }

    private fun openEventSource(url: String): ConnectedClient.Continuous {
        val opened = CountDownLatch(1)
        val listener = RecordingEventSourceListener(opened)
        val request = Request.Builder().url(url).build()
        val source = EventSources.createFactory(http).newEventSource(request, listener)
        check(opened.await(5, TimeUnit.SECONDS)) { "Healthy SSE client did not connect" }
        return ConnectedClient.Continuous(source, listener)
    }

    private sealed interface ConnectedClient {
        data class Paused(val socket: Socket) : ConnectedClient

        data class Continuous(
            val eventSource: EventSource,
            val listener: RecordingEventSourceListener,
        ) : ConnectedClient
    }
}

private class RecordingEventSourceListener(
    private val opened: CountDownLatch,
) : EventSourceListener() {
    val receivedIds = CopyOnWriteArrayList<String>()

    override fun onOpen(eventSource: EventSource, response: Response) {
        opened.countDown()
    }

    override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
        if (type == "TEST_EVENT" && id != null) {
            receivedIds.add(id)
        }
    }
}
