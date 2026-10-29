package com.ournight.api_server.api.admin.dto

import java.util.UUID

data class AdminStoreHostResponse(
    val storeId: UUID,
    val name: String,
    val type: String,
    val isActive: Boolean,
    val hostUserId: UUID,
    val hostKakaoId: Long?,
    val hostNickname: String,
    val venue: AdminStoreVenueResponse?,
)

data class AdminStoreHostTransferRequest(
    val targetUserId: UUID,
)

data class AdminStoreVenueResponse(
    val venueId: UUID,
    val name: String,
    val address: String,
    val detailLocation: String?,
    val latitude: Double,
    val longitude: Double,
    val isPartner: Boolean,
    val universityId: UUID?,
    val universityName: String?,
)
