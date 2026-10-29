package com.ournight.api_server.global.error.exception

import com.ournight.api_server.global.error.ErrorCode

open class BusinessException(
	val errorCode: ErrorCode,
	val details: Any? = null,
	override val message: String = errorCode.message
) : RuntimeException(message)

