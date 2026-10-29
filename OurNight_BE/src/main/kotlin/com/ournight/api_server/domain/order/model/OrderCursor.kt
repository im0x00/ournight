package com.ournight.api_server.domain.order.model

import java.time.LocalDateTime
import java.util.Base64
import java.util.UUID

data class OrderCursor(
    val orderedAt: LocalDateTime,
    val orderId: UUID
)

fun encodeCursor(c: OrderCursor): String {
    val raw = "${c.orderedAt}|${c.orderId}" // LocalDateTime은 ISO string
    return Base64.getUrlEncoder().withoutPadding()
        .encodeToString(raw.toByteArray(Charsets.UTF_8))
}

fun decodeCursor(s: String): OrderCursor {
    val raw = String(Base64.getUrlDecoder().decode(s), Charsets.UTF_8)
    val parts = raw.split("|")
    require(parts.size == 2) { "invalid cursor" }

    val orderedAt = LocalDateTime.parse(parts[0])
    val orderId = UUID.fromString(parts[1])

    return OrderCursor(orderedAt = orderedAt, orderId = orderId)
}