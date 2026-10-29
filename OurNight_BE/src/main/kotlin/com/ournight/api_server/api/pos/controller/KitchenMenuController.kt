package com.ournight.api_server.api.pos.controller

import com.ournight.api_server.api.pos.dto.KitchenMenuDetailResponse
import com.ournight.api_server.api.pos.dto.KitchenMenuPageResponse
import com.ournight.api_server.api.pos.dto.UpdateKitchenMenuRequest
import com.ournight.api_server.domain.store.application.StoreMenuService
import com.ournight.api_server.domain.user.model.User
import com.ournight.api_server.global.annotation.LoginUser
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/pos/{storeId}/kitchen/menus")
class KitchenMenuController(
    private val storeMenuService: StoreMenuService
) {
    @GetMapping
    fun getKitchenMenus(
        @PathVariable storeId: UUID,
        @LoginUser user: User,
    ): ResponseEntity<KitchenMenuPageResponse> {
        val response = storeMenuService.getKitchenMenus(storeId, user)
        return ResponseEntity.ok(response)
    }

    @PatchMapping("/{menuId}")
    fun updateKitchenMenu(
        @PathVariable storeId: UUID,
        @PathVariable menuId: UUID,
        @LoginUser user: User,
        @RequestBody request: UpdateKitchenMenuRequest,
    ): ResponseEntity<KitchenMenuDetailResponse> {
        val response = storeMenuService.updateKitchenMenu(storeId, menuId, user, request)
        return ResponseEntity.ok(response)
    }
}