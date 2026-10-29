package com.ournight.api_server.api.customer.dto

import com.ournight.api_server.domain.order.model.OrderStatus
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID


data class UserOrderItemResponse(
    val menuId: UUID,
    val menuName: String,
    val quantity: Int,
    /** 주문 시점 단가(메뉴 1개 기준) */
    val unitPrice: BigDecimal,
    /** 해당 라인 합계 = unitPrice × quantity */
    val lineTotal: BigDecimal,
)


data class UserOrderResponse(
    val orderId: UUID,
    val sessionId: UUID,
    val storeId: UUID,
    val tableNumber: Int,
    val totalAmount: BigDecimal,
    val status: OrderStatus,
    val orderedAt: LocalDateTime,
    val items: List<UserOrderItemResponse>
)

