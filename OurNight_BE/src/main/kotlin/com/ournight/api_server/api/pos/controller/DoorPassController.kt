package com.ournight.api_server.api.pos.controller

import com.ournight.api_server.api.pos.dto.ActivationCodeResponse
import com.ournight.api_server.api.pos.dto.DoorStatusResponse
import com.ournight.api_server.api.pos.dto.UpdateWaitingStatusRequest
import com.ournight.api_server.api.pos.dto.toResponse
import com.ournight.api_server.domain.pass.service.PassKeyService
import com.ournight.api_server.domain.store.application.StoreService
import com.ournight.api_server.domain.store.service.StoreTimeSlotService
import com.ournight.api_server.domain.user.model.User
import com.ournight.api_server.global.annotation.LoginUser
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/pos/{storeId}/door/timeslots/{slotId}")
class DoorPassController(
    private val passKeyService: PassKeyService,
    private val storeTimeSlotService: StoreTimeSlotService,
) {

    // activation code 조회. 없으면 생성하지 않고 미설정 상태를 반환한다.
    @GetMapping("/activation-code")
    fun getActivationCode(
        @LoginUser user: User,
        @PathVariable storeId: UUID,
        @PathVariable slotId: UUID
    ): ActivationCodeResponse {
        storeTimeSlotService.validateDoorSlotAccess(user, storeId, slotId)
        val view = passKeyService.getActivationCode(slotId)
        return view.toResponse() // Mapper 적용
    }

    // activation code 생성
    @PostMapping("/activation-code")
    fun createActivationCode(
        @LoginUser user: User,
        @PathVariable storeId: UUID,
        @PathVariable slotId: UUID
    ): ActivationCodeResponse {
        storeTimeSlotService.validateDoorSlotAccess(user, storeId, slotId)
        val view = passKeyService.createActivationCode(slotId)
        return view.toResponse()
    }

    // activation code 재생성
    @PostMapping("/activation-code/reset")
    fun resetActivationCode(
        @LoginUser user: User,
        @PathVariable storeId: UUID,
        @PathVariable slotId: UUID
    ): ActivationCodeResponse {
        storeTimeSlotService.validateDoorSlotAccess(user, storeId, slotId)
        val view = passKeyService.resetActivationCode(slotId)
        return view.toResponse() // Mapper 적용
    }

}
