package com.ournight.api_server.api.pos.controller

import com.ournight.api_server.api.host.dto.HallCallRequestResponse
import com.ournight.api_server.domain.call.service.CallService
import com.ournight.api_server.domain.notification.service.PosStreamWatermarkService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/pos/{storeId}/hall/calls")
class HallCallController(
    val callService: CallService,
    private val posStreamWatermarkService: PosStreamWatermarkService,
) {

    // 직원이 호출 완료 처리
    @PostMapping("/{callRequestId}/complete")
    fun completeCall(
        @PathVariable("storeId") storeId: UUID,
        @PathVariable callRequestId: UUID
    ): ResponseEntity<Unit> {
        callService.completeCall(storeId, callRequestId)
        return ResponseEntity.noContent().build()
    }

    @PatchMapping("/{callRequestId}/complete")
    fun completeCallByPatch(
        @PathVariable("storeId") storeId: UUID,
        @PathVariable callRequestId: UUID
    ): ResponseEntity<Unit> = completeCall(storeId, callRequestId)

    // 현재 들어와 있는 모든 호출 목록 조회
    @GetMapping("/pending-calls")
    fun getActiveCallRequests(
        @PathVariable storeId: UUID
    ): ResponseEntity<List<HallCallRequestResponse>> {
        val watermark = posStreamWatermarkService.currentWatermark(storeId)
        val result = callService.getActiveCallRequests(storeId)
        return posStreamWatermarkService.okWithWatermark(watermark, result)
    }

}
