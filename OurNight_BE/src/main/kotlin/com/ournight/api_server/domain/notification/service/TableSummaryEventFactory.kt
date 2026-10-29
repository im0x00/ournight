package com.ournight.api_server.domain.notification.service

import com.ournight.api_server.domain.notification.event.TableSummaryChangedEvent
import com.ournight.api_server.domain.order.domain.Order
import com.ournight.api_server.domain.order.model.OrderItemStatus
import com.ournight.api_server.domain.order.model.OrderStatus
import com.ournight.api_server.domain.order.repository.OrderRepository
import com.ournight.api_server.domain.session.repository.TableSessionRepository
import com.ournight.api_server.domain.session.service.SessionTimeResolver
import com.ournight.api_server.domain.store.dao.StoreTableRepository
import com.ournight.api_server.global.error.ErrorCode
import com.ournight.api_server.global.error.exception.BusinessException
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

@Component
class TableSummaryEventFactory(
    private val storeTableRepository: StoreTableRepository,
    private val tableSessionRepository: TableSessionRepository,
    private val orderRepository: OrderRepository,
    private val sessionTimeResolver: SessionTimeResolver,
    private val clock: Clock,
) {
    private data class ServingProgress(
        val servedItemCount: Int,
        val unservedItemCount: Int,
    )

    fun create(tableId: UUID): TableSummaryChangedEvent {
        val table = storeTableRepository.findById(tableId)
            .orElseThrow { BusinessException(ErrorCode.TABLE_NOT_FOUND) }

        val activeSession = tableSessionRepository.findActiveSessionByTableId(tableId)
        val now = LocalDateTime.now(clock)

        val sessionOrders = if (activeSession == null) {
            emptyList()
        } else {
            orderRepository.findBySessionIds(listOf(activeSession.id!!))
        }

        val servingProgress = calculateServingProgress(sessionOrders)
        val totalAmount = sessionOrders
            .filter { it.status != OrderStatus.CANCELED }
            .fold(BigDecimal.ZERO) { acc, order -> acc + order.totalAmount }

        val sessionTime = activeSession?.let { sessionTimeResolver.resolve(it, now) }

        return TableSummaryChangedEvent(
            storeId = table.store.id!!,
            tableId = table.id!!,
            tableNumber = table.tableNumber,
            status = table.status,
            sessionId = activeSession?.id,
            sessionStatus = activeSession?.status,
            timeOver = sessionTime?.timeOver ?: false,
            enteredAt = sessionTime?.enteredAt,
            servedOrderItemCount = servingProgress.servedItemCount,
            unservedOrderItemCount = servingProgress.unservedItemCount,
            remainingMinutes = sessionTime?.remainingMinutes,
            totalAmount = totalAmount,
        )
    }

    private fun calculateServingProgress(orders: List<Order>): ServingProgress {
        val activeItems = orders
            .filter { it.status != OrderStatus.CANCELED }
            .flatMap { it.orderItems }
            .filter { it.status != OrderItemStatus.CANCELED }

        val servedItemCount = activeItems
            .filter { it.status == OrderItemStatus.SERVED }
            .sumOf { it.quantity }
        val unservedItemCount = activeItems
            .filter { it.status != OrderItemStatus.SERVED }
            .sumOf { it.quantity }

        return ServingProgress(servedItemCount, unservedItemCount)
    }
}
