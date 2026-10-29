package com.ournight.api_server.domain.order.service

import com.ournight.api_server.api.pos.dto.OrderItemCancelRequest
import com.ournight.api_server.api.pos.dto.OrderItemDecreaseRequest
import com.ournight.api_server.domain.notification.event.CookingRequestedEvent
import com.ournight.api_server.domain.notification.event.HallOrderItemCanceledEvent
import com.ournight.api_server.domain.notification.event.HallOrderItemDecreasedEvent
import com.ournight.api_server.domain.notification.event.HallPaymentCanceledEvent
import com.ournight.api_server.domain.notification.event.HallPaymentResolvedEvent
import com.ournight.api_server.domain.notification.event.HallServeResolvedEvent
import com.ournight.api_server.domain.notification.event.KitchenCookResolvedEvent
import com.ournight.api_server.domain.notification.event.KitchenOrderItemCanceledEvent
import com.ournight.api_server.domain.notification.event.KitchenOrderItemDecreasedEvent
import com.ournight.api_server.domain.notification.event.PaymentVerificationNeededEvent
import com.ournight.api_server.domain.notification.event.ServingRequestedEvent
import com.ournight.api_server.domain.notification.service.TableSummaryEventFactory
import com.ournight.api_server.domain.order.domain.Order
import com.ournight.api_server.domain.order.domain.OrderItem
import com.ournight.api_server.domain.order.domain.OrderItemChange
import com.ournight.api_server.domain.order.domain.OrderItemChangeType
import com.ournight.api_server.domain.order.model.AdjustmentReasonType
import com.ournight.api_server.domain.order.model.Issue
import com.ournight.api_server.domain.order.model.IssueKind
import com.ournight.api_server.domain.order.model.IssueTargetType
import com.ournight.api_server.domain.order.model.OrderConfirmedForStatEvent
import com.ournight.api_server.domain.order.model.OrderItemStatus
import com.ournight.api_server.domain.order.model.OrderStatus
import com.ournight.api_server.domain.order.model.StatOrderItemInfo
import com.ournight.api_server.domain.order.repository.IssueRepository
import com.ournight.api_server.domain.order.repository.CartRedisRepository
import com.ournight.api_server.domain.order.repository.OrderItemChangeRepository
import com.ournight.api_server.domain.order.repository.OrderItemRepository
import com.ournight.api_server.domain.order.repository.OrderRepository
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
class OrderCommandService(
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository,
    private val orderItemChangeRepository: OrderItemChangeRepository,
    private val issueRepository: IssueRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val tableSummaryEventFactory: TableSummaryEventFactory,
    private val cartRedisRepository: CartRedisRepository,
    private val clock: Clock,
) {

    fun notifyPaymentComplete(orderId: UUID) {
        val order = orderRepository.findByIdForUpdate(orderId)
            ?: throw BusinessException(ErrorCode.ORDER_NOT_FOUND)

        if (order.status != OrderStatus.PENDING_PAYMENT) {
            throw BusinessException(
                ErrorCode.INVALID_ORDER_STATUS,
                "입금 확인 요청을 할 수 없는 상태입니다."
            )
        }

        order.markPaymentVerifying()

        eventPublisher.publishEvent(
            PaymentVerificationNeededEvent(
                storeId = order.store.id!!,
                orderId = order.id!!,
                sessionId = order.session.id!!,
                tableId = order.table.id,
                tableNumber = order.table.tableNumber,
                tableOrderSequence = order.sessionOrderSequence,
                totalAmount = order.totalAmount,
                currentOrderStatus = order.status.name,
            )
        )
    }

    fun confirmOrder(storeId: UUID, orderId: UUID) {
        val order = orderRepository.findByIdForUpdate(orderId)
            ?: throw BusinessException(ErrorCode.ORDER_NOT_FOUND)

        if (order.status == OrderStatus.CONFIRMED) return

        if (
            order.status != OrderStatus.PAYMENT_REQUESTED &&
            order.status != OrderStatus.PENDING_PAYMENT
        ) {
            throw BusinessException(
                ErrorCode.INVALID_ORDER_STATUS,
                "주문을 확정할 수 없는 상태입니다."
            )
        }

        val items = orderItemRepository.findAllByOrderIdWithMenu(orderId)

        order.confirm()
        cartRedisRepository.clearOrderPointer(order.table.id!!)

        eventPublisher.publishEvent(
            OrderConfirmedForStatEvent(
                storeId = order.store.id!!,
                timeSlotId = order.timeSlot.id!!,
                orderId = order.id!!,
                totalAmount = order.totalAmount,
                orderedAt = order.orderedAt,
                items = items.map {
                    StatOrderItemInfo(it.menu.id!!, it.menu.name, it.quantity, it.price)
                }
            )
        )

        eventPublisher.publishEvent(
            HallPaymentResolvedEvent(
                storeId = order.store.id!!,
                orderId = order.id!!,
                sessionId = order.session.id!!,
                tableId = order.table.id,
                tableNumber = order.table.tableNumber,
                totalAmount = order.totalAmount,
                currentOrderStatus = order.status.name,
            )
        )

        items.forEach { orderItem ->
            if (orderItem.status == OrderItemStatus.CANCELED) return@forEach
            if (orderItem.status != OrderItemStatus.WAITING) return@forEach

            if (orderItem.menu.requiresCooking) {
                orderItem.status = OrderItemStatus.COOKING

                eventPublisher.publishEvent(
                    CookingRequestedEvent(
                        storeId = order.store.id!!,
                        orderId = order.id!!,
                        orderItemId = orderItem.id!!,
                        sessionId = order.session.id!!,
                        tableId = order.table.id,
                        tableNumber = order.table.tableNumber,
                        menuId = orderItem.menu.id!!,
                        menuName = orderItem.name,
                        currentQty = orderItem.quantity,
                        requiresCooking = orderItem.menu.requiresCooking,
                        currentItemStatus = orderItem.status.name,
                        currentOrderStatus = order.status.name,
                        totalAmount = order.totalAmount,
                        tableOrderSequence = order.sessionOrderSequence,
                        orderedAt = order.orderedAt
                    )
                )
            } else {
                orderItem.status = OrderItemStatus.READY

                eventPublisher.publishEvent(
                    ServingRequestedEvent(
                        storeId = order.store.id!!,
                        orderId = order.id!!,
                        orderItemId = orderItem.id!!,
                        sessionId = order.session.id!!,
                        tableId = order.table.id,
                        tableNumber = order.table.tableNumber,
                        menuId = orderItem.menu.id!!,
                        menuName = orderItem.name,
                        currentQty = orderItem.quantity,
                        requiresCooking = orderItem.menu.requiresCooking,
                        currentItemStatus = orderItem.status.name,
                        totalAmount = order.totalAmount,
                        tableOrderSequence = order.sessionOrderSequence,
                        orderedAt = order.orderedAt,
                    )
                )
            }
        }

        publishTableSummary(order.table.id!!)
    }

    fun decreaseOrderItem(orderItemId: UUID, request: OrderItemDecreaseRequest) {
        val item = orderItemRepository.findByIdWithOrderAndMenu(orderItemId)
            ?: throw BusinessException(ErrorCode.ORDER_ITEM_NOT_FOUND)

        val order = item.order

        if (order.status == OrderStatus.CANCELED) {
            throw BusinessException(ErrorCode.INVALID_ORDER_STATUS, "이미 취소된 주문입니다.")
        }
        if (order.status == OrderStatus.COMPLETED) {
            throw BusinessException(ErrorCode.INVALID_ORDER_STATUS, "이미 완료된 주문입니다.")
        }

        item.ensureDecreasable()

        val previousStatus = item.status
        val beforeQty = item.quantity
        val now = LocalDateTime.now(clock)

        if (request.quantity <= 0) {
            throw BusinessException(ErrorCode.INVALID_ORDER_STATUS, "감소 수량은 1 이상이어야 합니다.")
        }
        if (request.quantity > beforeQty) {
            throw BusinessException(ErrorCode.ORDER_NEGATIVE_QTY)
        }

        val afterQty = beforeQty - request.quantity
        val changeType =
            if (afterQty == 0) OrderItemChangeType.CANCELED
            else OrderItemChangeType.DECREASED

        val change = orderItemChangeRepository.save(
            OrderItemChange(
                orderItem = item,
                type = changeType,
                delta = request.quantity,
                beforeQty = beforeQty,
                afterQty = afterQty,
                createdAt = now,
                reasonType = request.reasonType
            )
        )

        item.applyDecrease(request.quantity)
        order.recomputeTotalAmount()
        order.checkAndCompleteOrder(now)

        if (requiresIssue(previousStatus)) {
            createIssueForDecrease(
                item = item,
                beforeQty = beforeQty,
                decreaseQty = request.quantity,
                afterQty = afterQty,
                reasonType = request.reasonType,
                clientRequestId = request.clientRequestId,
            )
        }

        when (changeType) {
            OrderItemChangeType.DECREASED -> {
                eventPublisher.publishEvent(
                    HallOrderItemDecreasedEvent(
                        storeId = order.store.id!!,
                        orderId = order.id!!,
                        orderItemId = item.id!!,
                        orderItemChangeId = change.id!!,
                        sessionId = order.session.id!!,
                        tableId = order.table.id,
                        tableNumber = order.table.tableNumber,
                        tableOrderSequence = order.sessionOrderSequence,
                        menuId = item.menu.id!!,
                        menuName = item.menu.name,
                        decreasedQty = request.quantity,
                        currentQty = item.quantity,
                        requiresCooking = item.menu.requiresCooking,
                        previousItemStatus = previousStatus.name,
                        currentItemStatus = item.status.name,
                    )
                )

                if (item.menu.requiresCooking) {
                    eventPublisher.publishEvent(
                        KitchenOrderItemDecreasedEvent(
                            storeId = order.store.id!!,
                            orderId = order.id!!,
                            orderItemId = item.id!!,
                            orderItemChangeId = change.id!!,
                            sessionId = order.session.id!!,
                            tableId = order.table.id,
                            tableNumber = order.table.tableNumber,
                            tableOrderSequence = order.sessionOrderSequence,
                            menuId = item.menu.id!!,
                            menuName = item.menu.name,
                            decreasedQty = request.quantity,
                            currentQty = item.quantity,
                            requiresCooking = item.menu.requiresCooking,
                            previousItemStatus = previousStatus.name,
                            currentItemStatus = item.status.name,
                        )
                    )
                }

            }

            OrderItemChangeType.CANCELED -> {
                eventPublisher.publishEvent(
                    HallOrderItemCanceledEvent(
                        storeId = order.store.id!!,
                        orderId = order.id!!,
                        orderItemId = item.id!!,
                        orderItemChangeId = change.id!!,
                        sessionId = order.session.id!!,
                        tableId = order.table.id,
                        tableNumber = order.table.tableNumber,
                        tableOrderSequence = order.sessionOrderSequence,
                        menuId = item.menu.id!!,
                        menuName = item.menu.name,
                        currentQty = item.quantity,
                        requiresCooking = item.menu.requiresCooking,
                        previousItemStatus = previousStatus.name,
                        currentItemStatus = item.status.name,
                    )
                )

                if (item.menu.requiresCooking) {
                    eventPublisher.publishEvent(
                        KitchenOrderItemCanceledEvent(
                            storeId = order.store.id!!,
                            orderId = order.id!!,
                            orderItemId = item.id!!,
                            orderItemChangeId = change.id!!,
                            sessionId = order.session.id!!,
                            tableId = order.table.id,
                            tableNumber = order.table.tableNumber,
                            tableOrderSequence = order.sessionOrderSequence,
                            menuId = item.menu.id!!,
                            menuName = item.menu.name,
                            currentQty = item.quantity,
                            requiresCooking = item.menu.requiresCooking,
                            previousItemStatus = previousStatus.name,
                            currentItemStatus = item.status.name,
                        )
                    )
                }

            }

        }

        publishTableSummary(order.table.id!!)

    }

    fun cancelOrderItem(orderItemId: UUID, request: OrderItemCancelRequest) {
        val item = orderItemRepository.findByIdWithOrderAndMenu(orderItemId)
            ?: throw BusinessException(ErrorCode.ORDER_ITEM_NOT_FOUND)

        val order = item.order

        if (order.status == OrderStatus.CANCELED) {
            throw BusinessException(ErrorCode.INVALID_ORDER_STATUS, "이미 취소된 주문입니다.")
        }
        if (order.status == OrderStatus.COMPLETED) {
            throw BusinessException(ErrorCode.INVALID_ORDER_STATUS, "이미 완료된 주문입니다.")
        }

        item.ensureCancelable()

        val previousStatus = item.status
        val beforeQty = item.quantity
        val now = LocalDateTime.now(clock)

        val change = orderItemChangeRepository.save(
            OrderItemChange(
                orderItem = item,
                type = OrderItemChangeType.CANCELED,
                delta = beforeQty,
                beforeQty = beforeQty,
                afterQty = 0,
                createdAt = now,
                reasonType = request.reasonType
            )
        )

        item.cancelAll()
        order.recomputeTotalAmount()
        order.checkAndCompleteOrder(now)

        if (requiresIssue(previousStatus)) {
            createIssueForCancelOrderItem(
                item = item,
                beforeQty = beforeQty,
                reasonType = request.reasonType,
                clientRequestId = request.clientRequestId,
            )
        }

        eventPublisher.publishEvent(
            HallOrderItemCanceledEvent(
                storeId = order.store.id!!,
                orderId = order.id!!,
                orderItemId = item.id!!,
                orderItemChangeId = change.id!!,
                sessionId = order.session.id!!,
                tableId = order.table.id,
                tableNumber = order.table.tableNumber,
                tableOrderSequence = order.sessionOrderSequence,
                menuId = item.menu.id!!,
                menuName = item.menu.name,
                currentQty = item.quantity,
                requiresCooking = item.menu.requiresCooking,
                previousItemStatus = previousStatus.name,
                currentItemStatus = item.status.name,
            )
        )

        if (item.menu.requiresCooking) {
            eventPublisher.publishEvent(
                KitchenOrderItemCanceledEvent(
                    storeId = order.store.id!!,
                    orderId = order.id!!,
                    orderItemId = item.id!!,
                    orderItemChangeId = change.id!!,
                    sessionId = order.session.id!!,
                    tableId = order.table.id,
                    tableNumber = order.table.tableNumber,
                    tableOrderSequence = order.sessionOrderSequence,
                    menuId = item.menu.id!!,
                    menuName = item.menu.name,
                    currentQty = item.quantity,
                    requiresCooking = item.menu.requiresCooking,
                    previousItemStatus = previousStatus.name,
                    currentItemStatus = item.status.name,
                )
            )
        }

        publishTableSummary(order.table.id!!)
    }

    fun cancelOrder(orderId: UUID, clientRequestId: UUID) {
        val order = orderRepository.findByIdForUpdate(orderId)
            ?: throw BusinessException(ErrorCode.ORDER_NOT_FOUND)

        val items = orderItemRepository.findAllByOrderIdForUpdate(orderId)

        if (order.status == OrderStatus.COMPLETED || order.status == OrderStatus.CANCELED) {
            throw BusinessException(
                ErrorCode.INVALID_ORDER_STATUS,
                "이미 완료되었거나 취소된 주문입니다."
            )
        }

        if (items.any { it.status == OrderItemStatus.SERVED }) {
            throw BusinessException(
                ErrorCode.INVALID_ORDER_STATUS,
                "이미 서빙된 메뉴가 있어 전체 취소가 불가능합니다. 개별 환불 flow를 이용해주세요."
            )
        }

        val refundableItems = mutableListOf<RefundableItemSnapshot>()
        val now = LocalDateTime.now(clock)

        items.forEach { item ->
            if (item.status == OrderItemStatus.CANCELED) return@forEach

            val previousStatus = item.status
            val beforeQty = item.quantity

            orderItemChangeRepository.save(
                OrderItemChange(
                    orderItem = item,
                    type = OrderItemChangeType.CANCELED,
                    delta = beforeQty,
                    beforeQty = beforeQty,
                    afterQty = 0,
                    createdAt = now,
                    reasonType = AdjustmentReasonType.VOID,
                )
            )

            if (requiresIssue(previousStatus)) {
                refundableItems += RefundableItemSnapshot(
                    menuName = item.name,
                    quantity = beforeQty,
                    unitPrice = item.price
                )
            }

            item.cancelAll()
        }

        if (refundableItems.isNotEmpty()) {
            createIssueForCancelOrder(
                order = order,
                refundableItems = refundableItems,
                reasonType = AdjustmentReasonType.VOID,
                clientRequestId = clientRequestId,
            )
        }
        order.cancel()
        cartRedisRepository.clearOrderPointer(order.table.id!!)

        eventPublisher.publishEvent(
            HallPaymentCanceledEvent(
                storeId = order.store.id!!,
                orderId = order.id!!,
                sessionId = order.session.id!!,
                tableId = order.table.id,
                tableNumber = order.table.tableNumber,
                totalAmount = order.totalAmount,
                currentOrderStatus = order.status.name,
            )
        )

        publishTableSummary(order.table.id!!)
    }

    fun completeCookingItem(orderItemId: UUID) {
        val item = orderItemRepository.findByIdWithOrderAndMenu(orderItemId)
            ?: throw BusinessException(ErrorCode.ORDER_ITEM_NOT_FOUND)

        val order = item.order

        if (order.status == OrderStatus.CANCELED) {
            throw BusinessException(ErrorCode.INVALID_ORDER_STATUS, "이미 취소된 주문입니다.")
        }
        if (order.status == OrderStatus.COMPLETED) {
            throw BusinessException(ErrorCode.INVALID_ORDER_STATUS, "이미 완료된 주문입니다.")
        }

        if (item.status == OrderItemStatus.COOKED || item.status == OrderItemStatus.SERVED) return

        if (item.status != OrderItemStatus.COOKING) {
            throw BusinessException(ErrorCode.INVALID_ORDER_STATUS, "조리를 완료할 수 없는 상태입니다.")
        }

        item.completeCooking()

        eventPublisher.publishEvent(
            KitchenCookResolvedEvent(
                storeId = order.store.id!!,
                orderId = order.id!!,
                orderItemId = item.id!!,
                sessionId = order.session.id!!,
                tableId = order.table.id,
                tableNumber = order.table.tableNumber,
                menuId = item.menu.id!!,
                menuName = item.name,
                currentQty = item.quantity,
                requiresCooking = item.menu.requiresCooking,
                previousItemStatus = OrderItemStatus.COOKING.name,
                currentItemStatus = item.status.name,
            )
        )

        eventPublisher.publishEvent(
            ServingRequestedEvent(
                storeId = order.store.id!!,
                orderId = order.id!!,
                orderItemId = item.id!!,
                sessionId = order.session.id!!,
                tableId = order.table.id,
                tableNumber = order.table.tableNumber,
                menuId = item.menu.id!!,
                menuName = item.name,
                currentQty = item.quantity,
                requiresCooking = item.menu.requiresCooking,
                tableOrderSequence = order.sessionOrderSequence,
                orderedAt = order.orderedAt,
                totalAmount = order.totalAmount,
            )
        )
    }

    fun completeServingItem(orderItemId: UUID) {
        val item = orderItemRepository.findByIdWithOrderAndMenu(orderItemId)
            ?: throw BusinessException(ErrorCode.ORDER_ITEM_NOT_FOUND)

        val order = item.order

        if (order.status == OrderStatus.CANCELED) {
            throw BusinessException(ErrorCode.INVALID_ORDER_STATUS, "이미 취소된 주문입니다.")
        }
        if (order.status == OrderStatus.COMPLETED) {
            throw BusinessException(ErrorCode.INVALID_ORDER_STATUS, "이미 완료된 주문입니다.")
        }

        if (item.status == OrderItemStatus.SERVED) return

        if (item.menu.requiresCooking) {
            if (item.status != OrderItemStatus.COOKED) {
                throw BusinessException(ErrorCode.INVALID_ORDER_STATUS, "서빙 완료할 수 없는 상태입니다.")
            }
        } else {
            if (item.status != OrderItemStatus.READY) {
                throw BusinessException(ErrorCode.INVALID_ORDER_STATUS, "서빙 완료할 수 없는 상태입니다.")
            }
        }

        item.completeServing()
        order.checkAndCompleteOrder(LocalDateTime.now(clock))

        eventPublisher.publishEvent(
            HallServeResolvedEvent(
                storeId = order.store.id!!,
                orderId = order.id!!,
                orderItemId = item.id!!,
                sessionId = order.session.id!!,
                tableId = order.table.id,
                tableNumber = order.table.tableNumber,
                menuId = item.menu.id!!,
                menuName = item.name,
                currentQty = item.quantity,
                requiresCooking = item.menu.requiresCooking,
                currentOrderStatus = order.status.name,
            )
        )
        publishTableSummary(order.table.id!!)
    }

    private fun requiresIssue(status: OrderItemStatus): Boolean {
        return status == OrderItemStatus.COOKING ||
                status == OrderItemStatus.COOKED ||
                status == OrderItemStatus.READY
    }

    private fun publishTableSummary(tableId: UUID) {
        eventPublisher.publishEvent(tableSummaryEventFactory.create(tableId))
    }

    private fun createIssueForDecrease(
        item: OrderItem,
        beforeQty: Int,
        decreaseQty: Int,
        afterQty: Int,
        reasonType: AdjustmentReasonType,
        clientRequestId: UUID,
    ) {
        val unitPrice = item.menu.salePrice
        val originalAmount = unitPrice.multiply(BigDecimal.valueOf(decreaseQty.toLong()))
        val timeSlotId = resolveTimeSlotId(item.order)

        issueRepository.save(
            Issue(
                storeId = item.order.store.id!!,
                timeSlotId = timeSlotId,
                orderId = item.order.id!!,
                orderItemId = item.id!!,
                targetType = IssueTargetType.ORDER_ITEM,
                kind = IssueKind.DECREASE,
                originalAmount = originalAmount,
                afterAmount = unitPrice.multiply(afterQty.toBigDecimal()),
                originalQuantity = beforeQty,
                afterQuantity = afterQty,
                reasonType = reasonType,
                clientRequestId = clientRequestId,
                openedAt = LocalDateTime.now(clock),
                tableNumberSnapshot = item.order.table.tableNumber,
                menuSummarySnapshot = buildSingleMenuSummary(item.name, decreaseQty),
            )
        )
    }

    private fun createIssueForCancelOrderItem(
        item: OrderItem,
        beforeQty: Int,
        reasonType: AdjustmentReasonType,
        clientRequestId: UUID,
    ) {
        val refundAmount = item.menu.salePrice.multiply(BigDecimal.valueOf(beforeQty.toLong()))
        val timeSlotId = resolveTimeSlotId(item.order)

        issueRepository.save(
            Issue(
                storeId = item.order.store.id!!,
                timeSlotId = timeSlotId,
                orderId = item.order.id!!,
                orderItemId = item.id!!,
                targetType = IssueTargetType.ORDER_ITEM,
                kind = IssueKind.REFUND,
                originalAmount = refundAmount,
                afterAmount = BigDecimal.ZERO,
                originalQuantity = beforeQty,
                afterQuantity = 0,
                reasonType = reasonType,
                clientRequestId = clientRequestId,
                openedAt = LocalDateTime.now(clock),
                tableNumberSnapshot = item.order.table.tableNumber,
                menuSummarySnapshot = buildSingleMenuSummary(item.name, beforeQty),
            )
        )
    }

    data class RefundableItemSnapshot(
        val menuName: String,
        val quantity: Int,
        val unitPrice: BigDecimal,
    )

    private fun createIssueForCancelOrder(
        order: Order,
        refundableItems: List<RefundableItemSnapshot>,
        reasonType: AdjustmentReasonType,
        clientRequestId: UUID,
    ) {
        val refundAmount = refundableItems.fold(BigDecimal.ZERO) { acc, item ->
            acc + item.unitPrice.multiply(BigDecimal.valueOf(item.quantity.toLong()))
        }
        val timeSlotId = resolveTimeSlotId(order)

        issueRepository.save(
            Issue(
                storeId = order.store.id!!,
                timeSlotId = timeSlotId,
                orderId = order.id!!,
                orderItemId = null,
                targetType = IssueTargetType.ORDER,
                kind = IssueKind.REFUND,
                originalAmount = refundAmount,
                afterAmount = BigDecimal.ZERO,
                reasonType = reasonType,
                clientRequestId = clientRequestId,
                openedAt = LocalDateTime.now(clock),
                tableNumberSnapshot = order.table.tableNumber,
                menuSummarySnapshot = buildOrderMenuSummary(refundableItems),
            )
        )
    }

    private fun buildSingleMenuSummary(menuName: String, qty: Int): String {
        return if (qty <= 1) menuName else "$menuName ${qty}개"
    }

    private fun buildOrderMenuSummary(items: List<RefundableItemSnapshot>): String {
        val first = items.first().menuName
        val remain = items.size - 1

        return if (remain <= 0) first else "$first 외 ${remain}개"
    }

    private fun resolveTimeSlotId(order: Order): UUID {
        return order.timeSlot.id
            ?: throw BusinessException(ErrorCode.TIMESLOT_INVALID, "주문의 영업 슬롯을 찾을 수 없습니다.")
    }
}
