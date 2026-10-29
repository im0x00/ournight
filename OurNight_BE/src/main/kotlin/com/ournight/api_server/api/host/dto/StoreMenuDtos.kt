package com.ournight.api_server.api.host.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.ournight.api_server.domain.store.domain.MenuBadge
import com.ournight.api_server.domain.store.domain.StoreMenuStatus
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.util.UUID

data class StoreMenuCreateRequest(
	@field:NotNull(message = "categoryId는 필수입니다")
	@JsonProperty(required = true)
	val categoryId: UUID,
	
	@field:NotNull(message = "name은 필수입니다")
	@JsonProperty(required = true)
	val name: String,
	
	@field:NotNull(message = "salePrice는 필수입니다")
	@field:DecimalMin(value = "1", message = "salePrice는 1원 이상이어야 합니다")
	@JsonProperty(required = true)
	val salePrice: BigDecimal,
	
	val description: String? = null,
	val imageUrl: String? = null,
	val sequence: Int = 0,
	val isRecommended: Boolean = false,
	val isUnlimited: Boolean = false,
	val status: StoreMenuStatus = StoreMenuStatus.ON_SALE,
	val requiresCooking: Boolean = true,
	val badges: List<MenuBadge> = emptyList(),
)

data class StoreMenuUpdateRequest(
	val categoryId: UUID?,
	val name: String,
	@field:DecimalMin(value = "1", message = "salePrice는 1원 이상이어야 합니다")
	val salePrice: BigDecimal,
	val description: String? = null,
	val imageUrl: String? = null,
	val sequence: Int = 0,
	val isRecommended: Boolean = false,
	val isUnlimited: Boolean = false,
	val status: StoreMenuStatus,
	val badges: List<MenuBadge>? = null,
)

data class StoreMenuResponse(
	val id: UUID,
	val categoryId: UUID,
	val name: String,
	val salePrice: BigDecimal,
	val imageUrl: String?,
	val description: String? = null,
	val requiresCooking: Boolean = true,
	val status: StoreMenuStatus,
	val badges: List<MenuBadge> = emptyList()
)

data class StoreMenuCategoryCreateRequest(
	val name: String,
	val sequence: Int
)

data class StoreMenuCategoryUpdateRequest(
	val name: String,
	val sequence: Int
)

data class StoreMenuCategoryMoveAndDeleteRequest(
	val targetCategoryId: UUID
)

data class StoreMenuCategoryResponse(
	val id: UUID,
	val name: String,
	val sequence: Int
)
