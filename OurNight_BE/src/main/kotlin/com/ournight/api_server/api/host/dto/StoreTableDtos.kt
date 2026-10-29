package com.ournight.api_server.api.host.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

data class SaveStoreTableLayoutRequest(
	val tables: List<StoreTableLayoutItemRequest>
)

data class StoreTableLayoutItemRequest(
	val id: UUID?,
	val zoneId: UUID,
	val tableNumber: Int,
	val xPosition: Double,
	val yPosition: Double,
	val capacity: Int,
	val qrImageUrl: String? = null,
)

data class HostStoreTableResponse(
	val id: UUID,
	val tableNumber: Int,
	val capacity: Int,
	val status: String,
	val qrLink: String?,
	val qrImageUrl: String?,
	val zoneId: UUID?,
	val zoneName: String?,
	@get:JsonProperty("xPosition")
	val xPosition: Double,
	@get:JsonProperty("yPosition")
	val yPosition: Double,
	val activeSession: HostActiveTableSessionSummaryResponse? = null,
)

data class HostActiveTableSessionSummaryResponse(
	val sessionId: UUID,
	val sessionStatus: String,
	val timeOver: Boolean,
	val enteredAt: LocalDateTime?,
	val servedOrderCount: Int,
	val confirmedOrderCount: Int,
	val remainingMinutes: Long?,
	val totalAmount: BigDecimal
)


data class HostStoreTableDetailResponse(
	val id: UUID,
	val tableNumber: Int,
	@get:JsonProperty("xPosition")
	val xPosition: Double,
	@get:JsonProperty("yPosition")
	val yPosition: Double,
	val capacity: Int,
	val qrLink: String?,
	val qrImageUrl: String?,
	val status: String,
	val zoneId: UUID?,
	val zoneName: String?,
)

data class StoreTableZoneCreateRequest(
	val storeId: UUID,
	val name: String,
	val displayOrder: Int = 0,
)

data class StoreTableZoneUpdateRequest(
	val name: String,
	val displayOrder: Int,
	val isActive: Boolean,
)

data class HostStoreTableZoneResponse(
	val id: UUID,
	val name: String,
	val displayOrder: Int,
	val isActive: Boolean,
)

data class HostStoreTableZoneWithTablesResponse(
	val zoneId: UUID?,
	val zoneName: String,
	val displayOrder: Int,
	val tables: List<HostStoreTableResponse>,
)
