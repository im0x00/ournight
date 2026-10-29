package com.ournight.api_server.api.customer.controller

import com.ournight.api_server.api.customer.dto.CustomerTableResponse
import com.ournight.api_server.domain.session.service.SessionService
import com.ournight.api_server.domain.store.application.StoreTableService
import com.ournight.api_server.domain.user.model.User
import com.ournight.api_server.global.annotation.LoginUser
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/customer/tables")
class CustomerTableController(
    private val sessionService: SessionService
) {

    // 비대면 주문을 위한 QR 스캔시 1st step: 테이블 점유 로직 실행 및 기본 정보 반환
    @GetMapping("/{tableId}")
    fun getTable(
        @LoginUser user: User,
        @PathVariable tableId: UUID
    ): ResponseEntity<CustomerTableResponse> {
        val response = sessionService.enterByQr(tableId, user.id!!)
        return ResponseEntity.ok(response)
    }
}