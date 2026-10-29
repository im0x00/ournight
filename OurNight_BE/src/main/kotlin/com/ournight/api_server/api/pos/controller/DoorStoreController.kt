package com.ournight.api_server.api.pos.controller

import com.ournight.api_server.api.pos.dto.DoorStatusResponse
import com.ournight.api_server.api.pos.dto.UpdateWaitingStatusRequest
import com.ournight.api_server.api.pos.dto.toResponse
import com.ournight.api_server.domain.pass.service.PassKeyService
import com.ournight.api_server.domain.store.service.StoreTimeSlotService
import com.ournight.api_server.domain.user.model.User
import com.ournight.api_server.global.annotation.LoginUser
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID


@RestController
@RequestMapping("/api/pos/{storeId}/door/timeslots/{slotId}")
class DoorStoreController(
    private val storeTimeSlotService: StoreTimeSlotService,
) {
    @GetMapping("/status")
    fun getDoorStatus(
        @LoginUser user: User,
        @PathVariable storeId: UUID,
        @PathVariable slotId: UUID
    ): DoorStatusResponse {
        val view = storeTimeSlotService.getDoorStatus(user, storeId, slotId)
        return view.toResponse()
    }

    @PatchMapping("/waiting-status")
    fun updateWaitingStatus(
        @LoginUser user: User,
        @PathVariable storeId: UUID,
        @PathVariable slotId: UUID,
        @RequestBody request: UpdateWaitingStatusRequest
    ): DoorStatusResponse {
        val view = storeTimeSlotService.updateWaitingStatus(
            user = user,
            storeId = storeId,
            slotId = slotId,
            waitingEnabled = request.waitingEnabled
        )
        return view.toResponse()
    }
}