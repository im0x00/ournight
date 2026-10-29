package com.ournight.api_server.api.customer.controller

import com.ournight.api_server.api.customer.dto.ActivatePassRequest
import com.ournight.api_server.api.customer.dto.CustomerPassStatusResponse
import com.ournight.api_server.api.customer.dto.CustomerPassResponse
import com.ournight.api_server.api.customer.dto.toResponse
import com.ournight.api_server.api.customer.dto.toStatusResponse
import com.ournight.api_server.domain.pass.service.PassService
import com.ournight.api_server.domain.store.service.StoreTimeSlotService
import com.ournight.api_server.domain.user.model.User
import com.ournight.api_server.global.annotation.LoginUser
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID


@RestController
@RequestMapping("/api/customer/stores/{storeId}")
class CustomerPassController(
    private val passService: PassService,
    private val storeTimeSlotService: StoreTimeSlotService,
) {

    /** 백엔드 현재 시각 기준 활성 슬롯의 패스 상태 */
    @GetMapping("/pass/current")
    fun getCurrentPassStatus(
        @LoginUser user: User,
        @PathVariable storeId: UUID,
    ): CustomerPassStatusResponse {
        val slot = storeTimeSlotService.getActiveSlot(storeId)
        return passService.getPassStatus(user.id!!, storeId, slot.id!!).toStatusResponse()
    }

    /** 백엔드 현재 시각 기준 활성 슬롯에 패스 발급 */
    @PostMapping("/pass/current")
    fun issueCurrentPass(
        @LoginUser user: User,
        @PathVariable storeId: UUID,
    ): CustomerPassResponse {
        val slot = storeTimeSlotService.getActiveSlot(storeId)
        val passView = passService.issuePass(
            userId = user.id!!,
            storeId = storeId,
            slotId = slot.id!!,
        )

        return passView.toResponse()
    }

    /** 해당 스토어·슬롯에 대해 로그인 유저의 패스 존재 여부 및 활성(주문 가능) 여부 */
    @GetMapping("/timeslots/{slotId}/pass")
    fun getPassStatus(
        @LoginUser user: User,
        @PathVariable storeId: UUID,
        @PathVariable slotId: UUID,
    ): CustomerPassStatusResponse {
        return passService.getPassStatus(user.id!!, storeId, slotId).toStatusResponse()
    }

    @PostMapping("/timeslots/{slotId}/pass")
    fun issuePass(
        @LoginUser user: User,
        @PathVariable storeId: UUID,
        @PathVariable slotId: UUID,
    ): CustomerPassResponse {
        val passView = passService.issuePass(
            userId = user.id!!,
            storeId = storeId,
            slotId = slotId,
        )

        return passView.toResponse()
    }

    // PIN 입력을 통한 패스 활성화
    @PostMapping("/timeslots/{slotId}/pass/activate")
    fun activatePass(
        @LoginUser user: User,
        @PathVariable storeId: UUID,
        @PathVariable slotId: UUID,
        @RequestBody @Valid request: ActivatePassRequest
    ): CustomerPassResponse {
        val passView = passService.activatePass(
            userId = user.id!!,
            storeId = storeId,
            slotId = slotId,
            inputCode = request.code
        )

        return passView.toResponse()
    }

}
