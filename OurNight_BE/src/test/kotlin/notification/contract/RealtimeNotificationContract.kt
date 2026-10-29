package notification.contract

import java.time.Duration

/**
 * Transport-neutral vocabulary shared by MVC and future WebFlux guarantee tests.
 */
data class GuaranteeScenario(
    val name: String,
    val problem: String,
    val disturbance: String,
    val guarantees: List<String>,
)

data class NotificationMessage(
    val id: String,
    val name: String = "TEST_EVENT",
    val data: String = """{"id":"$id"}""",
)

enum class ClientDemand {
    CONTINUOUS,
    PAUSED,
}

data class TestClient(
    val id: String,
    val demand: ClientDemand,
)

data class ClientObservation(
    val connected: Boolean,
    val receivedMessageIds: List<String>,
)

/**
 * An implementation adapter maps these operations to MVC SseEmitter or WebFlux.
 *
 * PAUSED means that the client supplies no downstream consumption:
 * - MVC adapter: do not drain the outbound queue.
 * - WebFlux adapter: stop downstream demand/consumption.
 */
interface RealtimeNotificationDriver : AutoCloseable {
    fun connect(client: TestClient)

    fun publish(message: NotificationMessage): String

    fun observe(clientId: String): ClientObservation

    fun  awaitObservation(
        clientId: String,
        timeout: Duration = Duration.ofSeconds(10),
        predicate: (ClientObservation) -> Boolean,
    ): ClientObservation {
        val deadline = System.nanoTime() + timeout.toNanos()
        var current = observe(clientId)

        while (!predicate(current) && System.nanoTime() < deadline) {
            Thread.sleep(5)
            current = observe(clientId)
        }

        return current
    }

    override fun close() = Unit
}
