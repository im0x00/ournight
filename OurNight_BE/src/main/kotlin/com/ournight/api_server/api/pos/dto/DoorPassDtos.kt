package com.ournight.api_server.api.pos.dto

import com.ournight.api_server.domain.pass.view.PassKeyView

data class ActivationCodeResponse(
    val code: String?,
    val type: String?,
    val configured: Boolean
)



fun PassKeyView.toResponse(): ActivationCodeResponse {
    return ActivationCodeResponse(
        code = this.code,
        type = this.type,
        configured = this.configured
    )
}
