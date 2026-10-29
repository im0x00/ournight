package com.ournight.api_server.api.host.controller

import com.ournight.api_server.api.host.dto.HostCallOptionUpsertRequest
import com.ournight.api_server.api.host.dto.HallCallRequestResponse
import com.ournight.api_server.domain.call.service.CallService
import com.ournight.api_server.domain.user.model.User
import com.ournight.api_server.global.annotation.LoginUser
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/host/stores/{storeId}/calls")
class HostCallController(
    val callService: CallService
) {
//    // 해당 매장의 호출 옵션 설정 등록
//    @PostMapping("/options")
//    fun createCallOption(
//        @LoginUser user: User,
//        @PathVariable storeId: UUID,
//        @RequestBody request: HostCallOptionUpsertRequest
//    ): ResponseEntity<Unit> {
//        callService.createCallOption(storeId, request)
//        return ResponseEntity.noContent().build()
//    }
//
//    @PutMapping("/options/{optionId}")
//    fun updateCallOption(
//        @LoginUser user: User,
//        @PathVariable storeId: UUID,
//        @PathVariable optionId: UUID,
//        @RequestBody request: HostCallOptionUpsertRequest
//    ): ResponseEntity<Unit> {
//        callService.updateCallOption(storeId, optionId, request)
//        return ResponseEntity.ok().build()
//    }
//
//
//
//    @DeleteMapping("/options/{optionId}")
//    fun deleteCallOption(
//        @LoginUser user: User,
//        @PathVariable storeId: UUID,
//        @PathVariable optionId: UUID
//    ): ResponseEntity<Unit> {
//        callService.deleteCallOption(storeId, optionId)
//        return ResponseEntity.noContent().build()
//    }
//
//


}