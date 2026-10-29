package com.ournight.api_server.global.config


import com.ournight.api_server.global.debug.RequestMappingInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
@Profile("debug")
class WebMvcConfig(
    private val requestMappingInterceptor: RequestMappingInterceptor
) : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(requestMappingInterceptor)
            .addPathPatterns("/**")
            .excludePathPatterns(
                "/actuator/**",
                "/favicon.ico",
                "/error"
            )
    }
}