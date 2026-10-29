package com.ournight.api_server.domain.order.view

import com.ournight.api_server.domain.order.model.OrderCursor
import com.ournight.api_server.domain.order.model.OrderItemStatus
import com.ournight.api_server.domain.order.model.OrderStatus
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

data class HallOrderListResult(
    val items: List<HallOrderCard>,
    val nextCursor: OrderCursor?,
    val hasNext: Boolean
)

data class HallOrderCard(
    val orderId: UUID,
    val tableId: UUID,
    val tableNumber: Int,
    val status: OrderStatus,
    val totalAmount: BigDecimal,
    val orderedAt: LocalDateTime,
    val items: List<HallOrderItem>,
    val tableOrderSequence: Int,
)

data class HallOrderItem(
    val orderItemId: UUID,
    val menuId: UUID,
    val name: String,
    val quantity: Int,
    val status: OrderItemStatus
)