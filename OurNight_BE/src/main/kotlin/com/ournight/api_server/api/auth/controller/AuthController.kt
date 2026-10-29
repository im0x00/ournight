package com.ournight.api_server.api.auth.controller

import com.ournight.api_server.domain.auth.dto.AuthDtos
import com.ournight.api_server.domain.auth.service.AuthService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController (
    private val authService: AuthService,
) {
    @PostMapping("/kakao")
    fun kakaoLogin(@RequestBody request: AuthDtos.KakaoLoginRequest): ResponseEntity<AuthDtos.LoginResponse> {
        val response = authService.login(request.code)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/reissue")
    fun reissue(@RequestBody request: AuthDtos.ReissueRequest): ResponseEntity<AuthDtos.TokenResponse> {
        val response = authService.reissue(request.refreshToken)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/logout")
    fun kakaoLogout(
        @RequestHeader("Authorization") bearerToken: String,
        @RequestBody request: AuthDtos.LogoutRequest
    ): ResponseEntity<Unit> {
        authService.kakaoLogout(bearerToken, request.refreshToken)
        return ResponseEntity.noContent().build()
    }
}