package com.ournight.api_server.api.guest.controller

import com.ournight.api_server.api.guest.dto.PosExperienceResponse
import com.ournight.api_server.domain.guest.service.PosExperienceService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@CrossOrigin(origins = ["https://im0x00.com", "https://www.im0x00.com"])
@RequestMapping("/api/guest/pos-experience")
class PosExperienceController(
    private val posExperienceService: PosExperienceService,
) {
    @PostMapping
    fun create(): ResponseEntity<PosExperienceResponse> =
        ResponseEntity.ok(posExperienceService.create())
}
