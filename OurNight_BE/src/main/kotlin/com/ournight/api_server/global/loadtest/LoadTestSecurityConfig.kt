package com.ournight.api_server.global.loadtest

import com.ournight.api_server.global.jwt.MockAuthFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@Profile("loadtest")
class LoadTestSecurityConfig(
    private val mockAuthFilter: MockAuthFilter
) {
    @Bean
    fun loadTestFilterChain(http: HttpSecurity): SecurityFilterChain {
        http.csrf { it.disable() }
        http.cors { }
        http.sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
        http.addFilterBefore(mockAuthFilter, UsernamePasswordAuthenticationFilter::class.java)

        http.authorizeHttpRequests {
            it.requestMatchers("/__load-test/**").permitAll()
            it.requestMatchers("/actuator/health", "/actuator/metrics/**").permitAll()
            it.requestMatchers(HttpMethod.GET, "/api/health").permitAll()
            it.requestMatchers(HttpMethod.GET, "/api/pos/*/notifications/subscribe").permitAll()
            it.anyRequest().authenticated()
        }

        return http.build()
    }
}
