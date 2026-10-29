package com.ournight.api_server.domain.order.service

import com.ournight.api_server.api.customer.dto.UserOrderItemResponse
import com.ournight.api_server.api.customer.dto.UserOrderResponse
import com.ournight.api_server.api.pos.dto.HallPaymentResponse
import com.ournight.api_server.api.pos.dto.HallServingResponse
import com.ournight.api_server.api.pos.dto.OrderItemInRoundResponse
import com.ournight.api_server.api.pos.dto.OrderRoundResponse
import com.ournight.api_server.api.pos.dto.PosOrderViewMode
import com.ournight.api_server.api.pos.dto.PosTableOrderDetailResponse
import com.ournight.api_server.api.pos.dto.PosTableOrderSummaryListResponse
import com.ournight.api_server.domain.order.model.IssueStatus
import com.ournight.api_server.domain.order.domain.Order
import com.ournight.api_server.domain.order.domain.OrderItem
import com.ournight.api_server.domain.order.model.OrderCursor
import com.ournight.api_server.domain.order.model.OrderItemStatus
import com.ournight.api_server.domain.order.model.OrderStatus
import com.ournight.api_server.domain.order.repository.IssueRepository
import com.ournight.api_server.domain.order.repository.OrderItemRepository
import com.ournight.api_server.domain.order.repository.OrderRepository
import com.ournight.api_server.domain.order.view.HallOrderCard
import com.ournight.api_server.domain.order.view.HallOrderItem
import com.ournight.api_server.domain.order.view.HallOrderListResult
import com.ournight.api_server.domain.order.view.KitchenOrderCard
import com.ournight.api_server.domain.order.view.KitchenOrderItem
import com.ournight.api_server.domain.order.view.KitchenOrderListResult
import com.ournight.api_server.api.pos.mapper.PosOrderAssembler
import com.ournight.api_server.api.pos.mapper.PosSessionTime
import com.ournight.api_server.domain.session.model.SessionStatus
import com.ournight.api_server.domain.session.model.TableSession
import com.ournight.api_server.domain.session.repository.TableSessionRepository
import com.ournight.api_server.domain.session.service.SessionTimeResolver
import com.ournight.api_server.domain.store.dao.StoreTableRepository
import com.ournight.api_server.domain.store.model.StoreTableStatus
import com.ournight.api_server.domain.store.model.StoreTimeSlot
import com.ournight.api_server.domain.store.service.StoreTimeSlotService
import com.ournight.api_server.global.error.ErrorCode
import com.ournight.api_server.global.error.exception.BusinessException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Service
@Transactional(readOnly = true)
class OrderQueryService(
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository,
    private val tableSessionRepository: TableSessionRepository,
    private val storeTableRepository: StoreTableRepository,
    private val issueRepository: IssueRepository,
    private val storeTimeSlotService: StoreTimeSlotService,
    private val sessionTimeResolver: SessionTimeResolver,
    private val clock: Clock,
) {
    private data class OperationalOrderScope(
        val activeTimeSlotIds: List<UUID>,
        val activeSessionIds: List<UUID>,
    )

    private fun activeSessionOrderScope(storeId: UUID): OperationalOrderScope {
        val activeSessionIds = tableSessionRepository
            .findAllActiveSessionsWithParticipantsByStoreId(storeId)
            .mapNotNull { it.id }

        return OperationalOrderScope(
            activeTimeSlotIds = emptyList(),
            activeSessionIds = activeSessionIds,
        )
    }


    fun listUserOrders(userId: UUID, storeId: UUID, tableId: UUID? = null): List<UserOrderResponse> {
        val orders = if (tableId != null) {
            orderRepository.findUserOrdersInSession(userId, storeId, tableId, SessionStatus.ACTIVE)
        } else {
            orderRepository.findUserOrders(userId, storeId)
        }

        return toUserOrderResponses(orders)
    }

    private fun toUserOrderResponses(orders: List<Order>): List<UserOrderResponse> {
        if (orders.isEmpty()) return emptyList()

        val orderIds = orders.mapNotNull { it.id }
        val itemsByOrderId = orderItemRepository.findAllByOrderIdIn(orderIds)
            .groupBy { it.order.id!! }

        return orders.map { order ->
            UserOrderResponse(
                orderId = order.id!!,
                sessionId = order.session.id!!,
                storeId = order.session.table.store.id!!,
                tableNumber = order.session.table.tableNumber,
                totalAmount = order.totalAmount,
                status = order.status,
                orderedAt = order.orderedAt,
                items = itemsByOrderId[order.id!!]
                    ?.map { item ->
                        val lineTotal = item.price.multiply(BigDecimal.valueOf(item.quantity.toLong()))
                        UserOrderItemResponse(
                            menuId = item.menu.id!!,
                            menuName = item.name,
                            quantity = item.quantity,
                            unitPrice = item.price,
                            lineTotal = lineTotal,
                        )
                    }
                    ?: emptyList()
            )
        }
    }

    fun listKitchenOrders(
        storeId: UUID,
        status: OrderStatus,
        cursor: OrderCursor?,
        limit: Int,
    ): KitchenOrderListResult {
        val scope = activeSessionOrderScope(storeId)

        val fetched = orderRepository.findKitchenOrdersPage(
            storeId = storeId,
            status = status,
            activeTimeSlotIds = scope.activeTimeSlotIds,
            activeSessionIds = scope.activeSessionIds,
            cursor = cursor,
            limitPlusOne = limit + 1
        )

        val hasNext = fetched.size > limit
        val pageOrders = if (hasNext) fetched.take(limit) else fetched

        val orderIds = pageOrders.mapNotNull { it.id }
        val orderItems = orderItemRepository.findKitchenItemsByOrderIds(orderIds)
        val itemsByOrderId = orderItems.groupBy { it.order.id!! }

        val cards = pageOrders.mapNotNull { o ->
            val oid = o.id!!
            val kitchenItems = itemsByOrderId[oid].orEmpty().map { oi ->
                KitchenOrderItem(
                    orderItemId = oi.id!!,
                    menuId = oi.menu.id!!,
                    menuName = oi.menu.name,
                    quantity = oi.quantity,
                    status = oi.status,
                    requiresCooking = oi.menu.requiresCooking
                )
            }

            if (kitchenItems.isEmpty()) return@mapNotNull null

            KitchenOrderCard(
                orderId = oid,
                tableId = o.table.id!!,
                tableNumber = o.table.tableNumber,
                status = o.status,
                totalAmount = o.totalAmount,
                orderedAt = o.orderedAt,
                items = kitchenItems,
                tableOrderSequence = o.sessionOrderSequence
            )
        }

        val nextCursor =
            if (hasNext && pageOrders.isNotEmpty()) {
                val last = pageOrders.last()
                OrderCursor(
                    orderedAt = last.orderedAt,
                    orderId = last.id!!
                )
            } else null

        return KitchenOrderListResult(
            items = cards,
            nextCursor = nextCursor,
            hasNext = hasNext
        )
    }

    fun listKitchenOrdersByIds(storeId: UUID, orderIds: List<UUID>): List<KitchenOrderCard> {
        val scope = activeSessionOrderScope(storeId)
        val orders = orderRepository.findByStoreIdAndIds(
            storeId = storeId,
            orderIds = orderIds,
            activeTimeSlotIds = scope.activeTimeSlotIds,
            activeSessionIds = scope.activeSessionIds,
        )
        val ids = orders.mapNotNull { it.id }
        val orderItems = orderItemRepository.findKitchenItemsByOrderIds(ids)
        val itemsByOrderId = orderItems.groupBy { it.order.id!! }

        return orders.mapNotNull { o ->
            val oid = o.id!!
            val kitchenItems = itemsByOrderId[oid].orEmpty().map { oi ->
                KitchenOrderItem(
                    orderItemId = oi.id!!,
                    menuId = oi.menu.id!!,
                    menuName = oi.menu.name,
                    quantity = oi.quantity,
                    status = oi.status,
                    requiresCooking = oi.menu.requiresCooking
                )
            }

            if (kitchenItems.isEmpty()) return@mapNotNull null

            KitchenOrderCard(
                orderId = oid,
                tableId = o.table.id!!,
                tableNumber = o.table.tableNumber,
                status = o.status,
                totalAmount = o.totalAmount,
                orderedAt = o.orderedAt,
                items = kitchenItems,
                tableOrderSequence = o.sessionOrderSequence
            )
        }
    }

    fun getPendingPaymentOrders(storeId: UUID): List<HallPaymentResponse> {
        val scope = activeSessionOrderScope(storeId)
        if (scope.activeTimeSlotIds.isEmpty() && scope.activeSessionIds.isEmpty()) return emptyList()

        val statuses = listOf(OrderStatus.PENDING_PAYMENT, OrderStatus.PAYMENT_REQUESTED)
        val ordersById = linkedMapOf<UUID, Order>()

        if (scope.activeSessionIds.isNotEmpty()) {
            orderRepository.findAllByStoreIdAndSessionIdAndStatuses(
                storeId = storeId,
                sessionIds = scope.activeSessionIds,
                statuses = statuses,
            ).forEach { order ->
                ordersById[order.id!!] = order
            }
        }

        val orders = ordersById.values.toList()
        return orders.map { order ->
            HallPaymentResponse(
                orderId = order.id!!,
                tableId = order.session.table.id!!,
                tableNumber = order.session.table.tableNumber,
                totalAmount = order.totalAmount,
                status = order.status.name
            )
        }
    }

    fun getServingPendingItems(storeId: UUID): List<HallServingResponse> {
        val scope = activeSessionOrderScope(storeId)
        if (scope.activeTimeSlotIds.isEmpty() && scope.activeSessionIds.isEmpty()) return emptyList()

        val itemStatuses = listOf(OrderItemStatus.READY, OrderItemStatus.COOKED)
        val itemsById = linkedMapOf<UUID, OrderItem>()

        if (scope.activeSessionIds.isNotEmpty()) {
            orderItemRepository.findAllServingPendingBySessionIds(
                storeId = storeId,
                sessionIds = scope.activeSessionIds,
                orderStatus = OrderStatus.CONFIRMED,
                itemStatuses = itemStatuses,
            ).forEach { item ->
                itemsById[item.id!!] = item
            }
        }

        val items = itemsById.values.toList()
        return items.map { item ->
            HallServingResponse(
                orderId = item.order.id!!,
                orderItemId = item.id!!,
                tableId = item.order.session.table.id!!,
                menuId = item.menu.id!!,
                menuName = item.name,
                quantity = item.quantity,
                tableNumber = item.order.session.table.tableNumber,
                status = item.status.name,
                requiresCooking = item.menu.requiresCooking,
                price = item.price.toInt(),
            )
        }
    }

    fun listHallOrders(
        storeId: UUID,
        status: OrderStatus,
        cursor: OrderCursor?,
        limit: Int
    ): HallOrderListResult {
        val scope = activeSessionOrderScope(storeId)

        val fetched = orderRepository.findHallOrdersPage(
            storeId = storeId,
            status = status,
            activeTimeSlotIds = scope.activeTimeSlotIds,
            activeSessionIds = scope.activeSessionIds,
            cursor = cursor,
            limitPlusOne = limit + 1
        )

        val hasNext = fetched.size > limit
        val pageOrders = if (hasNext) fetched.take(limit) else fetched

        val orderIds = pageOrders.mapNotNull { it.id }
        val orderItems = orderRepository.findItemsByOrderIds(orderIds)
        val itemsByOrderId = orderItems.groupBy { it.order.id!! }

        val cards = pageOrders.map { o ->
            val oid = o.id!!
            HallOrderCard(
                orderId = oid,
                tableId = o.table.id!!,
                tableNumber = o.table.tableNumber,
                status = o.status,
                totalAmount = o.totalAmount,
                orderedAt = o.orderedAt,
                items = itemsByOrderId[oid].orEmpty().map { oi ->
                    HallOrderItem(
                        orderItemId = oi.id!!,
                        name = oi.name,
                        quantity = oi.quantity,
                        status = oi.status,
                        menuId = oi.menu.id!!,
                    )
                },
                tableOrderSequence = o.sessionOrderSequence
            )
        }

        val nextCursor =
            if (hasNext && pageOrders.isNotEmpty()) {
                val last = pageOrders.last()
                OrderCursor(
                    orderedAt = last.orderedAt,
                    orderId = last.id!!
                )
            } else null

        return HallOrderListResult(
            items = cards,
            nextCursor = nextCursor,
            hasNext = hasNext
        )
    }

    fun listHallOrdersByIds(storeId: UUID, orderIds: List<UUID>): List<HallOrderCard> {
        val scope = activeSessionOrderScope(storeId)
        val orders = orderRepository.findByStoreIdAndIds(
            storeId = storeId,
            orderIds = orderIds,
            activeTimeSlotIds = scope.activeTimeSlotIds,
            activeSessionIds = scope.activeSessionIds,
        )
        val ids = orders.mapNotNull { it.id }
        val orderItems = orderRepository.findItemsByOrderIds(ids)
        val itemsByOrderId = orderItems.groupBy { it.order.id!! }

        return orders.map { o ->
            val oid = o.id!!
            HallOrderCard(
                orderId = oid,
                tableId = o.table.id!!,
                tableNumber = o.table.tableNumber,
                status = o.status,
                totalAmount = o.totalAmount,
                orderedAt = o.orderedAt,
                items = itemsByOrderId[oid].orEmpty().map { oi ->
                    HallOrderItem(
                        orderItemId = oi.id!!,
                        name = oi.name,
                        quantity = oi.quantity,
                        status = oi.status,
                        menuId = oi.menu.id!!,
                    )
                },
                tableOrderSequence = o.sessionOrderSequence
            )
        }
    }

    /**
     *  POS 호스트 전용
     */

    // POS: active table list
    fun listActiveTables(storeId: UUID, slotId: UUID? = null): PosTableOrderSummaryListResponse {
        val now = LocalDateTime.now(clock)

        val allActiveSessions = tableSessionRepository.findAllActiveSessionsWithParticipantsByStoreId(storeId)
        val requestedSlot = slotId?.let { storeTimeSlotService.getSlot(storeId, it) }
        val scopedSessions = requestedSlot
            ?.let { slot -> allActiveSessions.filter { session -> session.timeSlot.id == slot.id } }
            ?: allActiveSessions
        val timeSlotId = requestedSlot?.id
            ?: storeTimeSlotService.findActiveSlotOrNull(storeId)?.id
            ?: scopedSessions.maxByOrNull { session -> session.timeSlot.endTime }?.timeSlot?.id
            ?: throw BusinessException(ErrorCode.ACTIVE_TIME_SLOT_NOT_FOUND)

        val candidateSessionIds = scopedSessions.mapNotNull { it.id }
        val orders = if (candidateSessionIds.isEmpty()) {
            emptyList()
        } else {
            orderRepository.findBySessionIds(candidateSessionIds)
        }
        val orderedSessionIds = orders.mapNotNull { it.session.id }.toSet()
        val sessions = scopedSessions.filter { session ->
            session.table.status == StoreTableStatus.OCCUPIED || session.id in orderedSessionIds
        }

        val tables = sessions
            .map { it.table }
            .distinctBy { it.id }

        val timeByTableId = sessions.associate { session ->
            session.table.id!! to resolveSessionTime(session, now)
        }

        val sessionIdByTableId = sessions.associate { session ->
            session.table.id!! to session.id
        }

        val orderIds = orders.mapNotNull { it.id }

        val executedIssues = if (orderIds.isEmpty()) {
            emptyList()
        } else {
            issueRepository.findAllByStatusAndOrderIds(IssueStatus.EXECUTED, orderIds)
        }

        val executedByOrderId = executedIssues.groupBy { it.orderId }

        return PosOrderAssembler.summaryList(
            storeId = storeId,
            timeSlotId = timeSlotId,
            mode = PosOrderViewMode.ACTIVE,
            tables = tables,
            ordersInSlot = orders,
            executedIssuesByOrderId = executedByOrderId,
            timeByTableId = timeByTableId,
            sessionIdByTableId = sessionIdByTableId
        )
    }


    // POS: history table list
    fun listHistoryTables(storeId: UUID, slotId: UUID): PosTableOrderSummaryListResponse {
        val slot = storeTimeSlotService.getSlot(storeId, slotId)

        val orders = orderRepository.findAllPosOrdersByTimeSlotIdAndSessionStatus(
            storeId = storeId,
            timeSlotId = slot.id!!,
            sessionStatuses = listOf(SessionStatus.CLOSED)
        )

        val orderIds = orders.mapNotNull { it.id }

        val executedIssues = if (orderIds.isEmpty()) emptyList()
        else issueRepository.findAllByStatusAndOrderIds(IssueStatus.EXECUTED, orderIds)

        val executedByOrderId = executedIssues.groupBy { it.orderId }

        val sessions = orders
            .map { it.session }
            .distinctBy { it.id }

        val now = LocalDateTime.now(clock)
        val timeBySessionId = sessions.associate { session ->
            session.id!! to resolveSessionTime(session, now)
        }

        return PosOrderAssembler.summaryListBySession(
            storeId = storeId,
            timeSlotId = slot.id!!,
            mode = PosOrderViewMode.HISTORY,
            sessions = sessions,
            ordersInSlot = orders,
            executedIssuesByOrderId = executedByOrderId,
            timeBySessionId = timeBySessionId,
        )
    }
    // POS: active table detail
    fun getActiveTableDetail(
        storeId: UUID,
        tableId: UUID,
    ): PosTableOrderDetailResponse {
        validateTableScope(storeId, tableId)
        val session = tableSessionRepository.findActiveSessionBasicByTableId(tableId)
            ?: throw BusinessException(ErrorCode.ACTIVE_SESSION_NOT_FOUND)
        val slot = session.timeSlot

        return buildSessionDetail(
            slot = slot,
            storeId = storeId,
            session = session,
            mode = PosOrderViewMode.ACTIVE
        )
    }

    // POS: historical table detail
    fun getHistorySessionDetail(
        storeId: UUID,
        sessionId: UUID,
        slotId: UUID,
    ): PosTableOrderDetailResponse {
        val session = tableSessionRepository.findById(sessionId)
            .orElseThrow { BusinessException(ErrorCode.SESSION_NOT_FOUND) }

        if (session.table.store.id != storeId) {
            throw BusinessException(ErrorCode.SESSION_NOT_FOUND, "session not in store")
        }

        val slot = storeTimeSlotService.getSlot(storeId, slotId)

        if (session.timeSlot.id != slot.id) {
            throw BusinessException(ErrorCode.SESSION_NOT_FOUND, "session not in requested time slot")
        }

        if (session.status != SessionStatus.CLOSED) {
            throw BusinessException(ErrorCode.SESSION_NOT_FOUND, "session is not closed")
        }

        return buildSessionDetail(
            slot = slot,
            storeId = storeId,
            session = session,
            mode = PosOrderViewMode.HISTORY
        )
    }

    private fun buildSessionDetail(
        slot: StoreTimeSlot,
        storeId: UUID,
        session: TableSession,
        mode: PosOrderViewMode,
    ): PosTableOrderDetailResponse {
        val table = session.table

        val orders = orderRepository.findWithItemsBySessionIdAndTimeSlotId(
            sessionId = session.id!!,
            timeSlotId = slot.id!!,
        )

        val orderIds = orders.mapNotNull { it.id }
        val orderItemIds = orders.flatMap { it.orderItems }.mapNotNull { it.id }

        val itemIssues = if (orderItemIds.isEmpty()) emptyList()
        else issueRepository.findAllByOrderItemIds(orderItemIds)

        val latestIssueByItemId = itemIssues
            .filter { it.orderItemId != null }
            .groupBy { it.orderItemId!! }
            .mapValues { (_, list) -> list.maxBy { it.openedAt } }

        val executedIssues = if (orderIds.isEmpty()) emptyList()
        else issueRepository.findAllByStatusAndOrderIds(IssueStatus.EXECUTED, orderIds)

        return PosOrderAssembler.detail(
            storeId = storeId,
            timeSlotId = slot.id!!,
            sessionId = session.id,
            mode = mode,
            table = table,
            orders = orders,
            latestIssueByOrderItemId = latestIssueByItemId,
            executedIssuesByOrderId = executedIssues,
            time = resolveSessionTime(session, LocalDateTime.now(clock)),
        )
    }

    private fun resolveSessionTime(
        session: TableSession,
        now: LocalDateTime
    ): PosSessionTime {
        val sessionTime = sessionTimeResolver.resolve(session, now)

        return PosSessionTime(
            enteredAt = sessionTime.enteredAt,
            expiresAt = sessionTime.expiresAt,
            remainingMinutes = sessionTime.remainingMinutes,
            timeOver = sessionTime.timeOver,
        )
    }

    /*
    * hall Table Order 페이지용
    * */
    // hall
    fun findRoundsByTable(
        storeId: UUID,
        tableId: UUID
    ): List<OrderRoundResponse> {
        validateTableScope(storeId, tableId)
        val currentSession = tableSessionRepository.findActiveSessionBasicByTableId(tableId) ?: return emptyList()
        val slot = currentSession.timeSlot
        val orders = orderRepository.findWithItemsBySessionIdAndTimeSlotId(currentSession.id!!, slot.id!!)
        return orders.map({ order ->
            OrderRoundResponse(
                orderId = order.id!!,
                sequence = order.sessionOrderSequence,
                orderedAt = order.orderedAt,
                status = order.status,
                items = order.orderItems.map { item ->
                    OrderItemInRoundResponse(
                        orderItemId = item.id!!,
                        menuId = item.menu.id!!,
                        name = item.name,
                        quantity = item.quantity,
                        price = item.price,
                        status = item.status,
                    )
                }
            )
        })
    }


    private fun validateTableScope(storeId: UUID, tableId: UUID) =
        storeTableRepository.findById(tableId)
            .orElseThrow { BusinessException(ErrorCode.TABLE_NOT_FOUND) }
            .also { table ->
                if (table.store.id != storeId) {
                    throw BusinessException(ErrorCode.TABLE_NOT_FOUND, "table not in store")
                }
            }

}
