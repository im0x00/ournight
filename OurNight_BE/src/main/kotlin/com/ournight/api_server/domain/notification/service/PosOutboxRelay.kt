package com.ournight.api_server.domain.notification.service

import com.ournight.api_server.domain.notification.PosStreamKey
import com.ournight.api_server.domain.notification.model.PosEventOutbox
import com.ournight.api_server.domain.notification.repository.PosEventOutboxRepository
import org.slf4j.LoggerFactory
import org.springframework.data.redis.connection.stream.StreamRecords
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Component
class PosOutboxRelay(
    private val redisTemplate: StringRedisTemplate,
    private val outboxRepository: PosEventOutboxRepository,
    private val transactionTemplate: TransactionTemplate,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val touchedKeys = ConcurrentHashMap<String, Boolean>()
    private val streamMaxLen = 10_000L
    private val batchSize = 100

    // fixedDelay is the wait only when there is nothing more to send.
    // A full batch with no failure means more rows may be waiting, so the next batch runs right away:
    // under backlog the throughput is batchSize / t, not batchSize / (500ms + t).
    // Each batch is its own transaction (joins the caller's transaction if there is one).
    @Scheduled(fixedDelay = 500)
    fun publishPendingEvents() {
        while (transactionTemplate.execute { publishBatch() } == true) {
            // keep draining
        }
    }

    // true = the batch was full and every event was published -> run again without waiting.
    // A failure returns false so a broken Redis is retried after fixedDelay, not in a tight loop.
    private fun publishBatch(): Boolean {
        val events = outboxRepository.lockPublishable(batchSize)
        if (events.isEmpty()) return false

        // A store whose event failed in this batch is skipped for the rest of the batch.
        // Publishing a later event of the same store would put it ahead of the failed one in the stream
        // and break per-store ordering. The failed event and everything after it are retried on the next run.
        val blockedStores = HashSet<UUID>()

        events.forEach { event ->
            if (event.storeId in blockedStores) return@forEach
            try {
                val streamId = publishToRedis(event)
                event.markPublished(streamId)
            } catch (e: Exception) {
                event.markFailed(e)
                blockedStores += event.storeId
                logger.warn(
                    "Failed to publish POS outbox event: id={} storeId={} type={} attempts={}",
                    event.id,
                    event.storeId,
                    event.eventType,
                    event.attempts,
                )
            }
        }
        return events.size == batchSize && blockedStores.isEmpty()
    }

    private fun publishToRedis(event: PosEventOutbox): String {
        val key = PosStreamKey.store(event.storeId)
        val data = mapOf(
            "eventId" to event.id.toString(),
            "type" to event.eventType,
            "payload" to event.payload,
            "timestamp" to event.createdAt.toEpochMilli().toString(),
        )

        val recordId = redisTemplate.opsForStream<String, String>() // XADD
            .add(
                StreamRecords.newRecord()
                    .ofMap(data)
                    .withStreamKey(key)
            )

        touchedKeys[key] = true
        return recordId?.value ?: throw IllegalStateException("Redis XADD returned null")
    }

    @Scheduled(fixedRate = 2000)
    fun trimStreams() {
        touchedKeys.keys.forEach { key ->
            try {
                redisTemplate.opsForStream<String, String>().trim(key, streamMaxLen)
            } catch (_: Exception) {
            } finally {
                touchedKeys.remove(key)
            }
        }
    }
}
