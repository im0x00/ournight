//package com.ournight.api_server.domain.order.view
//
//import com.ournight.api_server.domain.order.model.IssueKind
//import com.ournight.api_server.domain.order.model.IssueStatus
//import com.ournight.api_server.domain.order.model.OrderItemStatus
//import com.ournight.api_server.domain.order.model.OrderStatus
//import com.ournight.api_server.domain.store.model.StoreTableStatus
//import java.math.BigDecimal
//import java.time.LocalDateTime
//import java.util.UUID
//
//data class PosTableOrderSummaryListView(
//    val storeId: UUID,
//    val timeSlotId: UUID,
//    val mode: PosOrderViewModeView,
//    val tables: List<PosTableOrderSummaryView>,
//)
//
//enum class PosOrderViewModeView { ACTIVE, HISTORY }
//
//data class PosTableOrderSummaryView(
//    val tableId: UUID,
//    val tableNumber: Int,
//    val tableStatus: StoreTableStatus,
//    val lastEventAt: LocalDateTime,
//    val netAmount: Long,
//    val itemCount: Int,
//    val itemPreview: String,
//    val enteredAt: LocalDateTime?,
//    val expiresAt: LocalDateTime?,
//    val remainingMinutes: Long?,
//    val timeOver: Boolean,
//    val timeBasis: PosTimeBasisView?
//)
//
//data class PosTableOrderDetailView(
//    val storeId: UUID,
//    val timeSlotId: UUID,
//    val table: PosTableHeaderView,
//    val summary: PosPaymentSummaryView,
//    val orderGroups: List<PosOrderGroupView>,
//)
//
//data class PosTableHeaderView(
//    val tableId: UUID,
//    val tableNumber: Int,
//    val tableStatus: StoreTableStatus,
//    val enteredAt: LocalDateTime?,
//    val expiresAt: LocalDateTime?,
//    val remainingMinutes: Long?,
//    val timeOver: Boolean,
//    val timeBasis: PosTimeBasisView?
//)
//
//data class PosPaymentSummaryView(
//    val netAmount: Long,
//    val grossOrderAmount: Long,
//    val refundAmount: Long,
//)
//
//data class PosOrderGroupView(
//    val groupNo: Int,
//    val orderedAt: LocalDateTime,
//    val orderId: UUID,
//    val status: OrderStatus,
//    val lines: List<PosOrderLineView>,
//)
//
//data class PosOrderLineView(
//    val lineId: UUID,
//    val menuId: UUID,
//    val menuName: String,
//    val qty: Int,
//    val unitPrice: Long,
//    val linePrice: Long,
//    val itemStatus: OrderItemStatus,
//    val issue: PosIssueSummaryView? = null,
//)
//
//data class PosIssueSummaryView(
//    val issueId: UUID,
//    val kind: IssueKind,
//    val status: IssueStatus,
//    val originalAmount: Long,
//    val afterAmount: Long,
//    val deltaAmount: Long,
//    val openedAt: LocalDateTime,
//    val closedAt: LocalDateTime?,
//)
//
//enum class PosTimeBasisView {
//    PASS,
//    SESSION
//}
//
//data class PosSessionTimeView(
//    val enteredAt: LocalDateTime?,
//    val expiresAt: LocalDateTime?,
//    val remainingMinutes: Long?,
//    val timeOver: Boolean,
//    val basis: PosTimeBasisView
//)
//
//data class ActiveTableOrderRow(
//    val tableId: UUID,
//    val tableNumber: Int,
//    val sessionId: UUID,
//    val orderId: UUID,
//    val orderStatus: OrderStatus,
//    val totalAmount: BigDecimal,
//    val orderedAt: LocalDateTime,
//    val sessionOrderSequence: Int
//)