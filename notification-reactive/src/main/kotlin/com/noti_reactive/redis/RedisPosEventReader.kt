package org.im0x00.com.noti_reactive.redis

import org.im0x00.com.noti_reactive.PosStreamKey
import org.im0x00.com.noti_reactive.SseEvent
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.ReadOffset
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.stream.StreamReceiver
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import java.util.UUID


@Component
class RedisPosEventReader(
    private val receiver: StreamReceiver<
            String,
            MapRecord<String, String, String>
            >
) {

    fun read(
        storeId: UUID,
        lastEventId: String?
    ): Flux<SseEvent> {
        val streamKey = PosStreamKey.store(storeId)
        val offset = lastEventId?.let { ReadOffset.from(it) }
            ?: ReadOffset.latest()

        return receiver
            .receive(
                StreamOffset.create(
                    streamKey,
                    offset
                )
            )
            .map { record ->
                record.toDomain(storeId)
            }
    }

    private fun MapRecord<String, String, String>.toDomain(
        storeId: UUID,
    ): SseEvent {

        return SseEvent(
            id = id.value,
            eventId = value["eventId"]
                ?: error("eventId missing"),
            storeId = storeId,
            type = value["type"]
                ?: error("type missing"),
            payload = value["payload"]
                ?: error("payload missing"),
            timestamp = value["timestamp"]
                ?.toLongOrNull()
                ?: error("invalid timestamp"),
        )
    }
}