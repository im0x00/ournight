package com.ournight.api_server.domain.store.view

import com.ournight.api_server.domain.store.domain.StoreType
import com.ournight.api_server.api.host.dto.VenueUniversityInfo
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

data class StoreOnboardingCreateResult(
    val storeId: UUID,
)

data class VenueInfoResult(
    val venueId: UUID,
    val name: String,
    val address: String,
    val detailLocation: String?,
    val latitude: Double,
    val longitude: Double,
    val university: VenueUniversityInfo?,
)

data class StoreDetailResult(
    val storeId: UUID,
    val type: StoreType,

    val name: String,
    val description: String,
    val hostContact: String,
    val mainImageUrls: List<String>,
    val venueId: UUID?,
    val venue: VenueInfoResult?,

    val timeSlots: List<StoreTimeSlotResult>,

    val bankName: String,
    val accountNumber: String,
    val entranceFee: BigDecimal?,
    val timeLimit: Int,
    val notices: List<String>,
)

data class StoreTimeSlotResult(
    val slotId: UUID,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
)
