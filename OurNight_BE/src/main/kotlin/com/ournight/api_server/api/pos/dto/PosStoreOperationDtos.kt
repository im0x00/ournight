package com.ournight.api_server.api.pos.dto

import com.ournight.api_server.domain.order.model.OrderItemStatus
import com.ournight.api_server.domain.order.model.OrderStatus
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

data class StoreMenuResult(
    val id: UUID,
    val categoryId: UUID,
    val name: String,
    val salePrice: BigDecimal,
    val imageUrl: String?,
    val isSoldOut: Boolean,
)

data class OrderRoundResponse(
    val orderId: UUID,
    val sequence: Int,
    val orderedAt: LocalDateTime,
    val status: OrderStatus,
    val items: List<OrderItemInRoundResponse>,
)

data class OrderItemInRoundResponse(
    val orderItemId: UUID,
    val menuId: UUID,
    val name: String,
    val price: BigDecimal,
    val quantity: Int,
    val status: OrderItemStatus,
)

data class StoreMenuCategoryWithMenusResult(
    val id: UUID,
    val name: String,
    val sequence: Int,
    val menus: List<StoreMenuResult>,
)
