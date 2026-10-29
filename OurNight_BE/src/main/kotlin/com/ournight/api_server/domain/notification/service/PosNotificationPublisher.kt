package com.ournight.api_server.domain.notification.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.ournight.api_server.domain.notification.event.CookingRequestedEvent
import com.ournight.api_server.domain.notification.event.CustomerCallEvent
import com.ournight.api_server.domain.notification.event.CustomerCallResolvedEvent
import com.ournight.api_server.domain.notification.event.HallOrderItemCanceledEvent
import com.ournight.api_server.domain.notification.event.HallOrderItemDecreasedEvent
import com.ournight.api_server.domain.notification.event.HallPaymentCanceledEvent
import com.ournight.api_server.domain.notification.event.HallPaymentResolvedEvent
import com.ournight.api_server.domain.notification.event.HallServeResolvedEvent
import com.ournight.api_server.domain.notification.event.KitchenCookResolvedEvent
import com.ournight.api_server.domain.notification.event.KitchenOrderItemCanceledEvent
import com.ournight.api_server.domain.notification.event.KitchenOrderItemDecreasedEvent
import com.ournight.api_server.domain.notification.event.KitchenMenuChangedEvent
import com.ournight.api_server.domain.notification.event.OrdersReassignedToSessionEvent
import com.ournight.api_server.domain.notification.event.PaymentVerificationNeededEvent
import com.ournight.api_server.domain.notification.event.PosReloadRequiredEvent
import com.ournight.api_server.domain.notification.event.ServingRequestedEvent
import com.ournight.api_server.domain.notification.event.TableSummaryChangedEvent
import com.ournight.api_server.domain.notification.model.PosEventOutbox
import com.ournight.api_server.domain.notification.repository.PosEventOutboxRepository
import com.ournight.api_server.domain.order.repository.OrderRepository
import com.ournight.api_server.domain.session.repository.TableSessionRepository
import com.ournight.api_server.domain.store.service.StoreTimeSlotService
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.util.UUID

@Component
class PosNotificationPublisher(
    private val objectMapper: ObjectMapper,
    private val outboxRepository: PosEventOutboxRepository,
    private val orderRepository: OrderRepository,
    private val tableSessionRepository: TableSessionRepository,
    private val storeTimeSlotService: StoreTimeSlotService,
) {

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun handlePaymentVerification(event: PaymentVerificationNeededEvent) {
        if (!isCurrentSlotOrderEvent(event.storeId, event.orderId)) return
        enqueueOutbox(event.storeId, "HALL_PAYMENT", event)
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun handleCookingRequest(event: CookingRequestedEvent) {
        if (!isCurrentSlotOrderEvent(event.storeId, event.orderId)) return
        enqueueOutbox(event.storeId, "KITCHEN_COOK", event)
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun handleServingRequest(event: ServingRequestedEvent) {
        if (!isCurrentSlotOrderEvent(event.storeId, event.orderId)) return
        enqueueOutbox(event.storeId, "HALL_SERVE", event)
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun handleCustomerCall(event: CustomerCallEvent) {
        enqueueOutbox(event.storeId, "CUSTOMER_CALL", event)
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun handleHallPaymentResolved(event: HallPaymentResolvedEvent) {
        if (!isCurrentSlotOrderEvent(event.storeId, event.orderId)) return
        enqueueOutbox(event.storeId, "HALL_PAYMENT_RESOLVED", event)
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun handleHallPaymentCanceled(event: HallPaymentCanceledEvent) {
        if (!isCurrentSlotOrderEvent(event.storeId, event.orderId)) return
        enqueueOutbox(event.storeId, "HALL_PAYMENT_CANCELED", event)
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun handleCallResolved(event: CustomerCallResolvedEvent) {
        enqueueOutbox(event.storeId, "CUSTOMER_CALL_RESOLVED", event)
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun handleKitchenCookResolved(event: KitchenCookResolvedEvent) {
        if (!isCurrentSlotOrderEvent(event.storeId, event.orderId)) return
        enqueueOutbox(event.storeId, "KITCHEN_COOK_RESOLVED", event)
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun handleHallServeResolved(event: HallServeResolvedEvent) {
        if (!isCurrentSlotOrderEvent(event.storeId, event.orderId)) return
        enqueueOutbox(event.storeId, "HALL_SERVE_RESOLVED", event)
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun handleHallOrderItemCanceled(event: HallOrderItemCanceledEvent) {
        if (!isCurrentSlotOrderEvent(event.storeId, event.orderId)) return
        enqueueOutbox(event.storeId, "HALL_ORDER_ITEM_CANCELED", event)
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun handleKitchenOrderItemCanceled(event: KitchenOrderItemCanceledEvent) {
        if (!isCurrentSlotOrderEvent(event.storeId, event.orderId)) return
        enqueueOutbox(event.storeId, "KITCHEN_ORDER_ITEM_CANCELED", event)
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun handleHallOrderItemDecreased(event: HallOrderItemDecreasedEvent) {
        if (!isCurrentSlotOrderEvent(event.storeId, event.orderId)) return
        enqueueOutbox(event.storeId, "HALL_ORDER_ITEM_DECREASED", event)
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun handleKitchenOrderItemDecreased(event: KitchenOrderItemDecreasedEvent) {
        if (!isCurrentSlotOrderEvent(event.storeId, event.orderId)) return
        enqueueOutbox(event.storeId, "KITCHEN_ORDER_ITEM_DECREASED", event)
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun handleOrdersReassigned(event: OrdersReassignedToSessionEvent) {
        if (!event.orderIds.any { isCurrentSlotOrderEvent(event.storeId, it) }) return
        enqueueOutbox(event.storeId, "ORDERS_REASSIGNED_TO_SESSION", event)
    }

    private fun isCurrentSlotOrderEvent(storeId: UUID, orderId: UUID): Boolean {
        val order = orderRepository.findById(orderId).orElse(null) ?: return false
        val activeSlot = storeTimeSlotService.findActiveSlotOrNull(storeId)
        if (activeSlot != null && order.timeSlot.id == activeSlot.id) return true

        val liveSession = tableSessionRepository.findActiveSessionBasicByTableId(order.table.id!!)
            ?: return false

        return liveSession.id == order.session.id
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun handleTableSummaryChanged(event: TableSummaryChangedEvent) {
        enqueueOutbox(event.storeId, "TABLE_SUMMARY_CHANGED", event)
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun handleReloadRequired(event: PosReloadRequiredEvent) {
        enqueueOutbox(event.storeId, "RELOAD_REQUIRED", event)
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun handleKitchenMenuChanged(event: KitchenMenuChangedEvent) {
        enqueueOutbox(event.storeId, "KITCHEN_MENU_CHANGED", event)
    }

    private fun enqueueOutbox(storeId: UUID, type: String, payload: Any) {
        outboxRepository.save(
            PosEventOutbox(
                storeId = storeId,
                eventType = type,
                payload = objectMapper.writeValueAsString(payload),
            )
        )
    }
}
