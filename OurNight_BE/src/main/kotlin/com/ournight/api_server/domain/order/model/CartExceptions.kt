package com.ournight.api_server.domain.order.model

import com.ournight.api_server.global.error.ErrorCode
import com.ournight.api_server.global.error.exception.BusinessException

class CartVersionMismatchException(val latest: Any)
    : BusinessException(ErrorCode.CART_VERSION_MISMATCH, latest)

class OrderAlreadyExistsException(val latest: Any)
    : BusinessException(ErrorCode.ORDER_ALREADY_EXISTS, latest)

class CartLockedException(val latest: Any)
    : BusinessException(ErrorCode.CART_LOCKED, latest)

class RateLimitExceededException(val retryAfterSeconds: Long)
    : BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED)
