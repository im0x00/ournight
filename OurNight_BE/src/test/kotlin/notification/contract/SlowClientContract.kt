package notification.contract

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

abstract class SlowClientContract {
    protected abstract fun createDriver(outboundCapacity: Int): RealtimeNotificationDriver

    private val scenario = GuaranteeScenario(
        name = "slow client isolation",
        problem = "one client consumes slower than the store event rate",
        disturbance = "pause one client and publish beyond its outbound capacity",
        guarantees = listOf(
            "the slow client is removed when its bounded outbound queue overflows",
            "only the slow client is terminated by overflow",
            "the healthy client receives every admitted event in order",
            "fan-out continues after the slow client is removed",
        ),
    )

    @Test
    fun `slow client overflow is isolated from a healthy client`() {
        val capacity = 3
        val driver = createDriver(outboundCapacity = capacity)

        try {
            driver.connect(TestClient("slow", ClientDemand.PAUSED))
            driver.connect(TestClient("healthy", ClientDemand.CONTINUOUS))

            val burst = (1..capacity + 2).map { sequence ->
                val id = "$sequence-0"
                NotificationMessage(
                    id = id,
                    data = """{"id":"$id","padding":"${"x".repeat(512 * 1024)}"}""",
                )
            }
            val admittedIds = burst.map(driver::publish)

            val slow = driver.awaitObservation("slow") { observation ->
                !observation.connected
            }
            val healthy = driver.awaitObservation("healthy") { observation ->
                observation.receivedMessageIds.size == burst.size
            }

            val probe = NotificationMessage(id = "${capacity + 3}-0")
            val probeId = driver.publish(probe)

            val healthyAfterProbe = driver.awaitObservation("healthy") { observation ->
                observation.receivedMessageIds.size == burst.size + 1
            }

            assertAll(
                {
                    assertFalse(
                        slow.connected,
                        "${scenario.name}: ${scenario.guarantees[1]}",
                    )
                },
                {
                    assertTrue(
                        healthy.connected,
                        "${scenario.name}: a healthy client must remain connected",
                    )
                },
                {
                    assertEquals(
                        admittedIds,
                        healthy.receivedMessageIds,
                        "${scenario.name}: ${scenario.guarantees[2]}",
                    )
                },
                {
                    assertEquals(
                        admittedIds + probeId,
                        healthyAfterProbe.receivedMessageIds,
                        "${scenario.name}: ${scenario.guarantees[3]}",
                    )
                },
            )
        } finally {
            driver.close()
        }
    }
}
