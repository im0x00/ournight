package com.ournight.api_server.api.guest.controller

import com.ournight.api_server.api.guest.dto.GuestPassActivateRequest
import com.ournight.api_server.api.guest.dto.GuestPassActivateResponse
import com.ournight.api_server.domain.guest.service.GuestAuthService
import com.ournight.api_server.domain.user.model.CustomUserDetails
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/guest/stores/{storeId}/pass")
class GuestPassController(
    private val guestAuthService: GuestAuthService,
) {
    @PostMapping("/activate")
    fun activatePass(
        @PathVariable storeId: UUID,
        @RequestBody @Valid request: GuestPassActivateRequest,
        @AuthenticationPrincipal principal: CustomUserDetails?,
    ): GuestPassActivateResponse {
        return guestAuthService.activatePass(
            storeId = storeId,
            slotId = request.slotId,
            code = request.code,
            currentUser = principal?.getUser(),
        )
    }
}
