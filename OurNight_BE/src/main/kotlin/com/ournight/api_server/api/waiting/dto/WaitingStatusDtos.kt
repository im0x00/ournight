package com.ournight.api_server.api.waiting.dto

import java.time.LocalDateTime
import java.util.UUID

data class WaitingStatusResponse(
    val id: UUID,
    val name: String,
    val count: Int,
    val updatedAt: LocalDateTime?,
)

data class WaitingStatusUpdateRequest(
    val count: Int,
)
