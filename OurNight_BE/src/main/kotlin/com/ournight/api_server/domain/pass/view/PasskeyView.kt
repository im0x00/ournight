package com.ournight.api_server.domain.pass.view

data class PassKeyView(
    val code: String?,
    val type: String?, // PIN, QR
    val configured: Boolean
)
