package com.ournight.api_server.global.jwt

import com.ournight.api_server.domain.user.model.CustomUserDetails
import com.ournight.api_server.domain.user.repository.UserRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Profile
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID


@Component
@Profile("local-mock", "loadtest")
class MockAuthFilter(
    private val userRepository: UserRepository
) : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val uri = request.requestURI
        return uri.startsWith("/__load-test") || uri.startsWith("/actuator")
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        if (SecurityContextHolder.getContext().authentication == null) {
            val requestedUserId = request.getHeader("X-Load-Test-User-Id")
                ?.takeIf { it.isNotBlank() }
                ?.let { UUID.fromString(it) }

            val user = requestedUserId
                ?.let { userRepository.findByIdOrNull(it) }
                ?: userRepository.findAll().firstOrNull()
                ?: throw IllegalStateException("local dev user가 없습니다.")

            val userDetails = CustomUserDetails(user)
            val auth = UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.authorities
            )

            SecurityContextHolder.getContext().authentication = auth
        }

        filterChain.doFilter(request, response)
    }
}
