package com.ournight.api_server.api.pos.mapper

import com.ournight.api_server.api.pos.dto.PosIssueSummary
import com.ournight.api_server.api.pos.dto.PosOrderGroup
import com.ournight.api_server.api.pos.dto.PosOrderLine
import com.ournight.api_server.api.pos.dto.PosOrderViewMode
import com.ournight.api_server.api.pos.dto.PosPaymentSummary
import com.ournight.api_server.api.pos.dto.PosTableHeader
import com.ournight.api_server.api.pos.dto.PosTableOrderDetailResponse
import com.ournight.api_server.api.pos.dto.PosTableOrderSummary
import com.ournight.api_server.api.pos.dto.PosTableOrderSummaryListResponse
import com.ournight.api_server.domain.order.domain.Order
import com.ournight.api_server.domain.order.model.Issue
import com.ournight.api_server.domain.order.model.OrderItemStatus
import com.ournight.api_server.domain.session.model.TableSession
import com.ournight.api_server.domain.store.domain.StoreTable
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID
import kotlin.collections.get

object PosOrderAssembler {

    fun summaryList(
        storeId: UUID,
        timeSlotId: UUID,
        mode: PosOrderViewMode,
        tables: List<StoreTable>,
        ordersInSlot: List<Order>,
        executedIssuesByOrderId: Map<UUID, List<Issue>>,
        timeByTableId: Map<UUID, PosSessionTime>,
        sessionIdByTableId: Map<UUID, UUID?> = emptyMap(),
    ): PosTableOrderSummaryListResponse {

        val ordersByTableId = ordersInSlot.groupBy { it.table.id!! }

        val tableViews = tables.map { t ->
            val tableOrders = ordersByTableId[t.id!!].orEmpty()
            val time = timeByTableId[t.id!!]

            val lastEventAt = tableOrders
                .flatMap { listOf(it.orderedAt, it.completedAt) }
                .filterNotNull()
                .maxOrNull() ?: LocalDateTime.MIN

            val grossOrderAmount = sumNonCanceledItemsAmount(tableOrders)

            val refundAmount = tableOrders
                .mapNotNull { it.id }
                .flatMap { orderId -> executedIssuesByOrderId[orderId].orEmpty() }
                .sumAmount()

            val netAmount = (grossOrderAmount - refundAmount).coerceAtLeast(0L)

            val (itemCount, preview) = buildPreview(tableOrders)

            PosTableOrderSummary(
                sessionId = sessionIdByTableId[t.id!!],
                tableId = t.id!!,
                tableNumber = t.tableNumber,
                tableStatus = t.status,
                lastEventAt = lastEventAt,
                netAmount = netAmount,
                itemCount = itemCount,
                itemPreview = preview,
                enteredAt = time?.enteredAt,
                expiresAt = time?.expiresAt,
                remainingMinutes = time?.remainingMinutes,
                timeOver = time?.timeOver ?: false,
            )
        }.sortedByDescending { it.lastEventAt }

        return PosTableOrderSummaryListResponse(
            storeId = storeId,
            timeSlotId = timeSlotId,
            mode = mode,
            tables = tableViews
        )
    }

    fun summaryListBySession(
        storeId: UUID,
        timeSlotId: UUID,
        mode: PosOrderViewMode,
        sessions: List<TableSession>,
        ordersInSlot: List<Order>,
        executedIssuesByOrderId: Map<UUID, List<Issue>>,
        timeBySessionId: Map<UUID, PosSessionTime>,
    ): PosTableOrderSummaryListResponse {
        val ordersBySessionId = ordersInSlot.groupBy { it.session.id!! }

        val tableViews = sessions
            .distinctBy { it.id }
            .map { session ->
                val sessionId = session.id!!
                val table = session.table
                val sessionOrders = ordersBySessionId[sessionId].orEmpty()
                val time = timeBySessionId[sessionId]

                val lastEventAt = sessionOrders
                    .flatMap { listOf(it.orderedAt, it.completedAt) }
                    .filterNotNull()
                    .maxOrNull() ?: session.endedAt ?: session.startedAt

                val grossOrderAmount = sumNonCanceledItemsAmount(sessionOrders)

                val refundAmount = sessionOrders
                    .mapNotNull { it.id }
                    .flatMap { orderId -> executedIssuesByOrderId[orderId].orEmpty() }
                    .sumAmount()

                val netAmount = (grossOrderAmount - refundAmount).coerceAtLeast(0L)

                val (itemCount, preview) = buildPreview(sessionOrders)

                PosTableOrderSummary(
                    sessionId = sessionId,
                    tableId = table.id!!,
                    tableNumber = table.tableNumber,
                    tableStatus = table.status,
                    lastEventAt = lastEventAt,
                    netAmount = netAmount,
                    itemCount = itemCount,
                    itemPreview = preview,
                    enteredAt = time?.enteredAt,
                    expiresAt = time?.expiresAt,
                    remainingMinutes = time?.remainingMinutes,
                    timeOver = time?.timeOver ?: false,
                )
            }
            .sortedByDescending { it.lastEventAt }

        return PosTableOrderSummaryListResponse(
            storeId = storeId,
            timeSlotId = timeSlotId,
            mode = mode,
            tables = tableViews
        )
    }


    fun detail(
        storeId: UUID,
        timeSlotId: UUID,
        sessionId: UUID?,
        mode: PosOrderViewMode,
        table: StoreTable,
        orders: List<Order>,
        latestIssueByOrderItemId: Map<UUID, Issue>,
        executedIssuesByOrderId: List<Issue>,
        time: PosSessionTime?,
    ): PosTableOrderDetailResponse {

        val grossOrderAmount = sumNonCanceledItemsAmount(orders)
        val refundAmount = executedIssuesByOrderId.sumAmount()
        val netAmount = (grossOrderAmount - refundAmount).coerceAtLeast(0L)

        val groups = orders.map { o ->
            PosOrderGroup(
                groupNo = o.sessionOrderSequence,
                orderedAt = o.orderedAt,
                orderId = o.id!!,
                status = o.status,
                lines = o.orderItems
                    .sortedBy { it.id.toString() }
                    .map { oi ->
                        val unit = oi.price.toLongSafe()
                        val qty = oi.quantity
                        val linePrice = unit * qty.toLong()
                        val issue = latestIssueByOrderItemId[oi.id!!]

                        PosOrderLine(
                            lineId = oi.id!!,
                            menuId = oi.menu.id!!,
                            menuName = oi.name,
                            qty = qty,
                            unitPrice = unit,
                            linePrice = linePrice,
                            itemStatus = oi.status,
                            issue = issue?.toIssueSummary()
                        )
                    }
            )
        }

        return PosTableOrderDetailResponse(
            storeId = storeId,
            timeSlotId = timeSlotId,
            sessionId = sessionId,
            mode = mode,
            table = PosTableHeader(
                tableId = table.id!!,
                tableNumber = table.tableNumber,
                tableStatus = table.status,
                enteredAt = time?.enteredAt,
                expiresAt = time?.expiresAt,
                remainingMinutes = time?.remainingMinutes,
                timeOver = time?.timeOver ?: false,
            ),
            summary = PosPaymentSummary(
                netAmount = netAmount,
                grossOrderAmount = grossOrderAmount,
                refundAmount = refundAmount
            ),
            orderGroups = groups
        )
    }

    private fun Issue.toIssueSummary(): PosIssueSummary =
        PosIssueSummary(
            issueId = this.id!!,
            kind = this.kind,
            status = this.status,
            originalAmount = this.originalAmount.toLongSafe(),
            afterAmount = this.afterAmount.toLongSafe(),
            deltaAmount = this.refundAmount().toLongSafe(),
            openedAt = this.openedAt,
            closedAt = this.closedAt
        )

    private fun sumNonCanceledItemsAmount(orders: List<Order>): Long {
        var acc = 0L
        for (o in orders) {
            for (it in o.orderItems) {
                if (it.status == OrderItemStatus.CANCELED) continue
                if (it.quantity <= 0) continue
                acc += it.price.toLongSafe() * it.quantity.toLong()
            }
        }
        return acc
    }

    private fun buildPreview(orders: List<Order>): Pair<Int, String> {
        val names = mutableListOf<String>()
        var count = 0

        for (o in orders) {
            for (it in o.orderItems) {
                if (it.status == OrderItemStatus.CANCELED) continue
                if (it.quantity <= 0) continue
                count += it.quantity
                names.add(it.name)
            }
        }
        if (count == 0) return 0 to ""

        val first = names.firstOrNull().orEmpty()
        val second = names.drop(1).firstOrNull()
        val preview = if (second == null) {
            "$first 외 총 ${count}개"
        } else {
            "$first, $second 외 총 ${count}개"
        }
        return count to preview
    }

    private fun List<Issue>.sumAmount(): Long =
        this.fold(0L) { acc, issue -> acc + issue.refundAmount().toLongSafe() }

    private fun BigDecimal.toLongSafe(): Long = this.toLong()
}


data class PosSessionTime(
    val enteredAt: LocalDateTime?,
    val expiresAt: LocalDateTime?,
    val remainingMinutes: Long?,
    val timeOver: Boolean,
)
