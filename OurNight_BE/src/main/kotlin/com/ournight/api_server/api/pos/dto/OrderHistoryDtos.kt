package com.ournight.api_server.api.pos.dto

import com.ournight.api_server.domain.order.model.IssueKind
import com.ournight.api_server.domain.order.model.IssueStatus
import com.ournight.api_server.domain.order.model.OrderItemStatus
import com.ournight.api_server.domain.order.model.OrderStatus
import com.ournight.api_server.domain.store.model.StoreTableStatus
import java.time.LocalDateTime
import java.util.UUID

data class PosTableOrderSummaryListResponse(
    val storeId: UUID,
    val timeSlotId: UUID,
    val mode: PosOrderViewMode,
    val tables: List<PosTableOrderSummary>,
)

enum class PosOrderViewMode {
    ACTIVE,
    HISTORY,
}

data class PosTableOrderSummary(
    val sessionId: UUID?,
    val tableId: UUID,
    val tableNumber: Int,
    val tableStatus: StoreTableStatus,
    val lastEventAt: LocalDateTime,
    val netAmount: Long,
    val itemCount: Int,
    val itemPreview: String,
    val enteredAt: LocalDateTime?,
    val expiresAt: LocalDateTime?,
    val remainingMinutes: Long?,
    val timeOver: Boolean,
)

data class PosTableOrderDetailResponse(
    val storeId: UUID,
    val timeSlotId: UUID,
    val sessionId: UUID?,
    val table: PosTableHeader,
    val summary: PosPaymentSummary,
    val mode: PosOrderViewMode,
    val orderGroups: List<PosOrderGroup>,
)

data class PosTableHeader(
    val tableId: UUID,
    val tableNumber: Int,
    val tableStatus: StoreTableStatus,
    val enteredAt: LocalDateTime?,
    val expiresAt: LocalDateTime?,
    val remainingMinutes: Long?,
    val timeOver: Boolean,
)

data class PosPaymentSummary(
    val netAmount: Long,
    val grossOrderAmount: Long,
    val refundAmount: Long,
)

data class PosOrderGroup(
    val groupNo: Int,
    val orderedAt: LocalDateTime,
    val orderId: UUID,
    val status: OrderStatus,
    val lines: List<PosOrderLine>,
)

data class PosOrderLine(
    val lineId: UUID,
    val menuId: UUID,
    val menuName: String,
    val qty: Int,
    val unitPrice: Long,
    val linePrice: Long,
    val itemStatus: OrderItemStatus,
    val issue: PosIssueSummary? = null,
)

data class PosIssueSummary(
    val issueId: UUID,
    val kind: IssueKind,
    val status: IssueStatus,
    val originalAmount: Long,
    val afterAmount: Long,
    val deltaAmount: Long,
    val openedAt: LocalDateTime,
    val closedAt: LocalDateTime?,
)