package com.ournight.api_server.domain.guest.service

import com.ournight.api_server.api.customer.dto.toResponse
import com.ournight.api_server.api.guest.dto.GuestPassActivateResponse
import com.ournight.api_server.domain.pass.service.PassService
import com.ournight.api_server.domain.user.model.User
import com.ournight.api_server.domain.user.model.UserAuthType
import com.ournight.api_server.domain.user.model.UserRole
import com.ournight.api_server.domain.user.repository.UserRepository
import com.ournight.api_server.global.jwt.JwtTokenProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class GuestAuthService(
    private val userRepository: UserRepository,
    private val passService: PassService,
    private val jwtTokenProvider: JwtTokenProvider,
) {
    @Transactional
    fun activatePass(
        storeId: UUID,
        slotId: UUID,
        code: String,
        currentUser: User?,
    ): GuestPassActivateResponse {
        val guestUser = currentUser
            ?.takeIf { it.role == UserRole.GUEST && it.authType == UserAuthType.GUEST }
            ?: createGuestUser()
        val guestUserId = guestUser.id!!

        val pass = passService.activatePass(
            userId = guestUserId,
            storeId = storeId,
            slotId = slotId,
            inputCode = code,
        )

        val accessToken = jwtTokenProvider.createAccessToken(
            userId = guestUserId,
            role = guestUser.role,
            authType = guestUser.authType,
        )

        return GuestPassActivateResponse(
            accessToken = accessToken,
            userId = guestUserId,
            nickname = guestUser.nickname,
            pass = pass.toResponse(),
        )
    }

    private fun createGuestUser(): User {
        val user = User(
            nickname = "guest-${UUID.randomUUID().toString().take(8)}",
            role = UserRole.GUEST,
            authType = UserAuthType.GUEST,
        )
        return userRepository.save(user)
    }
}
