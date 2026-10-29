package com.ournight.api_server.api.pos.dto

import com.ournight.api_server.domain.order.model.AdjustmentReasonType
import com.ournight.api_server.domain.order.model.OrderItemStatus
import com.ournight.api_server.domain.order.model.OrderStatus
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

// 홀에서 테이블 선택해서 직접 주문 생성할 때 (손님이 QR을 안쓸 경우)
data class CreateHallOrderRequest(
    val tableId: UUID,
    val clientRequestId: UUID,
    val items: List<CreateHallOrderItemRequest>
)

data class CreateHallOrderItemRequest(
    val menuId: UUID,
    val quantity: Int
)

data class CreateHallOrderResponse(
    val orderId: UUID,
)

data class OrderCancelRequest(
    val reasonType: AdjustmentReasonType,
    val clientRequestId: UUID
)

data class OrderItemDecreaseRequest(
    val quantity: Int,
    val reasonType: AdjustmentReasonType,
    val clientRequestId: UUID
)

data class OrderItemCancelRequest(
    val reasonType: AdjustmentReasonType,
    val clientRequestId: UUID
)

data class HallServingResponse(
    val orderId: UUID,
    val orderItemId: UUID,
    val tableId: UUID,
    val menuId: UUID,
    val menuName: String,
    val quantity: Int,
    val tableNumber: Int,
    val status: String,
    val requiresCooking: Boolean,
    val price: Int,
)

data class HallPaymentResponse(
    val orderId: UUID,
    val tableId: UUID,
    val tableNumber: Int,
    val totalAmount: BigDecimal,
    val status: String
)

data class HallOrderListResponse(
    val items: List<HallOrderCardDto>,
    val nextCursor: String?,
    val hasNext: Boolean
)

data class HallOrderCardDto(
    val orderId: UUID,
    val tableId: UUID,
    val tableNumber: Int,
    val tableOrderSequence: Int,
    val status: OrderStatus,
    val totalPrice: Int,
    val createdAt: Instant,
    val elapsedMinutes: Long,
    val items: List<HallOrderItemDto>
)

data class HallOrderItemDto(
    val orderItemId: UUID,
    val menuId: UUID,
    val menuName: String,
    val qty: Int,
    val status: OrderItemStatus,
)



