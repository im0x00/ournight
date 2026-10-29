package com.ournight.api_server.domain.order.service


import com.ournight.api_server.api.host.dto.HostIssueActionType
import com.ournight.api_server.api.host.dto.IssueActionRequest
import com.ournight.api_server.api.host.dto.IssueSummaryResponse
import com.ournight.api_server.api.host.dto.toSummaryResponse
import com.ournight.api_server.domain.order.model.AdjustmentReasonType
import com.ournight.api_server.domain.order.model.Issue
import com.ournight.api_server.domain.order.model.IssueExecutedForStatEvent
import com.ournight.api_server.domain.order.model.IssueKind
import com.ournight.api_server.domain.order.model.IssueStatus
import com.ournight.api_server.domain.order.model.IssueTargetType
import com.ournight.api_server.domain.order.model.OrderItemStatus
import com.ournight.api_server.domain.order.repository.IssueRepository
import com.ournight.api_server.domain.order.repository.OrderItemRepository
import com.ournight.api_server.domain.order.repository.OrderRepository
import com.ournight.api_server.domain.store.service.StoreTimeSlotService
import com.ournight.api_server.global.error.ErrorCode
import com.ournight.api_server.global.error.exception.BusinessException
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Service
@Transactional
class IssueService(
    private val issueRepository: IssueRepository,
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val storeTimeSlotService: StoreTimeSlotService,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun getActiveIssues(storeId: UUID, slotId: UUID): List<IssueSummaryResponse> {
        val slot = storeTimeSlotService.getSlot(storeId, slotId)
        return getActiveIssuesBySlotId(storeId, slot.id!!)
    }

    @Transactional(readOnly = true)
    fun getActiveIssues(storeId: UUID, startDate: LocalDate): List<IssueSummaryResponse> {
        val slot = storeTimeSlotService.getSlotStartedOn(storeId, startDate)
        return getActiveIssuesBySlotId(storeId, slot.id!!)
    }

    private fun getActiveIssuesBySlotId(storeId: UUID, slotId: UUID): List<IssueSummaryResponse> {
        return issueRepository
            .findAllByStoreIdAndTimeSlotIdAndStatusOrderByOpenedAtDesc(
                storeId,
                slotId,
                IssueStatus.OPEN
            )
            .map { it.toSummaryResponse() }
    }

    @Transactional(readOnly = true)
    fun getHistoryIssues(storeId: UUID, slotId: UUID): List<IssueSummaryResponse> {
        val slot = storeTimeSlotService.getSlot(storeId, slotId)
        return getHistoryIssuesBySlotId(storeId, slot.id!!)
    }

    @Transactional(readOnly = true)
    fun getHistoryIssues(storeId: UUID, startDate: LocalDate): List<IssueSummaryResponse> {
        val slot = storeTimeSlotService.getSlotStartedOn(storeId, startDate)
        return getHistoryIssuesBySlotId(storeId, slot.id!!)
    }

    private fun getHistoryIssuesBySlotId(storeId: UUID, slotId: UUID): List<IssueSummaryResponse> {
        return issueRepository
            .findAllByStoreIdAndTimeSlotIdAndStatusInOrderByOpenedAtDesc(
                storeId,
                slotId,
                listOf(IssueStatus.EXECUTED, IssueStatus.REJECTED)
            )
            .map { it.toSummaryResponse() }
    }

    fun requestServedItemRefund(
        storeId: UUID,
        orderItemId: UUID,
        quantity: Int,
        reasonType: AdjustmentReasonType,
        clientRequestId: UUID,
    ): IssueSummaryResponse {
        val item = orderItemRepository.findByIdWithOrderAndMenu(orderItemId)
            ?: throw BusinessException(ErrorCode.ORDER_ITEM_NOT_FOUND)
        val order = item.order

        if (order.store.id != storeId) {
            throw BusinessException(ErrorCode.ORDER_NOT_FOUND, "해당 매장의 주문 항목이 아닙니다.")
        }
        if (item.status != OrderItemStatus.SERVED) {
            throw BusinessException(ErrorCode.INVALID_ORDER_STATUS, "서빙 완료된 메뉴만 환불 요청할 수 있습니다.")
        }
        if (quantity <= 0 || quantity > item.quantity) {
            throw BusinessException(ErrorCode.INVALID_ORDER_ITEM, "환불 수량이 올바르지 않습니다.")
        }

        val alreadyRequestedQuantity = issueRepository.findAllByOrderItemIds(listOf(orderItemId))
            .filter { it.status != IssueStatus.REJECTED }
            .sumOf { issue ->
                runCatching { issue.refundQuantity() }.getOrDefault(0)
            }

        if (alreadyRequestedQuantity + quantity > item.quantity) {
            throw BusinessException(ErrorCode.INVALID_ORDER_ITEM, "이미 요청된 환불 수량을 초과합니다.")
        }

        val originalSlot = order.timeSlot
        val refundAmount = item.price.multiply(BigDecimal.valueOf(quantity.toLong()))

        val issue = issueRepository.save(
            Issue(
                storeId = storeId,
                timeSlotId = originalSlot.id!!,
                orderId = order.id!!,
                orderItemId = item.id!!,
                targetType = IssueTargetType.ORDER_ITEM,
                kind = IssueKind.REFUND,
                originalAmount = refundAmount,
                afterAmount = BigDecimal.ZERO,
                originalQuantity = item.quantity,
                afterQuantity = item.quantity - quantity,
                reasonType = reasonType,
                clientRequestId = clientRequestId,
                openedAt = LocalDateTime.now(clock),
                tableNumberSnapshot = order.table.tableNumber,
                menuSummarySnapshot = buildRefundMenuSummary(item.name, quantity),
            )
        )

        return issue.toSummaryResponse()
    }

    fun act(storeId: UUID, issueId: UUID, request: IssueActionRequest): IssueSummaryResponse {
        val issue = issueRepository.findByIdAndStoreId(issueId, storeId)
            ?: throw BusinessException(ErrorCode.ISSUE_NOT_FOUND, "이슈를 찾을 수 없습니다.")

        val originalOrder = orderRepository.findById(issue.orderId).orElseThrow {
            BusinessException(ErrorCode.ORDER_NOT_FOUND)
        }

        val orderItem = issue.orderItemId?.let { orderItemId ->
            orderItemRepository.findById(orderItemId).orElse(null)
        }
        val originalSlot = originalOrder.timeSlot
        val quantum = calculateQuantum(originalSlot.startTime, originalOrder.orderedAt)

        when (request.actionType) {
            HostIssueActionType.EXECUTE -> {
                issue.execute(LocalDateTime.now(clock))

                eventPublisher.publishEvent(
                    IssueExecutedForStatEvent(
                        storeId = issue.storeId,
                        timeSlotId = originalSlot.id!!,
                        timeQuantum = quantum,
                        issueId = issue.id!!,
                        orderId = issue.orderId,
                        orderItemId = issue.orderItemId,
                        menuId = orderItem?.menu?.id,
                        menuName = orderItem?.menu?.name,
                        refundAmount = issue.refundAmount(),
                        refundQuantity = issue.refundQuantity(),
                    )
                )
            }
            HostIssueActionType.REJECT -> issue.reject(now = LocalDateTime.now(clock))
        }

        return issue.toSummaryResponse()
    }

    private fun calculateQuantum(start: LocalDateTime, target: LocalDateTime): Int {
        return (Duration.between(start, target).toMinutes() / 30).toInt().coerceAtLeast(0)
    }

    private fun buildRefundMenuSummary(menuName: String, qty: Int): String {
        return if (qty <= 1) menuName else "$menuName ${qty}개"
    }
}
