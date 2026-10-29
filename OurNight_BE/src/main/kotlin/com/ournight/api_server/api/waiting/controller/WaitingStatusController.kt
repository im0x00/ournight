package com.ournight.api_server.api.waiting.controller

import com.ournight.api_server.api.waiting.dto.WaitingStatusResponse
import com.ournight.api_server.domain.waiting.model.WaitingStatus
import com.ournight.api_server.domain.waiting.service.WaitingStatusService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/waiting-status")
class WaitingStatusController(
    private val waitingStatusService: WaitingStatusService,
) {
    @GetMapping
    fun getWaitingStatuses(): ResponseEntity<List<WaitingStatusResponse>> {
        return ResponseEntity.ok(waitingStatusService.getAll().map { it.toResponse() })
    }

    private fun WaitingStatus.toResponse() = WaitingStatusResponse(
        id = id,
        name = name,
        count = count,
        updatedAt = updatedAt,
    )
}
