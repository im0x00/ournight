package com.ournight.api_server.global.error

import jakarta.persistence.OptimisticLockException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

data class SimpleErrorResponse(
    val code: String,
    val message: String
)

@RestControllerAdvice
class OptimisticLockExceptionAdvice {

    @ExceptionHandler(
        OptimisticLockException::class,
        ObjectOptimisticLockingFailureException::class
    )
    fun handleOptimisticLock(e: Exception): ResponseEntity<SimpleErrorResponse> {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            SimpleErrorResponse(
                code = "OPTIMISTIC_LOCK_CONFLICT",
                message = "이미 다른 처리로 상태가 바뀌었습니다. 화면을 새로고침 후 다시 시도해주세요."
            )
        )
    }
}