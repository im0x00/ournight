package org.im0x00.notification.reactor

import java.util.UUID

data class SseEvent(
    // Redis stream entry ID: SSE `id` and replay cursor (Last-Event-ID -> ReadOffset.from)
    val id: String,
    // outbox event id: client-side idempotency key
    val eventId: String,
    val storeId: UUID,
    val type: String,
    val payload: String,
    val timestamp: Long,
)

object PosStreamKey {
    fun store(storeId: UUID): String =
        "notification:store:$storeId:pos"
}