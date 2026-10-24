package org.im0x00.notification.coroutine

import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.data.domain.Range
import org.springframework.data.redis.connection.Limit
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.ReadOffset
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.connection.stream.StreamReadOptions
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.UUID

// Port of notification-mvc-vt RedisReader: same functions, suspend instead of blocking.
// Connection pool settings live in application.yml (spring.data.redis.lettuce.pool).
@Service
class RedisService(
    redis: ReactiveStringRedisTemplate,
) {
    private val ops = redis.opsForStream<String, String>()

    // One XREAD BLOCK (up to 2s). Returns the records after lastId, or an empty list on timeout.
    suspend fun poll(
        storeId: UUID,
        lastId: StreamId,
    ): List<SseEvent> =
        ops.read(
            StreamReadOptions.empty().block(Duration.ofSeconds(2)),
            StreamOffset.create(PosStreamKey.store(storeId), ReadOffset.from(lastId.toString())),
        )
            .map { it.toSseEvent(storeId) }
            .collectList()
            .awaitSingle()

    // Id of the newest record in the stream, or null when the stream is empty.
    suspend fun fetchLatestId(storeId: UUID): StreamId? =
        ops.reverseRange(PosStreamKey.store(storeId), Range.unbounded(), Limit.limit().count(1))
            .next()
            .map { StreamId.parse(it.id.value) }
            .awaitSingleOrNull()
}

// Same field layout as the load test / hub-check XADD: eventId, type, payload, timestamp.
fun MapRecord<String, String, String>.toSseEvent(storeId: UUID): SseEvent =
    SseEvent(
        id = StreamId.parse(id.value),
        eventId = value["eventId"] ?: error("eventId missing"),
        storeId = storeId,
        type = value["type"] ?: error("type missing"),
        payload = value["payload"] ?: error("payload missing"),
        timestamp = value["timestamp"]?.toLongOrNull() ?: error("invalid timestamp"),
    )
