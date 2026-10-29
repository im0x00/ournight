package com.ournight.api_server.domain.user.model

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

class CustomUserDetails(
    private val user: User
) : UserDetails {
    override fun getAuthorities(): Collection<GrantedAuthority> {
        return listOf(SimpleGrantedAuthority("ROLE_${user.role.name}"))
    }

    override fun getUsername(): String = user.id.toString()

    // 비밀번호 기반 인증이 아니므로(카카오 로그인) 빈 문자열 또는 더미 데이터 반환
    override fun getPassword(): String = ""

    // 계정 상태 정보 (비즈니스 요구사항에 따라 필드 추가 가능) TODO
    override fun isAccountNonExpired(): Boolean = true
    override fun isAccountNonLocked(): Boolean = true
    override fun isCredentialsNonExpired(): Boolean = true
    override fun isEnabled(): Boolean = true

    // 추가 정보가 필요할 때 엔티티를 직접 꺼내 쓸 수 있도록 getter 제공
    fun getUser(): User = user
}