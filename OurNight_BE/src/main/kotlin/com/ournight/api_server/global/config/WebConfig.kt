package com.ournight.api_server.common.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig(
    @Value("\${aws.s3.local-storage-enabled:false}")
    private val localStorageEnabled: Boolean,
    @Value("\${aws.s3.local-storage-dir:build/local-uploads}")
    private val localStorageDir: String,
    @Value("\${app.cors.allowed-origins:http://localhost:3000,https://alpha.ournight.co.kr}")
    private val allowedOriginsRaw: String,
) : WebMvcConfigurer {
    override fun addCorsMappings(registry: CorsRegistry) {
        val origins = allowedOriginsRaw.split(",").map { it.trim() }.toTypedArray()
        registry.addMapping("/**")
            .allowedOrigins(*origins)
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .exposedHeaders("X-Request-Id", "X-Pos-Stream-Watermark")
            .allowCredentials(true)
    }

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        if (!localStorageEnabled) return

        registry.addResourceHandler("/local-uploads/**")
            .addResourceLocations("file:${localStorageDir.trimEnd('/')}/")
    }
}
