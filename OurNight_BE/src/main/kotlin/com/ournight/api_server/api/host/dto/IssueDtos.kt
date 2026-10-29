package com.ournight.api_server.api.host.dto

import com.ournight.api_server.domain.order.model.Issue
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

data class IssueSummaryResponse(
    val issueId: UUID,
    val orderId: UUID,
    val orderItemId: UUID?,
    val refundAmount: BigDecimal,
    val refundQuantity: Int?,
    val tableNumber: Int,
    val menuSummary: String,
    val reasonType: String,
    val kind: String,
    val status: String,
    val openedAt: LocalDateTime,
    val closedAt: LocalDateTime?
)

data class IssueActionRequest(
    @field:NotNull
    val actionType: HostIssueActionType,
)

enum class HostIssueActionType {
    EXECUTE,
    REJECT,
}

fun Issue.toSummaryResponse(): IssueSummaryResponse {
    val beforeQuantity = originalQuantity
    val afterQuantity = afterQuantity

    return IssueSummaryResponse(
        issueId = requireNotNull(id),
        orderId = orderId,
        orderItemId = orderItemId,
        refundAmount = refundAmount(),
        refundQuantity = if (beforeQuantity != null && afterQuantity != null) {
            beforeQuantity - afterQuantity
        } else {
            null
        },
        tableNumber = tableNumberSnapshot,
        menuSummary = menuSummarySnapshot,
        reasonType = reasonType.name,
        kind = kind.name,
        status = status.name,
        openedAt = openedAt,
        closedAt = closedAt,
    )
}
