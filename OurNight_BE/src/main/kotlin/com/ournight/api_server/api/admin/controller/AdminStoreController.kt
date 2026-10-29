package com.ournight.api_server.api.admin.controller

import com.ournight.api_server.api.admin.dto.AdminStoreHostResponse
import com.ournight.api_server.api.admin.dto.AdminStoreHostTransferRequest
import com.ournight.api_server.api.admin.dto.AdminStoreVenueResponse
import com.ournight.api_server.api.host.dto.VenueCreateRequest
import com.ournight.api_server.domain.store.application.StoreService
import com.ournight.api_server.domain.store.domain.Store
import com.ournight.api_server.domain.user.model.User
import com.ournight.api_server.global.annotation.LoginUser
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/admin/stores")
class AdminStoreController(
    private val storeService: StoreService,
) {
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Suppress("UNUSED_PARAMETER")
    fun listStores(
        @LoginUser user: User,
    ): ResponseEntity<List<AdminStoreHostResponse>> {
        val stores = storeService.getStoresForAdminHostTransfer()
        return ResponseEntity.ok(stores.map { it.toAdminResponse() })
    }

    @PatchMapping("/{storeId}/host")
    @PreAuthorize("hasRole('ADMIN')")
    @Suppress("UNUSED_PARAMETER")
    fun transferHost(
        @LoginUser user: User,
        @PathVariable storeId: UUID,
        @RequestBody request: AdminStoreHostTransferRequest,
    ): ResponseEntity<AdminStoreHostResponse> {
        val store = storeService.transferHost(storeId, request.targetUserId)
        return ResponseEntity.ok(store.toAdminResponse())
    }

    @PostMapping("/{storeId}/venue")
    @PreAuthorize("hasRole('ADMIN')")
    @Suppress("UNUSED_PARAMETER")
    fun createVenueForStore(
        @LoginUser user: User,
        @PathVariable storeId: UUID,
        @RequestBody request: VenueCreateRequest,
    ): ResponseEntity<AdminStoreHostResponse> {
        val store = storeService.createVenueForStore(storeId, request)
        return ResponseEntity.ok(store.toAdminResponse())
    }

    @PatchMapping("/{storeId}/host/reclaim")
    @PreAuthorize("hasRole('ADMIN')")
    fun reclaimHost(
        @LoginUser user: User,
        @PathVariable storeId: UUID,
    ): ResponseEntity<AdminStoreHostResponse> {
        val store = storeService.transferHost(storeId, user.id!!)
        return ResponseEntity.ok(store.toAdminResponse())
    }

    @DeleteMapping("/{storeId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Suppress("UNUSED_PARAMETER")
    fun deleteStore(
        @LoginUser user: User,
        @PathVariable storeId: UUID,
    ): ResponseEntity<Void> {
        storeService.hardDeleteStore(storeId)
        return ResponseEntity.noContent().build()
    }

    private fun Store.toAdminResponse(): AdminStoreHostResponse {
        val venue = venue
        return AdminStoreHostResponse(
            storeId = id!!,
            name = name,
            type = type.name,
            isActive = isActive,
            hostUserId = host.id!!,
            hostKakaoId = host.kakaoId,
            hostNickname = host.nickname,
            venue = venue?.let {
                AdminStoreVenueResponse(
                    venueId = it.id!!,
                    name = it.name,
                    address = it.address,
                    detailLocation = it.detailLocation,
                    latitude = it.latitude,
                    longitude = it.longitude,
                    isPartner = it.isPartner,
                    universityId = it.university?.id,
                    universityName = it.university?.name,
                )
            },
        )
    }
}
