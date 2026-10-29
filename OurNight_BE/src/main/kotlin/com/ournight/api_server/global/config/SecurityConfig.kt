package com.ournight.api_server.common.config

import com.ournight.api_server.global.jwt.JwtFilter
import com.ournight.api_server.global.jwt.PosStoreAuthorizationFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
@Profile("!loadtest & !local-mock")
class SecurityConfig(
    private val jwtFilter: JwtFilter,
    private val posStoreAuthorizationFilter: PosStoreAuthorizationFilter,
) {
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http.csrf { it.disable() }
        http.cors { }
        http.sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }

        http.authorizeHttpRequests {
            it.requestMatchers(HttpMethod.GET, "/", "/api/health", "/api/version").permitAll()
            it.requestMatchers("/api/auth/**").permitAll()
            it.requestMatchers("/api/guest/**").permitAll()
            it.requestMatchers(HttpMethod.GET, "/api/customer/stores/scraps").hasAnyRole("USER", "ADMIN")
            it.requestMatchers("/api/customer/stores/*/scrap").hasAnyRole("USER", "ADMIN")
            it.requestMatchers(HttpMethod.POST, "/api/customer/stores/*/guestbook").hasAnyRole("USER", "ADMIN")
            it.requestMatchers(HttpMethod.GET, "/api/customer/stores", "/api/customer/stores/*", "/api/customer/stores/*/menus", "/api/customer/stores/*/guestbook").permitAll()
            it.requestMatchers(HttpMethod.GET, "/api/waiting-status").permitAll()
            it.requestMatchers(HttpMethod.GET, "/api/pos/*/notifications/subscribe").permitAll()
            it.requestMatchers("/__dev/**").permitAll()
            it.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
            it.requestMatchers(HttpMethod.GET, "/api/admin/users/bootstrap-kakao-admin").permitAll()
            it.requestMatchers("/api/admin/**").hasRole("ADMIN")
            it.requestMatchers("/api/host/**").hasAnyRole("USER", "ADMIN")
            it.requestMatchers("/api/pos/**").hasAnyRole("USER", "ADMIN")
            it.requestMatchers("/api/customer/**").hasAnyRole("USER", "ADMIN", "GUEST")
            it.anyRequest().authenticated()
        }
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterAfter(posStoreAuthorizationFilter, JwtFilter::class.java)

        return http.build()
    }
}
