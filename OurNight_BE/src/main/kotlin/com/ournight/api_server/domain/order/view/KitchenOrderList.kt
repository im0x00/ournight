package com.ournight.api_server.domain.order.view

import com.ournight.api_server.domain.order.model.OrderCursor
import com.ournight.api_server.domain.order.model.OrderItemStatus
import com.ournight.api_server.domain.order.model.OrderStatus
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

data class KitchenOrderListResult(
    val items: List<KitchenOrderCard>,
    val nextCursor: OrderCursor?,
    val hasNext: Boolean
)

data class KitchenOrderCard(
    val orderId: UUID,
    val tableId: UUID,
    val tableNumber: Int,
    val status: OrderStatus,
    val totalAmount: BigDecimal,
    val orderedAt: LocalDateTime,
    val items: List<KitchenOrderItem>,
    val tableOrderSequence: Int,
)

data class KitchenOrderItem(
    val orderItemId: UUID,
    val menuId: UUID,
    val menuName: String,
    val quantity: Int,
    val status: OrderItemStatus,
    val requiresCooking: Boolean,
)