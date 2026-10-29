package com.ournight.api_server.global.common.health.controller

import com.ournight.api_server.common.health.dto.HealthCheckResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@RestController
@RequestMapping("/api")
class HealthCheckController {

    /**
     * 헬스체크 API
     * 
     * @return 서버 상태 정보와 현재 타임스탬프
     */
    @GetMapping("/health")
    fun health(): ResponseEntity<HealthCheckResponse> {
        return ResponseEntity.ok(
            HealthCheckResponse(
                "healthy",
                "All systems operational",
                LocalDateTime.now()
            )
        )
    }
}

