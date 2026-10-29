package com.ournight.api_server.domain.pass.view

import java.time.LocalDateTime
import java.util.UUID

data class AdminPassView(
    val passId: UUID,
    val userId: UUID,
    val kakaoId: Long?,
    val nickname: String,
    val storeId: UUID,
    val storeName: String,
    val slotId: UUID,
    val slotStartTime: LocalDateTime,
    val slotEndTime: LocalDateTime,
    val status: String,
    val effectiveStatus: String,
    val orderable: Boolean,
    val expired: Boolean,
    val activatedAt: LocalDateTime?,
    val expiredAt: LocalDateTime?,
)
