package com.ournight.api_server.api.pos.dto

import java.util.UUID

data class PosStreamChangesResponse(
    val sinceEventId: String,
    val currentWatermark: String,
    val requiresReload: Boolean,
    val reason: String? = null,
    val changedOrderIds: List<UUID> = emptyList(),
    val changedTableIds: List<UUID> = emptyList(),
    val changedMenuIds: List<UUID> = emptyList(),
    val changedCallRequestIds: List<UUID> = emptyList(),
)
