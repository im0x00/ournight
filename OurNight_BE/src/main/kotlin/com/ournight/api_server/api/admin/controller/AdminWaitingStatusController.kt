package com.ournight.api_server.api.admin.controller

import com.ournight.api_server.api.waiting.dto.WaitingStatusResponse
import com.ournight.api_server.api.waiting.dto.WaitingStatusUpdateRequest
import com.ournight.api_server.domain.user.model.User
import com.ournight.api_server.domain.waiting.model.WaitingStatus
import com.ournight.api_server.domain.waiting.service.WaitingStatusService
import com.ournight.api_server.global.annotation.LoginUser
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/admin/waiting-status")
@PreAuthorize("hasRole('ADMIN')")
class AdminWaitingStatusController(
    private val waitingStatusService: WaitingStatusService,
) {
    @GetMapping
    fun getWaitingStatuses(
        @LoginUser user: User,
    ): ResponseEntity<List<WaitingStatusResponse>> {
        return ResponseEntity.ok(waitingStatusService.getAll().map { it.toResponse() })
    }

    @PatchMapping("/{waitingStatusId}")
    fun updateWaitingStatus(
        @LoginUser user: User,
        @PathVariable waitingStatusId: UUID,
        @RequestBody request: WaitingStatusUpdateRequest,
    ): ResponseEntity<WaitingStatusResponse> {
        val updated = waitingStatusService.updateCount(waitingStatusId, request.count)
        return ResponseEntity.ok(updated.toResponse())
    }

    private fun WaitingStatus.toResponse() = WaitingStatusResponse(
        id = id,
        name = name,
        count = count,
        updatedAt = updatedAt,
    )
}
