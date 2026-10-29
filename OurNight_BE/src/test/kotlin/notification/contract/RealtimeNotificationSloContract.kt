package notification.contract

import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

data class ReceivedNotification(
    val streamId: String,
    val receivedAtNanos: Long,
)

interface RealtimeNotificationSloDriver : AutoCloseable {
    fun connect(lastEventId: String? = null)

    fun disconnect()

    fun publish(message: NotificationMessage): String

    fun clearReceived()

    fun received(): List<ReceivedNotification>

    fun awaitReceived(
        expectedCount: Int,
        timeout: Duration,
    ): List<ReceivedNotification> {
        val deadline = System.nanoTime() + timeout.toNanos()
        var current = received()

        while (current.size < expectedCount && System.nanoTime() < deadline) {
            Thread.sleep(5)
            current = received()
        }

        return current
    }
}

abstract class RealtimeNotificationSloContract {
    protected abstract fun createDriver(): RealtimeNotificationSloDriver

    @Test
    fun `healthy client meets notification latency and delivery guarantees`() {
        val driver = createDriver()

        try {
            driver.connect()

            // Warm up the HTTP, Redis reader, and sender paths before measuring.
            driver.publish(NotificationMessage("warm-up"))
            assertEquals(1, driver.awaitReceived(1, Duration.ofSeconds(5)).size)
            driver.clearReceived()

            val sentAtByStreamId = linkedMapOf<String, Long>()
            repeat(NORMAL_SAMPLE_COUNT) { sequence ->
                val sentAt = System.nanoTime()
                val streamId = driver.publish(NotificationMessage("normal-$sequence"))
                sentAtByStreamId[streamId] = sentAt
            }

            val observed = driver.awaitReceived(NORMAL_SAMPLE_COUNT, Duration.ofSeconds(10))
            val observedIds = observed.map(ReceivedNotification::streamId)
            val expectedIds = sentAtByStreamId.keys.toList()
            val latenciesMs = observed.map { delivery ->
                Duration.ofNanos(
                    delivery.receivedAtNanos - checkNotNull(sentAtByStreamId[delivery.streamId]),
                ).toMillis()
            }

            assertAll(
                {
                    assertEquals(
                        expectedIds,
                        observedIds,
                        "Healthy client event loss, duplicate, or reordering detected",
                    )
                },
                {
                    assertTrue(
                        percentile(latenciesMs, 95) <= NORMAL_P95_MS,
                        "Normal notification p95 exceeded ${NORMAL_P95_MS}ms: $latenciesMs",
                    )
                },
                {
                    assertTrue(
                        percentile(latenciesMs, 99) <= NORMAL_P99_MS,
                        "Normal notification p99 exceeded ${NORMAL_P99_MS}ms: $latenciesMs",
                    )
                },
            )
        } finally {
            driver.close()
        }
    }

    @Test
    fun `reconnect replays a critical offline gap within the sync SLO`() {
        val driver = createDriver()

        try {
            driver.connect()
            val anchorId = driver.publish(NotificationMessage("reconnect-anchor"))
            assertEquals(1, driver.awaitReceived(1, Duration.ofSeconds(5)).size)
            driver.disconnect()
            driver.clearReceived()

            val expectedIds = (1..RECONNECT_SAMPLE_COUNT).map { sequence ->
                driver.publish(NotificationMessage("offline-$sequence"))
            }

            val syncStartedAt = System.nanoTime()
            driver.connect(lastEventId = anchorId)
            val observed = driver.awaitReceived(RECONNECT_SAMPLE_COUNT, Duration.ofSeconds(10))
            val observedIds = observed.map(ReceivedNotification::streamId)
            val syncLatenciesMs = observed.map { delivery ->
                Duration.ofNanos(delivery.receivedAtNanos - syncStartedAt).toMillis()
            }

            assertAll(
                {
                    assertEquals(
                        expectedIds,
                        observedIds,
                        "Reconnect replay lost, duplicated, or reordered a critical event",
                    )
                },
                {
                    assertTrue(
                        percentile(syncLatenciesMs, 95) <= RECONNECT_P95_MS,
                        "Reconnect critical sync p95 exceeded ${RECONNECT_P95_MS}ms: $syncLatenciesMs",
                    )
                },
                {
                    assertTrue(
                        percentile(syncLatenciesMs, 99) <= RECONNECT_P99_MS,
                        "Reconnect critical sync p99 exceeded ${RECONNECT_P99_MS}ms: $syncLatenciesMs",
                    )
                },
            )
        } finally {
            driver.close()
        }
    }

    private fun percentile(values: List<Long>, percentile: Int): Long {
        if (values.isEmpty()) return Long.MAX_VALUE
        val sorted = values.sorted()
        val index = kotlin.math.ceil(percentile / 100.0 * sorted.size).toInt() - 1
        return sorted[index.coerceIn(sorted.indices)]
    }

    private companion object {
        const val NORMAL_SAMPLE_COUNT = 100
        const val RECONNECT_SAMPLE_COUNT = 20
        const val NORMAL_P95_MS = 1_000L
        const val NORMAL_P99_MS = 2_000L
        const val RECONNECT_P95_MS = 3_000L
        const val RECONNECT_P99_MS = 5_000L
    }
}
