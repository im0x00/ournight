package org.im0x00.com.benchmark1

import com.fasterxml.jackson.annotation.JsonValue
import java.util.UUID

data class SseEvent(
    // Redis stream entry ID: SSE `id` and replay cursor (Last-Event-ID -> ReadOffset.from)
    val id: StreamId,
    // outbox event id: client-side idempotency key
    val eventId: String,
    val storeId: UUID,
    val type: String,
    val payload: String,
    val timestamp: Long,
) {
    companion object {
        fun reloadRequired(storeId: UUID, from: StreamId) = SseEvent(
            id = from,
            eventId = UUID.randomUUID().toString(),
            storeId = storeId,
            type = "RELOAD_REQUIRED",
            payload = "{}",
            timestamp = System.currentTimeMillis(),
        )
    }
}

object PosStreamKey {
    fun store(storeId: UUID): String =
        "notification:store:$storeId:pos"
}

data class StreamId(val ms: Long, val seq: Long) : Comparable<StreamId> {

    override fun compareTo(other: StreamId): Int =
        compareValuesBy(this, other, StreamId::ms, StreamId::seq)

    @JsonValue
    override fun toString() = "$ms-$seq"

    companion object {
        val ZERO = StreamId(0, 0)

        // For ids Redis gave us: always well-formed. Throws NumberFormatException otherwise.
        fun parse(id: String): StreamId {
            val dash = id.indexOf('-')
            return if (dash < 0) StreamId(id.toLong(), 0)
            else StreamId(id.substring(0, dash).toLong(), id.substring(dash + 1).toLong())
        }

        // For ids a client sent us (Last-Event-ID): null when malformed, so the caller can decide what to do.
        fun parseOrNull(id: String): StreamId? =
            try { parse(id) } catch (e: NumberFormatException) { null }
    }
}
