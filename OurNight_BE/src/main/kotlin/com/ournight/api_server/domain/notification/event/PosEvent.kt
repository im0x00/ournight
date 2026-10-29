package com.ournight.api_server.domain.notification.event

import com.ournight.api_server.domain.store.model.StoreTableStatus
import com.ournight.api_server.domain.session.model.SessionStatus
import com.ournight.api_server.domain.store.domain.StoreMenuStatus
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

interface PosEvent {
    val storeId: UUID
    val entityId: UUID
    val eventStatus: String
}

data class OrdersReassignedToSessionEvent(
    override val storeId: UUID,
    val sourceSessionId: UUID,
    val targetSessionId: UUID,
    val sourceTableId: UUID,
    val targetTableId: UUID,
    val sourceTableNumber: Int,
    val targetTableNumber: Int,
    val orderIds: List<UUID>,
    override val eventStatus: String = "UPDATED",
) : PosEvent {
    override val entityId: UUID get() = targetSessionId
}

data class TableSummaryChangedEvent(
    override val storeId: UUID,
    val tableId: UUID,
    val tableNumber: Int,
    val status: StoreTableStatus,
    val sessionId: UUID?,
    val sessionStatus: SessionStatus?,
    val timeOver: Boolean,
    val enteredAt: LocalDateTime?,
    val servedOrderItemCount: Int,
    val unservedOrderItemCount: Int,
    val remainingMinutes: Long?,
    val totalAmount: BigDecimal,
    override val eventStatus: String = "UPDATED",
) : PosEvent {
    override val entityId: UUID get() = tableId
}

data class PosReloadRequiredEvent(
    override val storeId: UUID,
    override val eventStatus: String = "RELOAD_REQUIRED",
) : PosEvent {
    override val entityId: UUID get() = storeId
}

data class KitchenMenuChangedEvent(
    override val storeId: UUID,
    val menuId: UUID,
    val categoryId: UUID,
    val name: String,
    val salePrice: BigDecimal,
    val imageUrl: String?,
    val status: StoreMenuStatus,
    val inventoryEnabled: Boolean,
    val inventoryQuantity: Int?,
    val updatedAt: Instant,
    override val eventStatus: String = "UPDATED",
) : PosEvent {
    override val entityId: UUID get() = menuId
}
