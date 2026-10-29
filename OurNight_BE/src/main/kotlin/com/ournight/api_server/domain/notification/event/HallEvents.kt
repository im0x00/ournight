package com.ournight.api_server.domain.notification.event

import com.ournight.api_server.domain.call.model.CallStatus
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

data class CustomerCallEvent(
    override val storeId: UUID,
    val requestId: UUID,
    val sessionId: UUID?,
    val tableId: UUID?,
    val tableNumber: Int,
    val optionId: UUID,
    val optionName: String,
    val callStatus: CallStatus,
    val createdAt: LocalDateTime,
    override val eventStatus: String = "OPEN",
) : PosEvent {
    override val entityId: UUID get() = requestId
}

data class CustomerCallResolvedEvent(
    override val storeId: UUID,
    val requestId: UUID,
    val sessionId: UUID?,
    val tableId: UUID?,
    val tableNumber: Int,
    val callStatus: CallStatus = CallStatus.COMPLETED,
    override val eventStatus: String = "RESOLVED",
) : PosEvent {
    override val entityId: UUID get() = requestId
}

data class PaymentVerificationNeededEvent(
    override val storeId: UUID,
    val orderId: UUID,
    val sessionId: UUID,
    val tableId: UUID?,
    val tableNumber: Int,
    val tableOrderSequence: Int,
    val totalAmount: BigDecimal,
    val currentOrderStatus: String = "PENDING_PAYMENT",
    override val eventStatus: String = "OPEN",
) : PosEvent {
    override val entityId: UUID get() = orderId
}

data class HallPaymentResolvedEvent(
    override val storeId: UUID,
    val orderId: UUID,
    val sessionId: UUID,
    val tableId: UUID?,
    val tableNumber: Int,
    val totalAmount: BigDecimal?,
    val currentOrderStatus: String = "CONFIRMED",
    override val eventStatus: String = "RESOLVED",
) : PosEvent {
    override val entityId: UUID get() = orderId
}

data class HallPaymentCanceledEvent(
    override val storeId: UUID,
    val orderId: UUID,
    val sessionId: UUID,
    val tableId: UUID?,
    val tableNumber: Int,
    val totalAmount: BigDecimal?,
    val currentOrderStatus: String = "CANCELED",
    override val eventStatus: String = "CANCELED",
) : PosEvent {
    override val entityId: UUID get() = orderId
}

data class ServingRequestedEvent(
    override val storeId: UUID,
    val orderId: UUID,
    val orderItemId: UUID,
    val sessionId: UUID,
    val tableId: UUID?,
    val tableNumber: Int,
    val menuId: UUID,
    val menuName: String,
    val currentQty: Int,
    val requiresCooking: Boolean,
    val currentItemStatus: String = "READY",
    val tableOrderSequence: Int,
    val totalAmount: BigDecimal,
    val orderedAt: LocalDateTime,
    override val eventStatus: String = "OPEN",
) : PosEvent {
    override val entityId: UUID get() = orderItemId
}

data class HallServeResolvedEvent(
    override val storeId: UUID,
    val orderId: UUID,
    val orderItemId: UUID,
    val sessionId: UUID,
    val tableId: UUID?,
    val tableNumber: Int,
    val menuId: UUID,
    val menuName: String,
    val currentQty: Int,
    val requiresCooking: Boolean,
    val currentItemStatus: String = "SERVED",
    val currentOrderStatus: String,
    override val eventStatus: String = "RESOLVED",
) : PosEvent {
    override val entityId: UUID get() = orderItemId
}


data class HallOrderItemDecreasedEvent(
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
    val requiresCooking: Boolean,
    val previousItemStatus: String?,
    val currentItemStatus: String,
    override val eventStatus: String = "UPDATED",
) : PosEvent {
    override val entityId: UUID get() = orderItemId
}

data class HallOrderItemCanceledEvent(
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
    val requiresCooking: Boolean,
    val previousItemStatus: String?,
    val currentItemStatus: String = "CANCELED",
    override val eventStatus: String = "SUPERSEDED",
) : PosEvent {
    override val entityId: UUID get() = orderItemId
}
