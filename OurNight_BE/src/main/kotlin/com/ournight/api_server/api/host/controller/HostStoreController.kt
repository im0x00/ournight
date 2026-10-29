package com.ournight.api_server.api.host.controller

import com.ournight.api_server.api.host.dto.MyStoreSummaryResponse
import com.ournight.api_server.api.host.dto.VenueInfo
import com.ournight.api_server.api.host.dto.StoreTimeSlotResponse
import com.ournight.api_server.api.host.dto.StoreBasicUpdateRequest
import com.ournight.api_server.api.host.dto.StoreDetailResponse
import com.ournight.api_server.api.host.dto.StoreOnboardingCreateRequest
import com.ournight.api_server.api.host.dto.StoreOnboardingCreateResponse
import com.ournight.api_server.api.host.dto.StoreOperatingUpdateRequest
import com.ournight.api_server.api.host.dto.StoreOpsUpdateRequest
import com.ournight.api_server.api.host.dto.StoreTimeSlotsReplaceRequest
import com.ournight.api_server.api.host.dto.StoreTimeSlotsResponse
import com.ournight.api_server.domain.store.application.StoreService
import com.ournight.api_server.api.host.mapper.HostStoreMapper
import com.ournight.api_server.domain.store.service.StoreTimeSlotService
import com.ournight.api_server.domain.store.view.StoreTimeSlotCommand
import com.ournight.api_server.domain.user.model.User
import com.ournight.api_server.global.annotation.LoginUser
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/host/stores")
class HostStoreController(
    private val storeService: StoreService,
    private val timeSlotService: StoreTimeSlotService
) {


    @GetMapping("/{storeId}/time-slots")
    fun getTimeSlots(
        @PathVariable storeId: UUID,
    ): StoreTimeSlotsResponse {
        return timeSlotService.getAllSlots(storeId)
    }

    // 내 상점 목록 조회
    @GetMapping
    fun getMyStores(
        @LoginUser user: User
    ): ResponseEntity<List<MyStoreSummaryResponse>> {
        val stores = storeService.getMyStores(user)
        val response = stores.map { store ->
            MyStoreSummaryResponse(
                storeId = store.id!!,
                name = store.name,
                type = store.type,
                isActive = store.isActive,
                description = store.description,
                timeSlots = store.timeSlots
                    .sortedBy { it.startTime }
                    .map {
                        StoreTimeSlotResponse(
                            slotId = it.id!!,
                            startTime = it.startTime,
                            endTime = it.endTime,
                        )
                    },
                venue = store.venue?.let { venue ->
                    VenueInfo(
                        venueId = venue.id!!,
                        name = venue.name,
                        address = venue.address,
                        detailLocation = venue.detailLocation,
                        latitude = venue.latitude,
                        longitude = venue.longitude,
                    )
                },
                mainImageUrls = store.mainImageUrls.toList(),
            )
        }
        return ResponseEntity.ok(response)
    }

    @PostMapping("/onboarding")
    fun createOnboarding(
        @LoginUser user: User,
        @RequestBody request: StoreOnboardingCreateRequest
    ): ResponseEntity<StoreOnboardingCreateResponse> {
        val result = storeService.createOnboarding(user, HostStoreMapper.toCommand(request))
        return ResponseEntity.status(HttpStatus.CREATED).body(HostStoreMapper.toResponse(result))
    }

    @GetMapping("/{storeId}")
    fun getStoreDetail(
        @LoginUser user: User,
        @PathVariable storeId: UUID
    ): ResponseEntity<StoreDetailResponse> {
        val result = storeService.getStoreDetail(user, storeId)
        return ResponseEntity.ok(HostStoreMapper.toResponse(result))
    }

    @PutMapping("/{storeId}/basic")
    fun updateBasic(
        @LoginUser user: User,
        @PathVariable storeId: UUID,
        @RequestBody request: StoreBasicUpdateRequest
    ): ResponseEntity<StoreOnboardingCreateResponse> {
        val updatedId = storeService.updateBasic(
            user = user,
            storeId = storeId,
            command = HostStoreMapper.toCommand(request)
        )
        return ResponseEntity.ok(StoreOnboardingCreateResponse(storeId = updatedId))
    }

    @PutMapping("/{storeId}/ops")
    fun updateOps(
        @LoginUser user: User,
        @PathVariable storeId: UUID,
        @RequestBody request: StoreOpsUpdateRequest
    ): ResponseEntity<StoreOnboardingCreateResponse> {
        val updatedId = storeService.updateOps(
            user = user,
            storeId = storeId,
            command = HostStoreMapper.toCommand(request)
        )
        return ResponseEntity.ok(StoreOnboardingCreateResponse(storeId = updatedId))
    }

    @PutMapping("/{storeId}/operating")
    fun updateOperating(
        @LoginUser user: User,
        @PathVariable storeId: UUID,
        @RequestBody request: StoreOperatingUpdateRequest
    ): ResponseEntity<StoreOnboardingCreateResponse> {
        val updatedId = storeService.updateOperating(
            user = user,
            storeId = storeId,
            command = HostStoreMapper.toCommand(request)
        )
        return ResponseEntity.ok(StoreOnboardingCreateResponse(storeId = updatedId))
    }

    @PutMapping("/{storeId}/time-slots")
    fun replaceTimeSlots(
        @LoginUser user: User,
        @PathVariable storeId: UUID,
        @RequestBody request: StoreTimeSlotsReplaceRequest
    ): ResponseEntity<StoreOnboardingCreateResponse> {
        val commands = request.timeSlots.map {
            StoreTimeSlotCommand(slotId = it.slotId, startTime = it.startTime, endTime = it.endTime)
        }
        val updatedId = storeService.replaceTimeSlots(user, storeId, commands)
        return ResponseEntity.ok(StoreOnboardingCreateResponse(storeId = updatedId))
    }

    //todo: [gui 완성 후 추가하기]  베뉴가 정의된 경우, 후보 베뉴들을 정보 표시
//    @GetMapping()
//    fun getVenueList(
//
//    )

    //todo: [gui 완성 후 추가하기] venue 선택

    //todo: [gui 완성 후 추가하기] venue upsert 위치
}
