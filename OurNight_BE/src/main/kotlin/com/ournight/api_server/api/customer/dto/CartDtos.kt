package com.ournight.api_server.api.customer.dto

import java.util.UUID


data class CartMetaResponse(
    val v: Int,
    val locked: Boolean = false,
    val orderId: UUID? = null,
    val orderStatus: String? = null
)

data class CartItemDeltaRequest(
    val menuId: UUID,
    val delta: Int // +1 or -1
)

data class CartItemResponse(
    val menuId: UUID,
    val qty: Int
)

data class CartSnapshotResponse(
    val meta: CartMetaResponse,
    val items: List<CartItemResponse>
)

data class CartCommitRequest(
    val seenVersion: Int,
    val commitId: UUID,
)

data class CartCommitResponse(
    val orderId: UUID
)

