package com.ournight.api_server.domain.store.view

import com.ournight.api_server.domain.store.domain.StoreType
import com.ournight.api_server.api.host.dto.StoreTemporaryVenueRequest
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

data class StoreTimeSlotCommand(
    /** 기존 슬롯 수정 시 조회 응답의 slotId. 없으면 신규 추가 */
    val slotId: UUID? = null,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
)

data class StoreOnboardingCreateCommand(
    val type: StoreType,
    val name: String,
    val description: String,
    val hostContact: String,
    val timeSlots: List<StoreTimeSlotCommand>,
    val mainImageUrls: List<String>?,
)


data class StoreBasicUpdateCommand(
    val name: String?,
    val description: String?,
    val mainImageUrls: List<String>?,
    val venueId: UUID?,
    val timeSlots: List<StoreTimeSlotCommand>?,
)

data class StoreOpsUpdateCommand(
    val bankName: String?,
    val accountNumber: String?,
    val entranceFee: BigDecimal?,
    val timeLimit: Int?,
    val notice: List<String>?,
)

data class StoreOperatingUpdateCommand(
    val venueId: UUID?,
    val temporaryVenue: StoreTemporaryVenueRequest?,
    val notice: List<String>?,
    val bankName: String?,
    val accountNumber: String?,
    val entranceFee: BigDecimal?,
    val timeLimit: Int?,
)
