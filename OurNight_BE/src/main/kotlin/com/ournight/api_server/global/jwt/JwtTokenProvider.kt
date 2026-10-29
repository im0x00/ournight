package com.ournight.api_server.global.jwt

import com.ournight.api_server.domain.user.model.UserAuthType
import com.ournight.api_server.domain.user.model.UserRole
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import jakarta.servlet.http.HttpServletRequest
import org.apache.tomcat.util.net.openssl.ciphers.Authentication
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.stereotype.Component
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

@Component
class JwtTokenProvider(
    @Value("\${jwt.secret}") private val secretKeyRaw: String,
    @Value("\${jwt.access-token-validity}") private val accessTokenValidity: Long,
    @Value("\${jwt.refresh-token-validity}") private val refreshTokenValidity: Long
) {
    private val key: SecretKey = Keys.hmacShaKeyFor(secretKeyRaw.toByteArray())

    // access token 생성

    fun createAccessToken(
        userId: UUID,
        role: UserRole,
        authType: UserAuthType = UserAuthType.KAKAO,
    ): String {
        val claims = Jwts.claims().setSubject(userId.toString()) // UUID를 String으로 변환
        claims["roles"] = role.name
        claims["authType"] = authType.name
        val now = Date()
        val validity = Date(now.time + accessTokenValidity)

        return Jwts.builder()
            .setClaims(claims)
            .setIssuedAt(now)
            .setExpiration(validity)
            .signWith(key, SignatureAlgorithm.HS256)
            .compact()
    }

    // refresh token 생성
    fun createRefreshToken(userId: UUID): String {
        val now = Date()
        val validity = Date(now.time + refreshTokenValidity)

        return Jwts.builder()
            .setSubject(userId.toString()) // UUID를 String으로 변환
            .setIssuedAt(now)
            .setExpiration(validity)
            .signWith(key, SignatureAlgorithm.HS256)
            .compact()
    }

    fun getAuthentication(token: String): UsernamePasswordAuthenticationToken {
        val claims = parseClaims(token)
        val userId = claims.subject
        val roleString = claims["roles"] as? String ?: "USER"
        val role = try {
            UserRole.valueOf(roleString)
        } catch (e: IllegalArgumentException) {
            UserRole.USER
        }

        val authorities = listOf(SimpleGrantedAuthority("ROLE_${role.name}"))

        return UsernamePasswordAuthenticationToken(userId, "", authorities)
    }

    // 헤더에서 토큰 추출 ("Bearer " 제거)
    fun resolveToken(request: HttpServletRequest): String? {
        val bearerToken = request.getHeader("Authorization")
        return if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            bearerToken.substring(7)
        } else null
    }

    fun resolveToken(bearerToken: String): String {
        return if (bearerToken.startsWith("Bearer ")) {
            bearerToken.substring(7)
        } else {
            bearerToken
        }
    }

    // 토큰 유효성 검증
    fun validateToken(token: String): Boolean {
        return try {
            val claims = parseClaims(token)
            !claims.expiration.before(Date())
        } catch (e: Exception) {
            false
        }
    }

    fun getUserIdFromToken(token: String): UUID {
        val claims = parseClaims(token)
        return UUID.fromString(claims.subject)
    }

    private fun parseClaims(token: String): Claims {
        return Jwts.parserBuilder()
            .setSigningKey(key)
            .build()
            .parseClaimsJws(token)
            .body
    }
}
