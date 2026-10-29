package com.ournight.api_server.global.error

import com.ournight.api_server.domain.order.model.RateLimitExceededException
import com.ournight.api_server.global.error.exception.BusinessException
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.LocalDateTime

@RestControllerAdvice
class GlobalExceptionHandler {

	private fun buildResponse(
		errorCode: ErrorCode,
		details: Any? = null,
		messageOverride: String? = null,
		request: HttpServletRequest
	): ResponseEntity<ErrorResponse> {
		return ResponseEntity
			.status(errorCode.status)
			.body(ErrorResponse(
				code = errorCode.code,
				message = messageOverride ?: errorCode.message,
				details = details,
				path = request.requestURI,
				method = request.method
			))
	}

	@ExceptionHandler(BusinessException::class)
	fun handleBusinessException(
		e: BusinessException,
		request: HttpServletRequest
	): ResponseEntity<ErrorResponse> {
		return buildResponse(e.errorCode, e.details, e.message, request)
	}

	@ExceptionHandler(AccessDeniedException::class)
	fun handleAccessDenied(
		e: AccessDeniedException,
		request: HttpServletRequest,
	): ResponseEntity<ErrorResponse> {
		return buildResponse(ErrorCode.FORBIDDEN, null, e.message, request)
	}

	@ExceptionHandler(RateLimitExceededException::class)
	fun handleRateLimitException(
		e: RateLimitExceededException,
		request: HttpServletRequest
	): ResponseEntity<ErrorResponse> {
		val errorCode = ErrorCode.RATE_LIMIT_EXCEEDED

		// Retry-After 헤더 설정
		val headers = HttpHeaders()
		headers.set(HttpHeaders.RETRY_AFTER, e.retryAfterSeconds.toString())

		// ErrorResponse 생성 (Enum 활용)
		val response = ErrorResponse(
			code = errorCode.code,
			message = errorCode.message, // "요청이 너무 많습니다."
			details = "Please retry after ${e.retryAfterSeconds} seconds.",
			timestamp = LocalDateTime.now(),
			path = request.requestURI,
			method = request.method
		)

		return ResponseEntity
			.status(errorCode.status) // 429 TOO_MANY_REQUESTS
			.headers(headers)
			.body(response)
	}

	@ExceptionHandler(Exception::class)
	fun handleException(
		e: Exception,
		request: HttpServletRequest
	): ResponseEntity<ErrorResponse> {
		e.printStackTrace()
		return buildResponse(ErrorCode.INTERNAL_SERVER_ERROR, null, null, request)
	}
}

