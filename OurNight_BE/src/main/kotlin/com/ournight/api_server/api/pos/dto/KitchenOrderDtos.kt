package com.ournight.api_server.api.pos.dto

import com.ournight.api_server.domain.order.model.OrderItemStatus
import com.ournight.api_server.domain.order.model.OrderStatus
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID


// kitchen order 용

data class KitchenOrderListResponse(
    val items: List<KitchenOrderCardDto>,
    val nextCursor: String?,
    val hasNext: Boolean
)

data class KitchenOrderCardDto(
    val orderId: UUID,
    val tableId: UUID,
    val tableNumber: Int,
    val tableOrderSequence: Int,
    val status: OrderStatus,
    val totalPrice: Int,
    val createdAt: Instant,
    val elapsedMinutes: Long,
    val items: List<KitchenOrderItemDto>
)

data class KitchenOrderItemDto(
    val orderItemId: UUID,
    val menuId: UUID,
    val menuName: String,
    val qty: Int,
    val status: OrderItemStatus,
    val requiresCooking: Boolean,
)


