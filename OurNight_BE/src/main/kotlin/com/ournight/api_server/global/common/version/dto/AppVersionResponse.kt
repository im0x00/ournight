package com.ournight.api_server.global.common.version.dto

data class AppVersionResponse(
    val service: String,
    val version: String,
    val commit: String?,
    val builtAt: String?,
)
