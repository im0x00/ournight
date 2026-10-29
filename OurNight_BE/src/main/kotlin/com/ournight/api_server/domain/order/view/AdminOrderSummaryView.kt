package com.ournight.api_server.domain.order.view

import com.ournight.api_server.domain.order.model.OrderStatus
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

/** JPQL constructor projection — 전 스토어 주문 요약(어드민) */
data class AdminOrderSummaryView(
    val orderId: UUID,
    val status: OrderStatus,
    val totalAmount: BigDecimal,
    val orderedAt: LocalDateTime,
    val tableNumber: Int,
    val storeId: UUID,
    val storeName: String,
)
