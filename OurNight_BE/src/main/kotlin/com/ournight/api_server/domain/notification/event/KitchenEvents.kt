package com.ournight.api_server.domain.notification.event

import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

data class CookingRequestedEvent(
    override val storeId: UUID,
    val orderId: UUID,
    val orderItemId: UUID,
    val sessionId: UUID,
    val tableId: UUID?,
    val tableNumber: Int,
    val menuId: UUID,
    val menuName: String,
    val currentQty: Int,
    val requiresCooking: Boolean = true,
    val currentItemStatus: String = "COOKING",
    val currentOrderStatus: String = "CONFIRMED",
    val totalAmount: BigDecimal?,
    val tableOrderSequence: Int,
    val orderedAt: LocalDateTime,
    override val eventStatus: String = "OPEN",
) : PosEvent {
    override val entityId: UUID get() = orderItemId
}

data class KitchenCookResolvedEvent(
    override val storeId: UUID,
    val orderId: UUID,
    val orderItemId: UUID,
    val sessionId: UUID,
    val tableId: UUID?,
    val tableNumber: Int,
    val menuId: UUID,
    val menuName: String,
    val currentQty: Int,
    val requiresCooking: Boolean = true,
    val previousItemStatus: String? = "COOKING",
    val currentItemStatus: String = "COOKED",
    override val eventStatus: String = "RESOLVED",
) : PosEvent {
    override val entityId: UUID get() = orderItemId
}

data class KitchenOrderItemDecreasedEvent(
    override val storeId: UUID,
    val orderId: UUID,
    val orderItemId: UUID,
    val orderItemChangeId: UUID,
    val sessionId: UUID,
    val tableId: UUID?,
    val tableNumber: Int,
    val tableOrderSequence: Int,
    val menuId: UUID,
    val menuName: String,
    val decreasedQty: Int,
    val currentQty: Int,
    val requiresCooking: Boolean = true,
    val previousItemStatus: String?,
    val currentItemStatus: String,
    override val eventStatus: String = "UPDATED",
) : PosEvent {
    override val entityId: UUID get() = orderItemId
}

data class KitchenOrderItemCanceledEvent(
    override val storeId: UUID,
    val orderId: UUID,
    val orderItemId: UUID,
    val orderItemChangeId: UUID,
    val sessionId: UUID,
    val tableId: UUID?,
    val tableNumber: Int,
    val tableOrderSequence: Int,
    val menuId: UUID,
    val menuName: String,
    val currentQty: Int = 0,
    val requiresCooking: Boolean = true,
    val previousItemStatus: String?,
    val currentItemStatus: String = "CANCELED",
    override val eventStatus: String = "SUPERSEDED",
) : PosEvent {
    override val entityId: UUID get() = orderItemId
}