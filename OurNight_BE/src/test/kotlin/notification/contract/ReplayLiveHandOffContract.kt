package notification.contract

import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

abstract class ReplayLiveHandOffContract {
    protected abstract fun createDriver(): ReplayLiveHandOffDriver

    private val scenario = GuaranteeScenario(
        name = "replay-live handoff",
        problem = "live events are appended after the replay tail is captured but before replay completes",
        disturbance = "pause the first replay send, append live events, then resume replay",
        guarantees = listOf(
            "there is no gap or duplicate between replay and live ranges",
            "all delivered Redis Stream cursors are strictly increasing",
            "the final delivered cursor is the latest admitted cursor",
        ),
    )

    @Test
    fun `replay to live handoff preserves continuity and ordering`() {
        val driver = createDriver()

        try {
            val resumeCursor = driver.append(NotificationMessage("resume-cursor"))
            driver.connectKeeper()

            val replayIds = (1..3).map { sequence ->
                driver.append(NotificationMessage("replay-$sequence"))
            }
            driver.awaitKeeperReceived(replayIds)

            driver.connectReplayFrom(resumeCursor)
            driver.awaitReplayPaused()

            val liveIds = (1..2).map { sequence ->
                driver.append(NotificationMessage("live-$sequence"))
            }
            driver.awaitKeeperReceived(replayIds + liveIds)

            driver.resumeReplay()

            val expected = replayIds + liveIds
            val observed = driver.awaitReplayReceived(expected.size)

            assertAll(
                {
                    assertEquals(
                        expected,
                        observed,
                        "${scenario.name}: ${scenario.guarantees[0]}",
                    )
                },
                {
                    assertTrue(
                        observed.zipWithNext().all { (left, right) ->
                            compareRedisStreamIds(left, right) < 0
                        },
                        "${scenario.name}: ${scenario.guarantees[1]}",
                    )
                },
                {
                    assertEquals(
                        liveIds.last(),
                        observed.lastOrNull(),
                        "${scenario.name}: ${scenario.guarantees[2]}",
                    )
                },
            )
        } finally {
            driver.close()
        }
    }

    private fun compareRedisStreamIds(left: String, right: String): Int {
        val (leftMillis, leftSequence) = parseRedisStreamId(left)
        val (rightMillis, rightSequence) = parseRedisStreamId(right)
        return compareValuesBy(
            leftMillis to leftSequence,
            rightMillis to rightSequence,
            { it.first },
            { it.second },
        )
    }

    private fun parseRedisStreamId(id: String): Pair<Long, Long> {
        val parts = id.split('-', limit = 2)
        require(parts.size == 2) { "Invalid Redis Stream ID: $id" }
        return parts[0].toLong() to parts[1].toLong()
    }
}

interface ReplayLiveHandOffDriver : AutoCloseable {
    fun append(message: NotificationMessage): String

    fun connectKeeper()

    fun awaitKeeperReceived(
        expectedIds: List<String>,
        timeout: Duration = Duration.ofSeconds(10),
    )

    fun connectReplayFrom(lastEventId: String)

    fun awaitReplayPaused(timeout: Duration = Duration.ofSeconds(5))

    fun resumeReplay()

    fun replayReceivedIds(): List<String>

    fun awaitReplayReceived(
        count: Int,
        timeout: Duration = Duration.ofSeconds(10),
    ): List<String> {
        val deadline = System.nanoTime() + timeout.toNanos()
        var current = replayReceivedIds()

        while (current.size < count && System.nanoTime() < deadline) {
            Thread.sleep(5)
            current = replayReceivedIds()
        }

        return current
    }
}
