package com.ournight.api_server.domain.notification.service

import com.ournight.api_server.domain.notification.PosStreamKey
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import java.util.UUID

const val POS_STREAM_WATERMARK_HEADER = "X-Pos-Stream-Watermark"
private const val ZERO_STREAM_ID = "0-0"

@Service
class PosStreamWatermarkService(
    private val redisTemplate: StringRedisTemplate,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun currentWatermark(storeId: UUID): String {
        val key = PosStreamKey.store(storeId)
        return try {
            val info = redisTemplate.opsForStream<String, String>().info(key)
            info.lastGeneratedId() ?: info.lastEntryId() ?: ZERO_STREAM_ID
        } catch (e: Exception) {
            logger.debug("Failed to read POS stream watermark: storeId={}, message={}", storeId, e.message)
            ZERO_STREAM_ID
        }
    }

    fun <T> okWithWatermark(watermark: String, body: T): ResponseEntity<T> =
        ResponseEntity.ok()
            .header(POS_STREAM_WATERMARK_HEADER, watermark)
            .body(body)

    fun <T> ok(storeId: UUID, bodySupplier: () -> T): ResponseEntity<T> {
        val watermark = currentWatermark(storeId)
        val body = bodySupplier()
        return okWithWatermark(watermark, body)
    }
}
