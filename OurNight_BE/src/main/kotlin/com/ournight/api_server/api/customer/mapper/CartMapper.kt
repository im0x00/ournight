package com.ournight.api_server.api.customer.mapper

import com.ournight.api_server.api.customer.dto.CartItemResponse
import com.ournight.api_server.api.customer.dto.CartMetaResponse
import com.ournight.api_server.api.customer.dto.CartSnapshotResponse
import com.ournight.api_server.domain.order.model.CartItem
import com.ournight.api_server.domain.order.model.CartMeta
import com.ournight.api_server.domain.order.model.CartSnapshot


fun CartMeta.toResponse() = CartMetaResponse(
    v = v,
    locked = locked,
    orderId = orderId,
    orderStatus = orderStatus
)

fun CartItem.toResponse() = CartItemResponse(
    menuId = menuId,
    qty = qty
)

fun CartSnapshot.toResponse() = CartSnapshotResponse(
    meta = meta.toResponse(),
    items = items.map { it.toResponse() }
)