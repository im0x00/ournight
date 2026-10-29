package com.ournight.api_server.domain.order.model

import com.ournight.api_server.global.error.ErrorCode
import com.ournight.api_server.global.error.exception.BusinessException
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

enum class IssueTargetType {
    ORDER,
    ORDER_ITEM
}


enum class IssueStatus {
    OPEN,        // 요청됨
    EXECUTED,    // 적용됨
    REJECTED,    // 거절됨
}

enum class IssueKind {
    REFUND,     // 이미 받은 돈을 돌려줌
    DECREASE,   // 금액만 감액
}


@Entity
@Table(
    name = "issue",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_issue_order_client_request",
            columnNames = ["order_id", "client_request_id"]
        )
    ]
)
class Issue(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "UUID")
    val id: UUID? = null,

    @Column(name = "order_id", nullable = false, columnDefinition = "UUID")
    val orderId: UUID,

    @Column(name = "order_item_id", columnDefinition = "UUID")
    val orderItemId: UUID? = null,

    @Column(name = "store_id", nullable = false, columnDefinition = "UUID")
    val storeId: UUID,

    @Column(name = "time_slot_id", nullable = false, columnDefinition = "UUID")
    val timeSlotId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false)
    val targetType: IssueTargetType,

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    val kind: IssueKind,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: IssueStatus = IssueStatus.OPEN,

    @Column(name = "original_amount", nullable = false, precision = 19, scale = 2)
    val originalAmount: BigDecimal,

    @Column(name = "after_amount", nullable = false, precision = 19, scale = 2)
    val afterAmount: BigDecimal,

    @Column(name = "original_quantity")
    val originalQuantity: Int? = null, //OrderItem을 환불하는 경우

    @Column(name = "after_quantity")
    val afterQuantity: Int? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_type", nullable = false)
    val reasonType: AdjustmentReasonType,

    @Column(name = "reject_reason", length = 500)
    var rejectReason: String? = null, // 지금 UI에는 없지만 환불금 조정 혹은 환불 취소 사유

    @Column(name = "client_request_id", nullable = false, columnDefinition = "UUID")
    val clientRequestId: UUID,

    @Column(name = "opened_at", nullable = false)
    val openedAt: LocalDateTime,

    @Column(name = "closed_at")
    var closedAt: LocalDateTime? = null,

    @Column(name = "table_number_snapshot", nullable = false)
    val tableNumberSnapshot: Int,

    @Column(name = "menu_summary_snapshot", nullable = false, length = 300)
    val menuSummarySnapshot: String,
) {
    init {
        require(originalAmount >= BigDecimal.ZERO) { "originalAmount must be >= 0" }
        require(afterAmount >= BigDecimal.ZERO) { "finalAmount must be >= 0" }
        require(afterAmount <= originalAmount) { "finalAmount must be <= originalAmount" }
        require(
            (targetType == IssueTargetType.ORDER && orderItemId == null) ||
                    (targetType == IssueTargetType.ORDER_ITEM && orderItemId != null)
        ) {
            "targetType and orderItemId are inconsistent."
        }
    }

    fun refundAmount(): BigDecimal = originalAmount.subtract(afterAmount)

    fun refundQuantity(): Int {
        if (originalQuantity == null || afterQuantity == null) {
            throw BusinessException(ErrorCode.INVALID_ISSUE_WITH_NO_QUANTITY)
        }
        return originalQuantity!! - afterQuantity!!
    }

    fun execute(now: LocalDateTime) {
        check(status == IssueStatus.OPEN) { "Only OPEN issue can be executed." }
        status = IssueStatus.EXECUTED
        closedAt = now
    }

    fun reject(reason: String? = null, now: LocalDateTime) {
        check(status == IssueStatus.OPEN) { "Only OPEN issue can be rejected." }
        status = IssueStatus.REJECTED
        closedAt = now
        rejectReason = reason
    }
}
