package com.ournight.api_server.domain.order.service

import com.ournight.api_server.domain.order.model.CartMeta
import com.ournight.api_server.domain.order.model.CartSnapshot
import java.util.UUID

interface CartService {
    fun getMeta(tableId: UUID): CartMeta
    fun getSnapshot(tableId: UUID): CartSnapshot
    fun addDelta(tableId: UUID, menuId: UUID, delta: Int): CartMeta
    fun commitCart(tableId: UUID, commitId: UUID, seenVersion: Int, userId: UUID): UUID
    fun getCommitResult(tableId: UUID, commitId: UUID): UUID?
}


