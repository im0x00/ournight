package com.ournight.api_server.global.error

import java.time.LocalDateTime

data class ErrorResponse(
	val message: String,
	val code: String? = null,
	val details: Any? = null,

	val timestamp: LocalDateTime? = null,
	val path: String? = null,
	val method: String? = null,

)

