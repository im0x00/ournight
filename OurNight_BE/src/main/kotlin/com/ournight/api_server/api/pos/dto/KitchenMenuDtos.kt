package com.ournight.api_server.api.pos.dto

import com.ournight.api_server.domain.store.domain.StoreMenu
import com.ournight.api_server.domain.store.domain.StoreMenuStatus
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class KitchenMenuPageResponse(
    val categories: List<KitchenMenuCategoryResponse>,
    val menus: List<KitchenMenuSummaryResponse>,
)

data class KitchenMenuCategoryResponse(
    val categoryId: UUID,
    val categoryName: String,
    val sequence: Int,
)

data class KitchenMenuSummaryResponse(
    val menuId: UUID,
    val categoryId: UUID,
    val name: String,
    val salePrice: BigDecimal,
    val imageUrl: String?,
    val status: KitchenMenuAvailabilityResponse,
    val inventory: KitchenMenuInventoryResponse,
    val updatedAt: Instant,
)

data class KitchenMenuAvailabilityResponse(
    val value: StoreMenuStatus,
)

data class KitchenMenuInventoryResponse(
    val enabled: Boolean,
    val quantity: Int? = null,
)

data class KitchenMenuDetailResponse(
    val menuId: UUID,
    val categoryId: UUID,
    val name: String,
    val salePrice: BigDecimal,
    val imageUrl: String?,
    val status: KitchenMenuAvailabilityResponse,
    val inventory: KitchenMenuInventoryResponse,
    val updatedAt: Instant,
)

data class UpdateKitchenMenuRequest(
    val status: StoreMenuStatus? = null,
    val inventory: UpdateKitchenMenuInventoryRequest? = null,
)

data class UpdateKitchenMenuInventoryRequest(
    val enabled: Boolean? = null,
)

fun StoreMenu.toKitchenMenuSummaryResponse(): KitchenMenuSummaryResponse {
    return KitchenMenuSummaryResponse(
        menuId = this.id ?: throw IllegalStateException("menu id is null"),
        categoryId = this.category.id,
        name = this.name,
        salePrice = this.salePrice,
        imageUrl = this.imageUrl,
        status = KitchenMenuAvailabilityResponse(
            value = this.status
        ),
        inventory = KitchenMenuInventoryResponse(
            enabled = !this.isUnlimited,
            quantity = null
        ),
        updatedAt = this.updatedAt
    )
}

fun StoreMenu.toKitchenMenuDetailResponse(): KitchenMenuDetailResponse {
    return KitchenMenuDetailResponse(
        menuId = this.id ?: throw IllegalStateException("menu id is null"),
        categoryId = this.category.id,
        name = this.name,
        salePrice = this.salePrice,
        imageUrl = this.imageUrl,
        status = KitchenMenuAvailabilityResponse(
            value = this.status
        ),
        inventory = KitchenMenuInventoryResponse(
            enabled = !this.isUnlimited,
            quantity = null
        ),
        updatedAt = this.updatedAt
    )
}