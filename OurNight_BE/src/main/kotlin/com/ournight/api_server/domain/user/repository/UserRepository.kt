package com.ournight.api_server.domain.user.repository

import com.ournight.api_server.domain.user.model.User
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {
    fun findByKakaoId(kakaoId: Long): User?
}