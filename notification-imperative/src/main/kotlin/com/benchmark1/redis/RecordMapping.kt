package org.im0x00.com.benchmark1.redis

import org.im0x00.com.benchmark1.SseEvent
import org.im0x00.com.benchmark1.StreamId
import org.springframework.data.redis.connection.stream.MapRecord
import java.util.UUID

fun MapRecord<String, String, String>.toSseEvent(storeId: UUID): SseEvent =
    SseEvent(
        id = StreamId.parse(id.value),
        eventId = value["eventId"] ?: error("eventId missing"),
        storeId = storeId,
        type = value["type"] ?: error("type missing"),
        payload = value["payload"] ?: error("payload missing"),
        timestamp = value["timestamp"]?.toLongOrNull() ?: error("invalid timestamp"),
    )
