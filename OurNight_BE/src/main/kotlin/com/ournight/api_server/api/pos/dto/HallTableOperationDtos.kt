package com.ournight.api_server.api.pos.dto

import java.util.UUID

data class TableMoveRequest(val toTableId: UUID)
data class TableMergeRequest(val sourceTableId: UUID, val targetTableId: UUID)
data class TableClearRequest(val expectedSessionId: UUID)

data class TableClearResponse(
    val tableId: UUID,
    val tableNumber: Int,
    val status: String,
    val closedSessionId: UUID?,
)

data class TableMoveResponse(
    val sessionId: UUID,
    val fromTableId: UUID,
    val toTableId: UUID,
    val fromTableStatus: String,
    val toTableStatus: String,
)

data class TableMergeResponse(
    val mergedSessionId: UUID,
    val sourceTableId: UUID,
    val targetTableId: UUID,
    val targetTableStatus: String,
)
