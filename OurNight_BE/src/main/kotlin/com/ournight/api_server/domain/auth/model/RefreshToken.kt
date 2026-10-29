package com.ournight.api_server.domain.auth.model

import org.springframework.data.annotation.Id
import org.springframework.data.redis.core.RedisHash
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID

@RedisHash(value = "refreshToken", timeToLive = 60 * 60 * 24 * 7)
data class RefreshToken(
    @Id
    val tokenHash: String,

    val userId: String,

    val token: String
) {
    companion object {
        fun from(userId: UUID, token: String): RefreshToken =
            RefreshToken(
                tokenHash = hash(token),
                userId = userId.toString(),
                token = token
            )

        fun hash(token: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8))
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
        }
    }
}
