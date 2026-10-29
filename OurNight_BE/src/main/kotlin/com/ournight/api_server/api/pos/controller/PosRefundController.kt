package com.ournight.api_server.api.pos.controller

import com.ournight.api_server.api.host.dto.IssueSummaryResponse
import com.ournight.api_server.api.host.dto.IssueActionRequest
import com.ournight.api_server.domain.order.model.AdjustmentReasonType
import com.ournight.api_server.domain.order.service.IssueService
import com.ournight.api_server.domain.user.model.User
import com.ournight.api_server.global.annotation.CheckStoreOwner
import com.ournight.api_server.global.annotation.LoginUser
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class CreateServedItemRefundRequest(
    val quantity: Int,
    val reasonType: AdjustmentReasonType,
    val clientRequestId: UUID,
)

@RestController
@RequestMapping("/api/pos/{storeId}/refunds")
class PosRefundController(
    private val issueService: IssueService,
) {

    @GetMapping("/active")
    @CheckStoreOwner
    fun getActiveIssues(
        @LoginUser user: User,
        @PathVariable("storeId") storeId: UUID,
        @RequestParam slotId: UUID,
    ): List<IssueSummaryResponse> {
        return issueService.getActiveIssues(storeId, slotId)
    }

    @GetMapping("/history")
    @CheckStoreOwner
    fun getHistoricalIssues(
        @LoginUser user: User,
        @PathVariable("storeId") storeId: UUID,
        @RequestParam slotId: UUID,
    ): List<IssueSummaryResponse> {
        return issueService.getHistoryIssues(storeId, slotId)
    }

    @PostMapping("/{issueId}/actions")
    @CheckStoreOwner
    fun act(
        @LoginUser user: User,
        @PathVariable("storeId") storeId: UUID,
        @PathVariable("issueId") issueId: UUID,
        @RequestBody request: IssueActionRequest,
    ): IssueSummaryResponse {
        return issueService.act(storeId, issueId, request)
    }

    @PostMapping("/order-items/{orderItemId}")
    @CheckStoreOwner
    fun createServedItemRefund(
        @LoginUser user: User,
        @PathVariable("storeId") storeId: UUID,
        @PathVariable("orderItemId") orderItemId: UUID,
        @RequestBody request: CreateServedItemRefundRequest,
    ): IssueSummaryResponse {
        return issueService.requestServedItemRefund(
            storeId = storeId,
            orderItemId = orderItemId,
            quantity = request.quantity,
            reasonType = request.reasonType,
            clientRequestId = request.clientRequestId,
        )
    }
}
