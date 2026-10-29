package com.ournight.api_server.api.admin.dto

import java.util.UUID

data class AdminUserResponse(
    val userId: UUID,
    val kakaoId: Long?,
    val nickname: String,
    val role: String,
)
