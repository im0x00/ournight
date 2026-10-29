package com.ournight.api_server.api.host.dto

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonSetter
import com.ournight.api_server.domain.store.domain.StoreType
import java.time.LocalDate
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

// ---------- step1 onboarding ----------
data class StoreOpenSlotDto(
	val slotId: UUID? = null,
	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Seoul")
	val startTime: LocalDateTime,

	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Seoul")
	val endTime: LocalDateTime,
)

data class StoreOnboardingCreateRequest(
	val type: StoreType,                   // RENTAL / FESTIVAL
	val name: String,
	val description: String = "",
	val hostContact: String,
	val timeSlots: List<StoreOpenSlotDto>, // 1개 이상
	val mainImageUrls: List<String>? = null,
)

data class StoreOnboardingCreateResponse(
	val storeId: UUID,
)

// ---------- my stores ----------
data class MyStoreSummaryResponse(
	val storeId: UUID,
	val name: String,
	val type: StoreType,
	val isActive: Boolean,
	val description: String,
	val timeSlots: List<StoreTimeSlotResponse>,
	val venue: VenueInfo?,
	val mainImageUrls: List<String>,
)

data class VenueInfo(
	val venueId: UUID,
	val name: String,
	val address: String,
	val detailLocation: String?,
	val latitude: Double,
	val longitude: Double,
	val university: VenueUniversityInfo? = null,
)

// ---------- read ----------
data class StoreTimeSlotResponse(
	val slotId: UUID,
	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Seoul")
	val startTime: LocalDateTime,
	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Seoul")
	val endTime: LocalDateTime,
)

data class StoreDetailResponse(
	val storeId: UUID,
	val type: StoreType,

	val name: String,
	val description: String,
	val hostContact: String,
	val mainImageUrls: List<String>? = null,
	val venueId: UUID?,
	val venue: VenueInfo?,

	val timeSlots: List<StoreTimeSlotResponse>,

	val bankName: String,
	val accountNumber: String,
	val entranceFee: BigDecimal?,
	val timeLimit: Int,
	val notices: List<String>,
)

// ---------- update ----------
data class StoreBasicUpdateRequest(
	val name: String? = null,
	val description: String? = null,
	val mainImageUrls: List<String>? = null,
	val venueId: UUID? = null,
	val timeSlots: List<StoreOpenSlotDto>? = null,
)

data class StoreOpsUpdateRequest(
	val bankName: String? = null,
	val accountNumber: String? = null,
	val entranceFee: BigDecimal? = null,
	val timeLimit: Int? = null,
	val notice: List<String>? = null,
)

data class StoreOperatingUpdateRequest(
	val venueId: UUID? = null,
	val temporaryVenue: StoreTemporaryVenueRequest? = null,
	val notice: List<String>? = null,
	val bankName: String? = null,
	val accountNumber: String? = null,
	val entranceFee: BigDecimal? = null,
	val timeLimit: Int? = null,
)

data class StoreTemporaryVenueRequest(
	val latitude: Double,
	val longitude: Double,
	val detailLocation: String? = null,
	val universityId: UUID? = null,
)

data class StoreTimeSlotsReplaceRequest(
	val timeSlots: List<StoreOpenSlotDto>
)

// ---------------------------------------------------------
data class VenueCreateRequest(
	val name: String,
	val address: String,
	val latitude: Double,
	val longitude: Double,
	val detailLocation: String? = null,
	val isPartner: Boolean = false,
	val universityId: UUID? = null,
)

class VenueUpdateRequest(
	var name: String? = null,
	var address: String? = null,
	var latitude: Double? = null,
	var longitude: Double? = null,
	var detailLocation: String? = null,
	var isPartner: Boolean? = null,
) {
	@JsonIgnore
	var isUniversityIdProvided: Boolean = false
		private set

	var universityId: UUID? = null
		@JsonSetter("universityId")
		set(value) {
			field = value
			isUniversityIdProvided = true
		}
}

data class VenueResponse(
	val id: UUID,
	val name: String,
	val address: String,
	val latitude: Double,
	val longitude: Double,
	val detailLocation: String?,
	val isPartner: Boolean,
	val university: VenueUniversityInfo?,
)

// store time slot
data class StoreTimeSlotsResponse(
	val slots: List<StoreTimeSlotItemResponse>
)

data class StoreTimeSlotItemResponse(
	val timeSlotId: UUID,
	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Seoul")
	val startTime: LocalDateTime,
	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Seoul")
	val endTime: LocalDateTime,
	val startDate: LocalDate
)
