package com.ournight.api_server.domain.pass.view

import java.time.LocalDateTime
import java.util.UUID

data class PassView(
    val id: UUID,
    val slotId: UUID,
    val status: String,
    val activatedAt: LocalDateTime?,
    val expiredAt: LocalDateTime?
)
