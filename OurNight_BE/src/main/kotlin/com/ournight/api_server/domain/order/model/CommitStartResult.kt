package com.ournight.api_server.domain.order.model

import java.util.UUID

sealed interface CommitStartResult {
    data class Success(val snapshot: CartSnapshot, val lockToken: String) : CommitStartResult
    data class AlreadyCommitted(val orderId: UUID) : CommitStartResult
    data class Locked(val meta: CartMeta) : CommitStartResult
    data class OrderConflict(val meta: CartMeta) : CommitStartResult
    data class VersionConflict(val meta: CartMeta) : CommitStartResult
}