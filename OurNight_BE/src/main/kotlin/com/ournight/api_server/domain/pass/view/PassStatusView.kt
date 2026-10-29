package com.ournight.api_server.domain.pass.view

import java.time.LocalDateTime
import java.util.UUID

data class PassStatusView(
    val slotId: UUID,
    val hasPass: Boolean,
    /** INACTIVE / ACTIVATED / EXPIRED, pass 행이 없으면 null */
    val status: String?,
    /** 입장·주문에 쓸 수 있는 활성 패스 여부 */
    val orderable: Boolean,
    val passId: UUID?,
    val activatedAt: LocalDateTime?,
    val expiresAt: LocalDateTime?,
)
