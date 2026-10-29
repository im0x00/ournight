package com.ournight.api_server.api.admin.dto

import com.ournight.api_server.domain.pass.model.PassStatus
import com.ournight.api_server.domain.pass.view.AdminPassView
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime
import java.util.UUID

data class AdminPassResponse(
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

data class AdminPassStatusUpdateRequest(
    @field:NotNull
    val status: PassStatus,
)

fun AdminPassView.toAdminPassResponse() = AdminPassResponse(
    passId = passId,
    userId = userId,
    kakaoId = kakaoId,
    nickname = nickname,
    storeId = storeId,
    storeName = storeName,
    slotId = slotId,
    slotStartTime = slotStartTime,
    slotEndTime = slotEndTime,
    status = status,
    effectiveStatus = effectiveStatus,
    orderable = orderable,
    expired = expired,
    activatedAt = activatedAt,
    expiredAt = expiredAt,
)
