package com.ournight.api_server.api.host.controller

import com.ournight.api_server.api.host.dto.VenueCreateRequest
import com.ournight.api_server.api.host.dto.VenueResponse
import com.ournight.api_server.api.host.dto.VenueUniversityInfo
import com.ournight.api_server.api.host.dto.VenueUpdateRequest
import com.ournight.api_server.domain.user.model.User
import com.ournight.api_server.domain.venue.model.Venue
import com.ournight.api_server.domain.venue.service.VenueService
import com.ournight.api_server.global.annotation.LoginUser
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/host/venues")
class HostVenueController(
    private val venueService: VenueService
) {
    @PostMapping
    fun createVenue(
        @LoginUser user: User,
        @RequestBody request: VenueCreateRequest
    ): ResponseEntity<VenueResponse> {
        val created = venueService.createVenue(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(created.toResponse())
    }

    @GetMapping
    fun getVenues(
        @LoginUser user: User
    ): ResponseEntity<List<VenueResponse>> {
        val response = venueService.getAllVenues().map { it.toResponse() }
        return ResponseEntity.ok(response)
    }

    @GetMapping("/{venueId}")
    fun getVenue(
        @LoginUser user: User,
        @PathVariable venueId: UUID
    ): ResponseEntity<VenueResponse> {
        val venue = venueService.getVenue(venueId)
        return ResponseEntity.ok(venue.toResponse())
    }

    @PutMapping("/{venueId}")
    fun updateVenue(
        @LoginUser user: User,
        @PathVariable venueId: UUID,
        @RequestBody request: VenueUpdateRequest
    ): ResponseEntity<VenueResponse> {
        val updated = venueService.updateVenue(venueId, request)
        return ResponseEntity.ok(updated.toResponse())
    }

    @DeleteMapping("/{venueId}")
    fun deleteVenue(
        @LoginUser user: User,
        @PathVariable venueId: UUID
    ): ResponseEntity<Void> {
        venueService.deleteVenue(venueId)
        return ResponseEntity.noContent().build()
    }

    private fun Venue.toResponse(): VenueResponse {
        return VenueResponse(
            id = id ?: error("venue id is null"),
            name = name,
            address = address,
            latitude = latitude,
            longitude = longitude,
            detailLocation = detailLocation,
            isPartner = isPartner,
            university = university?.let {
                VenueUniversityInfo(
                    id = it.id ?: error("university id is null"),
                    name = it.name,
                    latitude = it.latitude,
                    longitude = it.longitude
                )
            },
        )
    }
}
