package com.ournight.api_server.api.customer.controller

import com.ournight.api_server.api.customer.dto.CustomerCallCreateRequest
import com.ournight.api_server.api.customer.dto.CustomerCallOptionResponse
import com.ournight.api_server.domain.call.service.CallService
import com.ournight.api_server.domain.user.model.User
import com.ournight.api_server.global.annotation.LoginUser
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/customer/calls")
class CustomerCallController(
    private val callService: CallService
) {
    // 고객이 선택할 수 있는 호출 옵션 목록 조회
    @GetMapping("/options")
    fun getAvailableOptions(
        @RequestParam storeId: UUID
    ): ResponseEntity<List<CustomerCallOptionResponse>> {
        val options = callService.getAvailableOptions(storeId)
        return ResponseEntity.ok(options)
    }

    // 고객이 특정 옵션으로 호출 수행
    @PostMapping
    fun makeCall(
        @LoginUser user: User,
        @RequestBody request: CustomerCallCreateRequest
    ): ResponseEntity<Unit> {
        callService.makeCall(user.id!!, request)
        return ResponseEntity.status(HttpStatus.CREATED).build()
    }
}
