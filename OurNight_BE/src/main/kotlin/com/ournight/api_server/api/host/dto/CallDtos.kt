package com.ournight.api_server.api.host.dto

import com.ournight.api_server.domain.call.model.CallStatus
import java.time.LocalDateTime
import java.util.UUID



// 호출 상태 변경 (Host용 - 수락/완료)

// 호출 옵션 생성 및 수정
data class HostCallOptionUpsertRequest(
    val name: String,
    val isActive: Boolean = true,
)


// 홀에 들어온 호출 요청 응답 (Host 화면용)
data class HallCallRequestResponse(
    val storeId: UUID,
    val status: String = "OPEN",
    val callRequestId: UUID,
    val tableId: UUID,
    val sessionId: UUID?,
    val tableNumber: Int,
    val optionName: String,
    val callStatus: CallStatus,
    val createdAt: LocalDateTime
)
