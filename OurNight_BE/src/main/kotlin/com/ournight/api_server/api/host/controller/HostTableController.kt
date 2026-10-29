package com.ournight.api_server.api.host.controller

import com.ournight.api_server.api.host.dto.*
import com.ournight.api_server.domain.notification.service.PosStreamWatermarkService
import com.ournight.api_server.api.host.mapper.HostStoreTableMapper
import com.ournight.api_server.domain.store.application.StoreTableService
import com.ournight.api_server.domain.store.service.StoreTableZoneService
import com.ournight.api_server.domain.user.model.User
import com.ournight.api_server.global.annotation.CheckStoreOwner
import com.ournight.api_server.global.annotation.LoginUser
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/host/stores/{storeId}")
class HostTableController(
    private val storeTableService: StoreTableService,
    private val storeTableZoneService: StoreTableZoneService,
    private val posStreamWatermarkService: PosStreamWatermarkService,
) {

    @PostMapping("/table-zones")
    @CheckStoreOwner
    fun createZone(
        @LoginUser user: User,
        @PathVariable storeId: UUID,
        @RequestBody request: StoreTableZoneCreateRequest
    ): ResponseEntity<HostStoreTableZoneResponse> {
        require(storeId == request.storeId) { "path storeId와 body storeId가 다릅니다." }

        val command = HostStoreTableMapper.toZoneCreateCommand(request)
        val view = storeTableZoneService.createZone(command)

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(HostStoreTableMapper.toZoneResponse(view))
    }

    @GetMapping("/table-zones")
    @CheckStoreOwner
    fun getStoreTableZones(
        @LoginUser user: User,
        @PathVariable storeId: UUID
    ): ResponseEntity<List<HostStoreTableZoneResponse>> {
        val watermark = posStreamWatermarkService.currentWatermark(storeId)
        val response = storeTableZoneService.getZones(storeId)
            .map(HostStoreTableMapper::toZoneResponse)
        return posStreamWatermarkService.okWithWatermark(watermark, response)
    }

    @GetMapping("/table-zones-with-tables")
    @CheckStoreOwner
    fun getStoreTableZonesWithTables(
        @LoginUser user: User,
        @PathVariable storeId: UUID
    ): ResponseEntity<List<HostStoreTableZoneWithTablesResponse>> {
        val watermark = posStreamWatermarkService.currentWatermark(storeId)
        val response = storeTableService.getStoreTableZonesWithTables(storeId)
        return posStreamWatermarkService.okWithWatermark(watermark, response)
    }

    @GetMapping("/tables")
    @CheckStoreOwner
    fun getStoreTables(
        @LoginUser user: User,
        @PathVariable storeId: UUID,
        @RequestParam(required = false) ids: List<UUID>?,
    ): ResponseEntity<List<HostStoreTableResponse>> {
        val watermark = posStreamWatermarkService.currentWatermark(storeId)
        val response = if (ids.isNullOrEmpty()) {
            storeTableService.getStoreTables(storeId)
                .map(HostStoreTableMapper::toResponse)
        } else {
            storeTableService.getStoreTablesByIds(storeId, ids)
        }

        return posStreamWatermarkService.okWithWatermark(watermark, response)
    }

    @GetMapping("/table-zones/{zoneId}/tables")
    @CheckStoreOwner
    fun getStoreTablesByZone(
        @LoginUser user: User,
        @PathVariable storeId: UUID,
        @PathVariable zoneId: UUID
    ): ResponseEntity<List<HostStoreTableResponse>> {
        val watermark = posStreamWatermarkService.currentWatermark(storeId)
        val response = storeTableService.getStoreTablesByZone(storeId, zoneId)
        return posStreamWatermarkService.okWithWatermark(watermark, response)
    }



    @PutMapping("/table-zones/{zoneId}")
    @CheckStoreOwner
    fun updateZone(
        @LoginUser user: User,
        @PathVariable storeId: UUID,
        @PathVariable zoneId: UUID,
        @RequestBody request: StoreTableZoneUpdateRequest
    ): ResponseEntity<HostStoreTableZoneResponse> {
        val command = HostStoreTableMapper.toZoneUpdateCommand(request)
        val view = storeTableZoneService.updateZone(zoneId, command)

        return ResponseEntity.ok(HostStoreTableMapper.toZoneResponse(view))
    }

    @DeleteMapping("/table-zones/{zoneId}")
    @CheckStoreOwner
    fun deleteZone(
        @LoginUser user: User,
        @PathVariable storeId: UUID,
        @PathVariable zoneId: UUID
    ): ResponseEntity<Void> {
        val deleted = storeTableZoneService.deleteZone(zoneId)
        return if (deleted) ResponseEntity.ok().build() else ResponseEntity.notFound().build()
    }

    // 편집 완료 클릭 시
    @PutMapping("/table-layout")
    @CheckStoreOwner
    fun saveTableLayout(
        @LoginUser user: User,
        @PathVariable storeId: UUID,
        @RequestBody request: SaveStoreTableLayoutRequest
    ): ResponseEntity<List<HostStoreTableDetailResponse>> {
        val command = HostStoreTableMapper.toCommand(storeId, request)
        val result = storeTableService.saveTableLayout(command)
            .map(HostStoreTableMapper::toDetailResponse)

        return ResponseEntity.ok(result)
    }
}
