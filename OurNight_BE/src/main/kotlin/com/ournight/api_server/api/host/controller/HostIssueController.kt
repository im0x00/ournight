package com.ournight.api_server.api.host.controller

import com.ournight.api_server.api.host.dto.IssueActionRequest
import com.ournight.api_server.api.host.dto.IssueSummaryResponse
import com.ournight.api_server.domain.order.service.IssueService
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import java.util.UUID

@RestController
@RequestMapping("/api/host/stores/{storeId}/issues")
class HostIssueController(
    private val issueService: IssueService,
) {
    @GetMapping("/active")
    fun getActiveIssues(
        @PathVariable storeId: UUID,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate,
    ): List<IssueSummaryResponse> {
        return issueService.getActiveIssues(storeId, startDate)
    }

    @GetMapping("/history")
    fun getHistoryIssues(
        @PathVariable storeId: UUID,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate,
    ): List<IssueSummaryResponse> {
        return issueService.getHistoryIssues(storeId, startDate)
    }

    @PostMapping("/{issueId}/actions")
    fun act(
        @PathVariable storeId: UUID,
        @PathVariable issueId: UUID,
        @Valid @RequestBody request: IssueActionRequest,
    ): IssueSummaryResponse {
        return issueService.act(storeId, issueId, request)
    }
}