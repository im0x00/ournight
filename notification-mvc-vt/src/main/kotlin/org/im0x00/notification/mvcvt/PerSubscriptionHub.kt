package org.im0x00.notification.mvcvt

import org.im0x00.notification.mvcvt.redis.RedisReader
import org.im0x00.notification.mvcvt.redis.toSseEvent
import org.springframework.data.domain.Range
import org.springframework.data.redis.connection.Limit
import org.springframework.data.redis.connection.stream.ReadOffset
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.connection.stream.StreamReadOptions
import org.springframework.data.redis.core.StringRedisTemplate
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.Duration
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

private val perSubLog = LoggerFactory.getLogger(PerSubscriptionHub::class.java)

@Service
class PerSubscriptionHub(
    val reader: RedisReader,
) {
    private val sse_emitter_ttl_ms: Long = 5 * 60 * 1000

    fun subscribe(
        storeId: UUID,
        lastEventId: String?,
    ): SseEmitter {

        val sending = AtomicBoolean(true)
        val emitter = SseEmitter(sse_emitter_ttl_ms)
            .apply {
                onCompletion { sending.set(false) }
                onTimeout { sending.set(false) }
                onError { sending.set(false) }
            }

        val thread = Thread.ofVirtual().name("StoreEventHub-worker-${storeId}-${1}")

        var last: StreamId
        if (lastEventId == null) {
            last = reader.fetchLatest(storeId).lastOrNull()?.id ?: StreamId.ZERO
        } else {
            val parsed = StreamId.parseOrNull(lastEventId)
            if (parsed == null) {
                sendToEmitter(emitter, SseEvent.reloadRequired(storeId, StreamId.ZERO))
                emitter.complete()
                return emitter
            }
            last = parsed
        }

        thread.start {
            try {
                while (sending.get()) {
                    reader.poll(storeId, last).forEach { record ->
                        perSubLog.debug("{}", record)
                        last = record.id
                        sendToEmitter(emitter, record)
                    }
                }
            } catch (e: Exception) {
                perSubLog.warn("loop error: {}", e.toString())
            } finally {
                perSubLog.info("loop end")
            }
        }


        return emitter
    }

    private fun sendToEmitter(emitter: SseEmitter, event: SseEvent) {
        emitter.send(
            SseEmitter.event()
                .id(event.id.toString())
                .data(event)
                .build()
        )
    }

}



