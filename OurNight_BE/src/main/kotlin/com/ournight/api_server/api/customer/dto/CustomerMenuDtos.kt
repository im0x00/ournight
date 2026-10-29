package com.ournight.api_server.api.customer.dto

import com.ournight.api_server.domain.store.domain.MenuBadge
import com.ournight.api_server.domain.store.domain.StoreType
import java.math.BigDecimal
import java.util.UUID

// 1. QR 스캔 후 입장 시 응답 (메뉴 제외)
data class CustomerTableResponse(
    val id: UUID,
    val tableNumber: Int,
    val store: CustomerStoreInfo
)

// 2. 입장 시 포함될 가벼운 가게 정보
data class CustomerStoreInfo(
    val id: UUID? = null,
    val name: String,
    val description: String?,
    val category: StoreType,
    val bankName: String?,
    val accountNumber: String?
)

// 메뉴
data class CustomerStoreMenuCategoryResponse(
    val categoryId: UUID,
    val categoryName: String,
    val items: List<CustomerMenuResponse>
)

data class CustomerMenuResponse(
    val menuId: UUID,
    val name: String,
    val price: BigDecimal,
    val imageUrl: String?,
    val description: String?,
    val isRecommended: Boolean,
    val isUnlimited: Boolean,
    val status: String,
    val badges: List<MenuBadge> = emptyList()
)
