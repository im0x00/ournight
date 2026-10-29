package notification.contract

import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Objective: Resilience
 * Scope: Integration
 * Generation: Example-based fault injection
 */
abstract class RedisRelayAtomicityGapContract {
    protected abstract fun createDriver(): RedisRelayAtomicityGapDriver

    private val scenario = GuaranteeScenario(
        name = "Redis relay atomicity gap",
        problem = "DB Outbox publish state and Redis XADD do not share one atomic transaction",
        disturbance = "fail either XADD or the DB transaction after XADD, then retry",
        guarantees = listOf(
            "Outbox remains unpublished until markPublished commits",
            "an unpublished event is retried without logical event loss",
            "retry duplicates carry the same outbox event ID",
            "the consumer domain effect occurs once",
            "the consumer cursor advances to the newest processed Redis Stream ID",
        ),
    )

    @Test
    fun `XADD failure leaves the outbox pending and recovery restores progress`() {
        val driver = createDriver()

        try {
            val outboxEventId = driver.commitOutboxEvent()

            driver.cutRedisConnection()
            driver.runRelay()

            val failed = driver.observeOutbox(outboxEventId)
            driver.restoreRedisConnection()
            val recordsAfterFailedAttempt = driver.observeRedisRecords(outboxEventId)

            assertAll(
                {
                    assertFalse(
                        failed.published,
                        "${scenario.name}: ${scenario.guarantees[0]}",
                    )
                },
                {
                    assertTrue(
                        failed.attempts > 0,
                        "${scenario.name}: failed XADD must remain retryable",
                    )
                },
            )

            driver.makeRetryEligible(outboxEventId)
            driver.runRelay()
            driver.consumeAvailableRecords()

            val recovered = driver.observeOutbox(outboxEventId)
            val records = driver.observeRedisRecords(outboxEventId)
            val consumer = driver.observeConsumer()

            assertAll(
                {
                    assertTrue(
                        recovered.published,
                        "${scenario.name}: ${scenario.guarantees[1]}",
                    )
                },
                {
                    assertEquals(
                        recordsAfterFailedAttempt.size + 1,
                        records.size,
                        "${scenario.name}: retry must append a new Redis record",
                    )
                },
                {
                    assertEquals(
                        setOf(outboxEventId),
                        records.map(RedisRelayRecord::outboxEventId).toSet(),
                        "${scenario.name}: logical identity must survive ambiguous delivery and retry",
                    )
                },
                {
                    assertEquals(
                        1,
                        consumer.domainEffectCount,
                        "${scenario.name}: ${scenario.guarantees[3]}",
                    )
                },
                {
                    assertEquals(
                        records.last().streamId,
                        consumer.cursor,
                        "${scenario.name}: ${scenario.guarantees[4]}",
                    )
                },
            )
        } finally {
            driver.close()
        }
    }

    @Test
    fun `DB rollback after XADD produces a harmless duplicate on retry`() {
        val driver = createDriver()

        try {
            val outboxEventId = driver.commitOutboxEvent()

            driver.runRelayAndRollbackDatabaseTransaction()

            val afterRollback = driver.observeOutbox(outboxEventId)
            val firstDelivery = driver.observeRedisRecords(outboxEventId)

            assertAll(
                {
                    assertFalse(
                        afterRollback.published,
                        "${scenario.name}: ${scenario.guarantees[0]}",
                    )
                },
                {
                    assertNull(
                        afterRollback.redisStreamId,
                        "${scenario.name}: rolled-back markPublished state must not leak",
                    )
                },
                {
                    assertEquals(
                        1,
                        firstDelivery.size,
                        "${scenario.name}: XADD survives the DB rollback",
                    )
                },
            )

            driver.runRelay()
            driver.consumeAvailableRecords()

            val published = driver.observeOutbox(outboxEventId)
            val duplicates = driver.observeRedisRecords(outboxEventId)
            val consumer = driver.observeConsumer()

            assertAll(
                {
                    assertTrue(
                        published.published,
                        "${scenario.name}: ${scenario.guarantees[1]}",
                    )
                },
                {
                    assertEquals(
                        2,
                        duplicates.size,
                        "${scenario.name}: retry may redeliver the logical event",
                    )
                },
                {
                    assertEquals(
                        setOf(outboxEventId),
                        duplicates.map(RedisRelayRecord::outboxEventId).toSet(),
                        "${scenario.name}: ${scenario.guarantees[2]}",
                    )
                },
                {
                    assertTrue(
                        compareRedisStreamIds(
                            duplicates.first().streamId,
                            duplicates.last().streamId,
                        ) < 0,
                        "${scenario.name}: duplicate delivery receives a newer cursor",
                    )
                },
                {
                    assertEquals(
                        duplicates.last().streamId,
                        published.redisStreamId,
                        "${scenario.name}: Outbox records the committed retry cursor",
                    )
                },
                {
                    assertEquals(
                        1,
                        consumer.domainEffectCount,
                        "${scenario.name}: ${scenario.guarantees[3]}",
                    )
                },
                {
                    assertEquals(
                        duplicates.last().streamId,
                        consumer.cursor,
                        "${scenario.name}: ${scenario.guarantees[4]}",
                    )
                },
            )
        } finally {
            driver.close()
        }
    }

    private fun compareRedisStreamIds(left: String, right: String): Int {
        val leftParts = left.split('-', limit = 2).map(String::toLong)
        val rightParts = right.split('-', limit = 2).map(String::toLong)
        require(leftParts.size == 2 && rightParts.size == 2)
        return compareValuesBy(
            leftParts[0] to leftParts[1],
            rightParts[0] to rightParts[1],
            { it.first },
            { it.second },
        )
    }
}

interface RedisRelayAtomicityGapDriver : AutoCloseable {
    fun commitOutboxEvent(): UUID

    fun cutRedisConnection()

    fun restoreRedisConnection()

    fun runRelay()

    fun runRelayAndRollbackDatabaseTransaction()

    fun makeRetryEligible(outboxEventId: UUID)

    fun observeOutbox(outboxEventId: UUID): OutboxObservation

    fun observeRedisRecords(outboxEventId: UUID): List<RedisRelayRecord>

    fun consumeAvailableRecords()

    fun observeConsumer(): RelayConsumerObservation
}

data class OutboxObservation(
    val published: Boolean,
    val redisStreamId: String?,
    val attempts: Int,
)

data class RedisRelayRecord(
    val streamId: String,
    val outboxEventId: UUID,
)

data class RelayConsumerObservation(
    val domainEffectCount: Int,
    val cursor: String?,
)
