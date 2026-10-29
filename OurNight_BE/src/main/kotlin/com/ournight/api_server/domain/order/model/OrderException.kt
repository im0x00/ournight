package com.ournight.api_server.domain.order.model

import com.ournight.api_server.global.error.ErrorCode
import com.ournight.api_server.global.error.exception.BusinessException

class OutOfStockException(val unavailableMenuIds: List<java.util.UUID>)
    : BusinessException(ErrorCode.OUT_OF_STOCK, mapOf("unavailableMenuIds" to unavailableMenuIds))