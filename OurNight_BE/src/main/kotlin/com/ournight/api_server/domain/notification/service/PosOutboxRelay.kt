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

    // 꽉 찬 batch가 실패 없이 끝나면 쉬지 않고 다음 batch. batch마다 transaction 하나.
    @Scheduled(fixedDelay = 500)
    fun publishPendingEvents() {
        while (transactionTemplate.execute { publishBatch() } == true) {
        }
    }

    // true: 꽉 찼고 전부 publish됨 → 바로 다시. 실패가 있으면 fixedDelay 뒤에 다시.
    private fun publishBatch(): Boolean {
        val events = outboxRepository.lockPublishable(batchSize)
        if (events.isEmpty()) return false

        // 실패한 store는 이 batch에서 멈춤. 뒤 event가 앞지르면 store 안 순서가 깨짐.
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
