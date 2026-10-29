package com.ournight.api_server.api.guest.dto

import java.util.UUID

data class PosExperienceResponse(
    val accessToken: String,
    val refreshToken: String,
    val userId: UUID,
    val nickname: String,
    val storeId: UUID,
    val tableId: UUID,
    val customerAccessToken: String,
    val customerUserId: UUID,
    val customerNickname: String,
    val expiresInMinutes: Long,
)
