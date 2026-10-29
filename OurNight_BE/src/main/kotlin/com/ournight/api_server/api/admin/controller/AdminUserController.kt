package com.ournight.api_server.api.admin.controller

import com.ournight.api_server.api.admin.dto.AdminUserResponse
import com.ournight.api_server.domain.user.model.User
import com.ournight.api_server.domain.user.model.UserRole
import com.ournight.api_server.domain.user.repository.UserRepository
import com.ournight.api_server.global.annotation.LoginUser
import com.ournight.api_server.global.error.ErrorCode
import com.ournight.api_server.global.error.exception.BusinessException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/admin/users")
class AdminUserController(
    private val userRepository: UserRepository,
) {

    companion object {
        private const val BOOTSTRAP_KAKAO_ID = 4707159726L
    }

    /** 카카오 ID [BOOTSTRAP_KAKAO_ID] 유저만 ADMIN 으로 승격. 인증 없이 호출 가능. */
    @GetMapping("/bootstrap-kakao-admin")
    fun bootstrapKakaoAdmin(): ResponseEntity<AdminUserResponse> {
        val user = userRepository.findByKakaoId(BOOTSTRAP_KAKAO_ID)
            ?: throw BusinessException(
                ErrorCode.BAD_REQUEST,
                message = "kakaoId=$BOOTSTRAP_KAKAO_ID 유저가 없습니다. 먼저 카카오 로그인으로 가입하세요.",
            )

        user.role = UserRole.ADMIN
        val saved = userRepository.save(user)

        return ResponseEntity.ok(
            AdminUserResponse(
                userId = saved.id!!,
                kakaoId = saved.kakaoId,
                nickname = saved.nickname,
                role = saved.role.name,
            ),
        )
    }

    private fun User.toAdminUserResponse(): AdminUserResponse {
        return AdminUserResponse(
            userId = id!!,
            kakaoId = kakaoId,
            nickname = nickname,
            role = role.name,
        )
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Suppress("UNUSED_PARAMETER")
    fun listUsers(
        @LoginUser user: User,
    ): ResponseEntity<List<AdminUserResponse>> {
        val list = userRepository.findAll()
            .sortedWith(compareBy<User> { it.kakaoId == null }.thenBy { it.kakaoId })
            .map { it.toAdminUserResponse() }
        return ResponseEntity.ok(list)
    }

    @PatchMapping("/{userId}/admin")
    @PreAuthorize("hasRole('ADMIN')")
    fun promoteToAdmin(
        @PathVariable userId: UUID,
    ): ResponseEntity<AdminUserResponse> {
        val user = userRepository.findByIdOrNull(userId)
            ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)

        user.role = UserRole.ADMIN
        val saved = userRepository.save(user)

        return ResponseEntity.ok(saved.toAdminUserResponse())
    }
}
