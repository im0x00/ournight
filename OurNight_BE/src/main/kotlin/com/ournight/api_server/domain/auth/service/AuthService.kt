package com.ournight.api_server.domain.auth.service

import com.ournight.api_server.global.jwt.JwtTokenProvider
import com.ournight.api_server.domain.auth.dto.AuthDtos
import com.ournight.api_server.domain.auth.model.RefreshToken
import com.ournight.api_server.domain.auth.repository.RefreshTokenRepository
import com.ournight.api_server.domain.user.model.User
import com.ournight.api_server.domain.user.repository.UserRepository
import com.ournight.api_server.global.error.ErrorCode
import com.ournight.api_server.global.error.exception.BusinessException
import com.ournight.api_server.infra.kakao.KakaoApiClient
import com.ournight.api_server.infra.kakao.KakaoAuthClient
import io.jsonwebtoken.ExpiredJwtException
import jakarta.transaction.Transactional
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class AuthService (
    private val kakaoAuthClient: KakaoAuthClient,
    private val kakaoApiClient: KakaoApiClient,
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val jwtTokenProvider: JwtTokenProvider,

    @Value("\${kakao.client-id}") private val clientId: String,
    @Value("\${kakao.redirect-uri}") private val redirectUri: String,
    @Value("\${kakao.client-secret}") private val clientSecret: String
) {
    @Transactional
    fun login(authCode: String): AuthDtos.LoginResponse {
        val kakaoToken = kakaoAuthClient.getAccessToken(
            clientId = clientId,
            redirectUri = redirectUri,
            code = authCode,
            clientSecret = clientSecret
        )

        val kakaoUser = kakaoApiClient.getUserInfo("Bearer ${kakaoToken.accessToken}")
        val nickname = kakaoUser.properties?.nickname
            ?: kakaoUser.kakaoAccount?.profile?.nickname
            ?: "카카오사용자"
        val user = userRepository.findByKakaoId(kakaoUser.id) ?: userRepository.save(
            User(
                kakaoId = kakaoUser.id,
                nickname = nickname
            )
        )

        val userId = user.id!!
        val accessToken = jwtTokenProvider.createAccessToken(userId, user.role, user.authType)
        val refreshToken = jwtTokenProvider.createRefreshToken(userId)

        refreshTokenRepository.save(RefreshToken.from(userId, refreshToken))

        return AuthDtos.LoginResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
            userId = userId,
            nickname = user.nickname
        )
    }

    @Transactional
    fun reissue(refreshToken: String): AuthDtos.TokenResponse {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw BusinessException(ErrorCode.UNAUTHORIZED, message = "Invalid refresh token")
        }
        val userId: UUID = jwtTokenProvider.getUserIdFromToken(refreshToken)

        val savedToken = findSavedRefreshToken(userId, refreshToken)
        if (savedToken.userId != userId.toString() || savedToken.token != refreshToken) {
            throw BusinessException(ErrorCode.UNAUTHORIZED, message = "Token mismatch")
        }
        val user = userRepository.findById(userId).orElseThrow {
            BusinessException(ErrorCode.USER_NOT_FOUND)
        }

        val newAccessToken = jwtTokenProvider.createAccessToken(user.id!!, user.role, user.authType)

        // 동일 계정 다중 POS 사용을 위해 refresh token은 사용자당 1개가 아니라
        // 로그인 세션별로 유지한다. reissue에서는 access token만 갱신한다.
        refreshTokenRepository.save(RefreshToken.from(userId, refreshToken))

        return AuthDtos.TokenResponse(
            accessToken = newAccessToken,
            refreshToken = refreshToken
        )
    }

    @Transactional
    fun kakaoLogout(bearerToken: String, requestRefreshToken: String) {
        val accessToken = jwtTokenProvider.resolveToken(bearerToken)

        val userId = try {
            jwtTokenProvider.getUserIdFromToken(accessToken)
        } catch (e: ExpiredJwtException) {
            UUID.fromString(e.claims.subject)
        } catch (e: Exception) {
            throw IllegalArgumentException("유효하지 않은 Access Token입니다.")
        }

        val tokenHash = RefreshToken.hash(requestRefreshToken)
        val savedToken = refreshTokenRepository.findById(tokenHash).orElse(null)
            ?: refreshTokenRepository.findById(userId.toString()).orElse(null)

        if (savedToken != null && savedToken.userId == userId.toString() && savedToken.token == requestRefreshToken) {
            refreshTokenRepository.deleteById(savedToken.tokenHash)
            refreshTokenRepository.deleteById(userId.toString())
        } else {
            println("WARN: 로그아웃 요청 토큰 불일치 또는 이미 삭제됨 - User: $userId")
        }
    }

    private fun findSavedRefreshToken(userId: UUID, refreshToken: String): RefreshToken {
        val tokenHash = RefreshToken.hash(refreshToken)
        val tokenRecord = refreshTokenRepository.findById(tokenHash).orElse(null)
        if (tokenRecord != null) {
            return tokenRecord
        }

        // 기존 배포본은 Redis key를 userId로 저장했다. 한 번은 호환 조회한 뒤
        // 새 tokenHash key로 옮겨, 배포 직후 전체 POS 재로그인을 피한다.
        val legacyTokenRecord = refreshTokenRepository.findById(userId.toString()).orElse(null)
        if (legacyTokenRecord != null && legacyTokenRecord.token == refreshToken) {
            refreshTokenRepository.deleteById(userId.toString())
            return refreshTokenRepository.save(RefreshToken.from(userId, refreshToken))
        }

        throw BusinessException(ErrorCode.UNAUTHORIZED, message = "Refresh token not found")
    }
}
