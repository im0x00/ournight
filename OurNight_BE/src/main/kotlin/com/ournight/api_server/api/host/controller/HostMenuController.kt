package com.ournight.api_server.api.host.controller

import com.ournight.api_server.api.customer.dto.CustomerStoreMenuCategoryResponse
import com.ournight.api_server.api.host.dto.StoreMenuCategoryCreateRequest
import com.ournight.api_server.api.host.dto.StoreMenuCategoryMoveAndDeleteRequest
import com.ournight.api_server.api.host.dto.StoreMenuCategoryResponse
import com.ournight.api_server.api.host.dto.StoreMenuCategoryUpdateRequest
import com.ournight.api_server.domain.store.application.StoreMenuService
import com.ournight.api_server.api.host.dto.StoreMenuCreateRequest
import com.ournight.api_server.api.host.dto.StoreMenuResponse
import com.ournight.api_server.api.host.dto.StoreMenuUpdateRequest
import com.ournight.api_server.domain.user.model.User
import com.ournight.api_server.global.annotation.LoginUser
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import jakarta.validation.Valid
import java.util.UUID

@RestController
@RequestMapping("/api/host/stores/{storeId}")
class HostMenuController(
    private val storeMenuService: StoreMenuService
) {

    @PostMapping("/menu-categories")
    fun createCategory(
        @LoginUser user: User,
        @PathVariable storeId: UUID,
        @RequestBody request: StoreMenuCategoryCreateRequest
    ): ResponseEntity<StoreMenuCategoryResponse> {
        val c = storeMenuService.createCategory(storeId, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(
            StoreMenuCategoryResponse(c.id, c.name, c.sequence)
        )
    }

    @PutMapping("/menu-categories/{categoryId}")
    fun updateCategory(
        @LoginUser user: User,
        @PathVariable storeId: UUID,
        @PathVariable categoryId: UUID,
        @RequestBody request: StoreMenuCategoryUpdateRequest
    ): ResponseEntity<StoreMenuCategoryResponse> {
        val c = storeMenuService.updateCategory(storeId, categoryId, request)
        return ResponseEntity.ok(StoreMenuCategoryResponse(c.id, c.name, c.sequence))
    }

    @DeleteMapping("/menu-categories/{categoryId}")
    fun deleteCategory(
        @LoginUser user: User,
        @PathVariable storeId: UUID,
        @PathVariable categoryId: UUID
    ): ResponseEntity<Void> {
        storeMenuService.deleteCategory(storeId, categoryId)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/menu-categories/{categoryId}/move-and-delete")
    fun moveMenusAndDeleteCategory(
        @LoginUser user: User,
        @PathVariable storeId: UUID,
        @PathVariable categoryId: UUID,
        @RequestBody request: StoreMenuCategoryMoveAndDeleteRequest
    ): ResponseEntity<Void> {
        storeMenuService.moveMenusAndDeleteCategory(storeId, categoryId, request.targetCategoryId)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/menu-categories")
    fun listCategories(
        @LoginUser user: User,
        @PathVariable storeId: UUID
    ): ResponseEntity<List<StoreMenuCategoryResponse>> {
        val res = storeMenuService.listCategories(storeId)
            .map { StoreMenuCategoryResponse(it.id, it.name, it.sequence) }
        return ResponseEntity.ok(res)
    }

    @PostMapping("/menus")
    fun createMenu(
        @LoginUser user: User,
        @PathVariable storeId: UUID,
        @Valid @RequestBody request: StoreMenuCreateRequest
    ): ResponseEntity<StoreMenuResponse> {
        val menu = storeMenuService.createMenu(storeId, request)

        return ResponseEntity.status(HttpStatus.CREATED).body(
            StoreMenuResponse(
                id = menu.id!!,
                categoryId = menu.category.id,
                name = menu.name,
                salePrice = menu.salePrice,
                imageUrl = menu.imageUrl,
                status = menu.status,
                badges = menu.badges.toList(),
            )
        )
    }

    @PutMapping("/menus/{menuId}")
    fun updateMenu(
        @LoginUser user: User,
        @PathVariable storeId: UUID,
        @PathVariable menuId: UUID,
        @Valid @RequestBody request: StoreMenuUpdateRequest
    ): ResponseEntity<StoreMenuResponse> {
        val menu = storeMenuService.updateMenu(menuId, request)

        return ResponseEntity.ok(
            StoreMenuResponse(
                id = menu.id!!,
                categoryId = menu.category.id,
                name = menu.name,
                salePrice = menu.salePrice,
                imageUrl = menu.imageUrl,
                status = menu.status,
                badges = menu.badges.toList()
            )
        )
    }

    @DeleteMapping("/menus/{menuId}")
    fun deleteMenu(
        @LoginUser user: User,
        @PathVariable storeId: UUID,
        @PathVariable menuId: UUID
    ): ResponseEntity<Void> {
        return if (storeMenuService.deleteMenu(storeId, menuId)) {
            ResponseEntity.noContent().build()
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @GetMapping("/menus")
    fun getMenusByStoreId(
        @LoginUser user: User,
        @PathVariable storeId: UUID
    ): ResponseEntity<List<CustomerStoreMenuCategoryResponse>> {
        val res = storeMenuService.getMenuAndCategoriesByStoreId(storeId)
        return ResponseEntity.ok()
            .body(res)
    }

    @GetMapping("/menus/{menuId}")
    fun getMenuById(
        @LoginUser user: User,
        @PathVariable storeId: UUID,
        @PathVariable menuId: UUID
    ): ResponseEntity<StoreMenuResponse> {
        val menu = storeMenuService.getMenuByStoreIdAndId(storeId, menuId)
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(
            StoreMenuResponse(
                id = menu.id!!,
                categoryId = menu.category.id,
                name = menu.name,
                salePrice = menu.salePrice,
                imageUrl = menu.imageUrl,
                description = menu.description,
                requiresCooking = menu.requiresCooking,
                status = menu.status,
                badges = menu.badges.toList()
            )
        )
    }
}
