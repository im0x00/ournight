package com.ournight.api_server.api.guest.dto

import com.ournight.api_server.api.customer.dto.CustomerPassResponse
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.util.UUID

data class GuestPassActivateRequest(
    @field:NotNull(message = "운영 시간대는 필수입니다.")
    val slotId: UUID,

    @field:NotBlank(message = "인증 코드는 필수임")
    @field:Size(min = 4, max = 4, message = "인증 코드는 4자리여야 함")
    val code: String,
)

data class GuestPassActivateResponse(
    val accessToken: String,
    val userId: UUID,
    val nickname: String,
    val pass: CustomerPassResponse,
)
