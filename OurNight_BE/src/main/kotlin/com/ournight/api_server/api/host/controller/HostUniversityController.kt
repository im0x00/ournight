package com.ournight.api_server.api.host.controller

import com.ournight.api_server.api.host.dto.UniversityCreateRequest
import com.ournight.api_server.api.host.dto.UniversityResponse
import com.ournight.api_server.api.host.dto.UniversityUpdateRequest
import com.ournight.api_server.domain.university.model.University
import com.ournight.api_server.domain.university.service.UniversityService
import com.ournight.api_server.domain.user.model.User
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
@RequestMapping("/api/host/universities")
class HostUniversityController(
    private val universityService: UniversityService
) {
    @PostMapping
    fun createUniversity(
        @LoginUser user: User,
        @RequestBody request: UniversityCreateRequest
    ): ResponseEntity<UniversityResponse> {
        val created = universityService.createUniversity(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(created.toResponse())
    }

    @GetMapping
    fun getUniversities(
        @LoginUser user: User
    ): ResponseEntity<List<UniversityResponse>> {
        val response = universityService.getAllUniversities().map { it.toResponse() }
        return ResponseEntity.ok(response)
    }

    @GetMapping("/{universityId}")
    fun getUniversity(
        @LoginUser user: User,
        @PathVariable universityId: UUID
    ): ResponseEntity<UniversityResponse> {
        val university = universityService.getUniversity(universityId)
        return ResponseEntity.ok(university.toResponse())
    }

    @PutMapping("/{universityId}")
    fun updateUniversity(
        @LoginUser user: User,
        @PathVariable universityId: UUID,
        @RequestBody request: UniversityUpdateRequest
    ): ResponseEntity<UniversityResponse> {
        val updated = universityService.updateUniversity(universityId, request)
        return ResponseEntity.ok(updated.toResponse())
    }

    @DeleteMapping("/{universityId}")
    fun deleteUniversity(
        @LoginUser user: User,
        @PathVariable universityId: UUID
    ): ResponseEntity<Void> {
        universityService.deleteUniversity(universityId)
        return ResponseEntity.noContent().build()
    }

    private fun University.toResponse(): UniversityResponse {
        return UniversityResponse(
            id = id ?: error("university id is null"),
            name = name,
            latitude = latitude,
            longitude = longitude
        )
    }
}
