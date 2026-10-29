package com.ournight.api_server.global.common.version.controller

import com.ournight.api_server.global.common.version.dto.AppVersionResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class AppVersionController(
    @Value("\${build.version:unknown}") private val version: String,
    @Value("\${build.commit:}") private val commit: String,
    @Value("\${build.time:}") private val builtAt: String,
) {
    @GetMapping("/version")
    fun version(): ResponseEntity<AppVersionResponse> =
        ResponseEntity.ok(
            AppVersionResponse(
                service = "backend",
                version = version,
                commit = commit.ifBlank { null },
                builtAt = builtAt.ifBlank { null },
            )
        )
}
