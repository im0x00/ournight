package com.ournight.api_server.domain.order.model

import java.util.UUID

data class CartMeta(
    val v: Int,
    val locked: Boolean = false,
    val orderId: UUID? = null,
    val orderStatus: String? = null
)

data class CartSnapshot(
    val meta: CartMeta,
    val items: List<CartItem>
)

data class CartItem(
    val menuId: UUID,
    val qty: Int
)