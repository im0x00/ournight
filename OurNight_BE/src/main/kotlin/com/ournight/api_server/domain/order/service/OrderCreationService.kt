package com.ournight.api_server.domain.order.service

import com.ournight.api_server.api.pos.dto.CreateHallOrderRequest
import com.ournight.api_server.api.pos.dto.PosOrderViewMode
import com.ournight.api_server.domain.notification.event.CookingRequestedEvent
import com.ournight.api_server.domain.notification.event.PaymentVerificationNeededEvent
import com.ournight.api_server.domain.notification.event.ServingRequestedEvent
import com.ournight.api_server.domain.notification.service.TableSummaryEventFactory
import com.ournight.api_server.domain.order.domain.Order
import com.ournight.api_server.domain.order.domain.OrderItem
import com.ournight.api_server.domain.order.model.CartItem
import com.ournight.api_server.domain.order.model.OrderConfirmedForStatEvent
import com.ournight.api_server.domain.order.model.OrderItemStatus
import com.ournight.api_server.domain.order.model.OrderStatus
import com.ournight.api_server.domain.order.model.OutOfStockException
import com.ournight.api_server.domain.order.model.StatOrderItemInfo
import com.ournight.api_server.domain.order.repository.OrderRepository
import com.ournight.api_server.domain.pass.repository.PassRepository
import com.ournight.api_server.domain.session.repository.SessionParticipantRepository
import com.ournight.api_server.domain.session.repository.TableSessionRepository
import com.ournight.api_server.domain.session.service.SessionService
import com.ournight.api_server.domain.session.model.SessionStatus
import com.ournight.api_server.domain.session.service.SessionTimeResolver
import com.ournight.api_server.domain.store.dao.StoreMenuRepository
import com.ournight.api_server.domain.store.domain.StoreMenu
import com.ournight.api_server.domain.store.domain.StoreMenuStatus
import com.ournight.api_server.domain.store.service.StoreTimeSlotService
import com.ournight.api_server.global.error.ErrorCode
import com.ournight.api_server.global.error.exception.BusinessException
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

@Service
@Transactional
class OrderCreationService(
    private val orderRepository: OrderRepository,
    private val sessionService: SessionService,
    private val storeMenuRepository: StoreMenuRepository,
    private val storeTimeSlotService: StoreTimeSlotService,
    private val tableSessionRepository: TableSessionRepository,
    private val passRepository: PassRepository,
    private val sessionParticipantRepository: SessionParticipantRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val tableSummaryEventFactory: TableSummaryEventFactory,
    private val sessionTimeResolver: SessionTimeResolver,
    private val clock: Clock,
) {

    fun createOrderFromCart(
        tableId: UUID,
        userId: UUID,
        commitId: UUID,
        items: List<CartItem>,
        cartVersion: Int
    ): UUID {
        validateCartItems(items)

        val session = tableSessionRepository.findActiveSessionByTableId(tableId)
            ?: throw BusinessException(ErrorCode.ACTIVE_SESSION_NOT_FOUND)
        if (session.status != SessionStatus.ACTIVE) {
            throw BusinessException(ErrorCode.PASS_NOT_ORDERABLE)
        }

        val storeId = session.table.store.id!!
        val activeSlot = storeTimeSlotService.getActiveSlot(storeId)
        val now = LocalDateTime.now(clock)
        if (session.timeSlot.id != activeSlot.id) {
            throw BusinessException(ErrorCode.TIMESLOT_EXPIRED)
        }
        if (sessionTimeResolver.resolve(session, now).timeOver) {
            throw BusinessException(ErrorCode.TIMESLOT_EXPIRED)
        }
        val pass = passRepository.findByUserIdAndTimeSlotId(userId, activeSlot.id!!)
            ?: throw BusinessException(ErrorCode.PASS_NOT_FOUND)

        if (!pass.isOrderable(now)) {
            throw BusinessException(ErrorCode.PASS_NOT_ORDERABLE)
        }

        val participant = sessionParticipantRepository.findBySessionIdAndPassId(session.id!!, pass.id!!)
        if (participant == null) {
            throw BusinessException(ErrorCode.FORBIDDEN_USER_IN_SESSION)
        }

        session.table.occupy()

        val menuIds = items.map { it.menuId }

        val menuMap = loadOrderableMenusForUpdate(storeId, menuIds)

        session.nextOrderSeq()

        val order = Order(
            session = session,
            timeSlot = activeSlot,
            commitId = commitId,
            cartVersion = cartVersion,
            status = OrderStatus.PENDING_PAYMENT,
            sessionOrderSequence = session.currentOrderSequence,
            orderedAt = now,
        )

        items.forEach { cartItem ->
            val menu = menuMap[cartItem.menuId]
                ?: throw IllegalStateException("Menu not found: ${cartItem.menuId}")

            val orderItem = OrderItem(
                order = order,
                menu = menu,
                quantity = cartItem.qty,
                price = menu.salePrice,
                name = menu.name,
                status = OrderItemStatus.WAITING
            )
            order.addOrderItem(orderItem)
        }

        val savedOrder = orderRepository.save(order)

        eventPublisher.publishEvent(
            PaymentVerificationNeededEvent(
                storeId = savedOrder.store.id!!,
                orderId = savedOrder.id!!,
                sessionId = savedOrder.session.id!!,
                tableId = savedOrder.session.table.id,
                tableNumber = savedOrder.session.table.tableNumber,
                tableOrderSequence = savedOrder.sessionOrderSequence,
                totalAmount = savedOrder.totalAmount,
                currentOrderStatus = savedOrder.status.name,
            )
        )
        publishTableSummary(savedOrder.table.id!!)

        return savedOrder.id!!
    }

    fun createHallOrder(storeId: UUID, request: CreateHallOrderRequest): UUID {

        if (request.items.isEmpty()) {
            throw BusinessException(ErrorCode.INVALID_ORDER_ITEM)
        }
        request.items.forEach { validateOrderItemQuantity(it.quantity) }

        val session = sessionService.getOrCreateActiveSession(request.tableId)
        if (session.status != SessionStatus.ACTIVE) {
            throw BusinessException(ErrorCode.PASS_NOT_ORDERABLE)
        }
        if (session.table.store.id != storeId) {
            throw BusinessException(ErrorCode.TABLE_NOT_FOUND)
        }
        val now = LocalDateTime.now(clock)
        val activeSlot = storeTimeSlotService.getActiveSlot(storeId)
        if (session.timeSlot.id != activeSlot.id) {
            throw BusinessException(ErrorCode.PASS_NOT_ORDERABLE)
        }
        if (sessionTimeResolver.resolve(session, now).timeOver) {
            throw BusinessException(ErrorCode.PASS_NOT_ORDERABLE)
        }

        session.table.occupy()

        val menuIds = request.items.map { it.menuId }.distinct()

        val menuMap = loadOrderableMenusForUpdate(storeId, menuIds)

        val nextSeq = session.nextOrderSeq()

        val order = Order(
            session = session,
            timeSlot = activeSlot,
            commitId = request.clientRequestId,
            cartVersion = 0,
            status = OrderStatus.CONFIRMED,
            sessionOrderSequence = nextSeq,
            orderedAt = now,
        )

        request.items.forEach { reqItem ->
            val menu = menuMap[reqItem.menuId]
                ?: throw BusinessException(ErrorCode.INVALID_ORDER_ITEM)

            val initialStatus = if (menu.requiresCooking) {
                OrderItemStatus.COOKING
            } else {
                OrderItemStatus.READY
            }

            val orderItem = OrderItem(
                order = order,
                menu = menu,
                quantity = reqItem.quantity,
                price = menu.salePrice,
                name = menu.name,
                status = initialStatus
            )
            order.addOrderItem(orderItem)
        }

        val savedOrder = orderRepository.save(order)

        savedOrder.orderItems
            .filter { it.status != OrderItemStatus.CANCELED }
            .forEach { orderItem ->
                if (orderItem.menu.requiresCooking) {
                    eventPublisher.publishEvent(
                        CookingRequestedEvent(
                            storeId = savedOrder.store.id!!,
                            orderId = savedOrder.id!!,
                            orderItemId = orderItem.id!!,
                            sessionId = savedOrder.session.id!!,
                            tableId = savedOrder.table.id,
                            tableNumber = savedOrder.table.tableNumber,
                            menuId = orderItem.menu.id!!,
                            menuName = orderItem.name,
                            currentQty = orderItem.quantity,
                            requiresCooking = orderItem.menu.requiresCooking,
                            currentItemStatus = orderItem.status.name,
                            currentOrderStatus = savedOrder.status.name,
                            totalAmount = savedOrder.totalAmount,
                            tableOrderSequence = order.sessionOrderSequence,
                            orderedAt = order.orderedAt
                        )
                    )
                } else {
                    eventPublisher.publishEvent(
                        ServingRequestedEvent(
                            storeId = savedOrder.store.id!!,
                            orderId = savedOrder.id!!,
                            orderItemId = orderItem.id!!,
                            sessionId = savedOrder.session.id!!,
                            tableId = savedOrder.table.id,
                            tableNumber = savedOrder.table.tableNumber,
                            menuId = orderItem.menu.id!!,
                            menuName = orderItem.name,
                            currentQty = orderItem.quantity,
                            requiresCooking = orderItem.menu.requiresCooking,
                            currentItemStatus = orderItem.status.name,
                            totalAmount = savedOrder.totalAmount,
                            tableOrderSequence = savedOrder.sessionOrderSequence,
                            orderedAt = order.orderedAt
                        )
                    )
                }
            }

        publishTableSummary(order.table.id!!)

        eventPublisher.publishEvent(
            OrderConfirmedForStatEvent(
                storeId = savedOrder.store.id!!,
                timeSlotId = savedOrder.timeSlot.id!!,
                orderId = savedOrder.id!!,
                totalAmount = savedOrder.totalAmount,
                orderedAt = savedOrder.orderedAt,
                items = savedOrder.orderItems.map {
                    StatOrderItemInfo(it.menu.id!!, it.menu.name, it.quantity, it.price)
                }
            )
        )

        return savedOrder.id!!
    }

    private fun publishTableSummary(tableId: UUID) {
        eventPublisher.publishEvent(tableSummaryEventFactory.create(tableId))
    }

    private fun validateCartItems(items: List<CartItem>) {
        if (items.isEmpty()) {
            throw BusinessException(ErrorCode.INVALID_ORDER_ITEM, "주문 항목이 비어 있습니다.")
        }
        items.forEach { validateOrderItemQuantity(it.qty) }
    }

    private fun validateOrderItemQuantity(quantity: Int) {
        if (quantity <= 0) {
            throw BusinessException(ErrorCode.INVALID_ORDER_ITEM, "주문 수량은 1 이상이어야 합니다.")
        }
    }

    private fun loadOrderableMenusForUpdate(
        storeId: UUID,
        menuIds: List<UUID>
    ): Map<UUID, StoreMenu> {
        val distinctMenuIds = menuIds.distinct().sorted()
        val menus = storeMenuRepository.findAllByStoreIdAndIdsForUpdate(storeId, distinctMenuIds)

        if (menus.size != distinctMenuIds.size) {
            throw BusinessException(ErrorCode.INVALID_ORDER_ITEM)
        }

        val unavailableMenuIds = menus
            .filter { it.status == StoreMenuStatus.NOT_AVAILABLE }
            .mapNotNull { it.id }

        if (unavailableMenuIds.isNotEmpty()) {
            throw OutOfStockException(unavailableMenuIds)
        }

        if (menus.any { it.salePrice <= BigDecimal.ZERO }) {
            throw BusinessException(ErrorCode.INVALID_ORDER_ITEM, "메뉴 가격은 1원 이상이어야 합니다.")
        }

        return menus.associateBy { it.id!! }
    }
}
