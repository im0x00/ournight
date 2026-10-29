package com.ournight.api_server.api.host.dto

import java.util.UUID

data class UniversityCreateRequest(
    val name: String,
    val latitude: Double,
    val longitude: Double,
)

data class UniversityUpdateRequest(
    val name: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

data class UniversityResponse(
    val id: UUID,
    val name: String,
    val latitude: Double,
    val longitude: Double,
)

data class VenueUniversityInfo(
    val id: UUID,
    val name: String,
    val latitude: Double,
    val longitude: Double,
)
