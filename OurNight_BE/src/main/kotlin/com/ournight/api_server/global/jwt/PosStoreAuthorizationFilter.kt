package com.ournight.api_server.global.jwt

import com.ournight.api_server.domain.store.dao.StoreRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.http.HttpMethod
import java.util.UUID

@Component
class PosStoreAuthorizationFilter(
    private val storeRepository: StoreRepository,
) : OncePerRequestFilter() {
    private val storePath = Regex("^/api/pos/([0-9a-fA-F-]{36})(?:/.*)?$")

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val uri = request.requestURI
        return request.method == HttpMethod.OPTIONS.name() ||
            !uri.startsWith("/api/pos/") ||
            uri.endsWith("/notifications/subscribe")
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val storeId = storePath.matchEntire(request.requestURI)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val userId = SecurityContextHolder.getContext().authentication
            ?.takeIf { it.isAuthenticated }
            ?.name
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }

        if (storeId == null || userId == null || !storeRepository.existsByIdAndHostId(storeId, userId)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "이 매장의 POS에 접근할 수 없습니다.")
            return
        }

        filterChain.doFilter(request, response)
    }
}
