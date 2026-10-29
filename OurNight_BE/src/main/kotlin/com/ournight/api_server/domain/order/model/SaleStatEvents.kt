package com.ournight.api_server.domain.order.model

import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

/**
 * 주문 확정 시 발생하는 통계용 이벤트
 */
data class OrderConfirmedForStatEvent(
    val storeId: UUID,
    val timeSlotId: UUID,
    val orderId: UUID,
    val totalAmount: BigDecimal,
    val orderedAt: LocalDateTime,
    val items: List<StatOrderItemInfo>
)

data class StatOrderItemInfo(
    val menuId: UUID,
    val menuName: String,
    val quantity: Int,
    val price: BigDecimal
)

/**
 * 이슈(환불/감액) 적용 시 발생하는 통계용 이벤트
 */
data class IssueExecutedForStatEvent(
    val storeId: UUID,
    val timeSlotId: UUID,
    val timeQuantum: Int,
    val issueId: UUID,
    val orderId: UUID,
    val orderItemId: UUID?,
    val menuId: UUID?,
    val menuName: String?,
    val refundAmount: BigDecimal,
    val refundQuantity: Int = 0
)