package org.im0x00.com.benchmark1.redis

import org.im0x00.com.benchmark1.PosStreamKey
import org.im0x00.com.benchmark1.SseEvent
import org.im0x00.com.benchmark1.StreamId
import org.springframework.data.domain.Range
import org.springframework.data.redis.connection.Limit
import org.springframework.data.redis.connection.stream.ReadOffset
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.connection.stream.StreamReadOptions
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.UUID
import kotlin.collections.map
import kotlin.collections.orEmpty


@Service()
class RedisReader(
    private val redis: StringRedisTemplate,
) {
    private val ops = redis.opsForStream<String, String>()

    fun poll(
        storeId: UUID,
        lastId: StreamId?,
    ): List<SseEvent>  {
        val streamKey = PosStreamKey.store(storeId)
        val offset = ReadOffset.from(lastId.toString())
        return ops.read(
            StreamReadOptions.empty().block(Duration.ofSeconds(2)),
            StreamOffset.create(streamKey, offset)
        ).orEmpty()
            .map { it.toSseEvent(storeId) }
    }

    fun fetchLatest(
        storeId: UUID,
        count: Int = 1
    ): List<SseEvent> {
        val streamKey = PosStreamKey.store(storeId)
        val records = redis.opsForStream<String, String>()
            .reverseRange(streamKey, Range.unbounded(), Limit.limit().count(count))
            ?: emptyList()
        return records.map { it.toSseEvent(storeId) }
    }

}