package com.ournight.api_server.domain.auth.dto

import java.util.UUID

class AuthDtos {
    data class KakaoLoginRequest(
        val code: String
    )

    data class LoginResponse(
        val accessToken: String,
        val refreshToken: String,
        val userId: UUID? = null,
        val nickname: String? = null
    )


    data class TokenResponse( // 재 발급용
        val accessToken: String,
        val refreshToken: String
    )

    data class ReissueRequest( // 토큰 재발급 요청
        val refreshToken: String
    )

    data class LogoutRequest( // 로그아웃 요청
        val refreshToken: String
    )
}