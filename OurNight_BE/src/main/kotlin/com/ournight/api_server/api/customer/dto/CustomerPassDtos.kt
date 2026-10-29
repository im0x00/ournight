package com.ournight.api_server.api.customer.dto

import com.ournight.api_server.domain.pass.view.PassStatusView
import com.ournight.api_server.domain.pass.view.PassView
import com.ournight.api_server.domain.pass.view.MyPassView
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDateTime
import java.util.UUID

data class ActivatePassRequest(
    @field:NotBlank(message = "인증 코드는 필수임")
    @field:Size(min = 4, max = 4, message = "인증 코드는 4자리여야 함")
    val code: String
)

/** GET .../pass — 현재 유저의 해당 슬롯 패스 상태 */
data class CustomerPassStatusResponse(
    val slotId: UUID,
    val hasPass: Boolean,
    val status: String?,
    val orderable: Boolean,
    val passId: UUID?,
    val activatedAt: LocalDateTime?,
    val expiredAt: LocalDateTime?,
)

fun PassStatusView.toStatusResponse() = CustomerPassStatusResponse(
    slotId = slotId,
    hasPass = hasPass,
    status = status,
    orderable = orderable,
    passId = passId,
    activatedAt = activatedAt,
    expiredAt = expiresAt,
)

data class CustomerMyPassResponse(
    val passId: UUID,
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

fun MyPassView.toMyPassResponse() = CustomerMyPassResponse(
    passId = passId,
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

// 응답 DTO
data class CustomerPassResponse(
    val passId: UUID,
    val slotId: UUID,
    val status: String,
    val activatedAt: LocalDateTime?,
    val expiredAt: LocalDateTime?
)

// Mapper
fun PassView.toResponse() = CustomerPassResponse(
    passId = this.id,
    slotId = this.slotId,
    status = this.status,
    activatedAt = this.activatedAt,
    expiredAt = this.expiredAt
)
