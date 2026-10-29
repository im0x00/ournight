package com.ournight.api_server.api.admin.controller

import com.ournight.api_server.api.admin.dto.AdminPassResponse
import com.ournight.api_server.api.admin.dto.AdminPassStatusUpdateRequest
import com.ournight.api_server.api.admin.dto.toAdminPassResponse
import com.ournight.api_server.domain.pass.service.PassService
import com.ournight.api_server.domain.user.model.User
import com.ournight.api_server.global.annotation.LoginUser
import jakarta.validation.Valid
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
@RequestMapping("/api/admin/passes")
class AdminPassController(
    private val passService: PassService,
) {
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Suppress("UNUSED_PARAMETER")
    fun listPasses(
        @LoginUser user: User,
    ): ResponseEntity<List<AdminPassResponse>> =
        ResponseEntity.ok(passService.getAdminPasses().map { it.toAdminPassResponse() })

    @PatchMapping("/{passId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Suppress("UNUSED_PARAMETER")
    fun updatePassStatus(
        @LoginUser user: User,
        @PathVariable passId: UUID,
        @RequestBody @Valid request: AdminPassStatusUpdateRequest,
    ): ResponseEntity<AdminPassResponse> =
        ResponseEntity.ok(passService.updateAdminPassStatus(passId, request.status).toAdminPassResponse())
}
