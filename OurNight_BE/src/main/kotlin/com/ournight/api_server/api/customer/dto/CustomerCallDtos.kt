package com.ournight.api_server.api.customer.dto

import java.util.UUID

// 고객이 보는 호출 옵션 목록
data class CustomerCallOptionResponse(
    val optionId: UUID,
    val name: String
)

// 고객의 호출 발생 요청
data class CustomerCallCreateRequest(
    val tableId: UUID,
    val optionId: UUID
)