package com.ournight.api_server.common.health.dto

import com.fasterxml.jackson.annotation.JsonFormat
import java.time.LocalDateTime

/**
 * 헬스체크/Ping API 응답 DTO
 * 
 * @property status 서버 상태 (ok, healthy 등)
 * @property message 상태 메시지
 * @property timestamp 응답 생성 시각
 */
data class HealthCheckResponse(
	val status: String,
	val message: String,
	@JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
	val timestamp: LocalDateTime
)

