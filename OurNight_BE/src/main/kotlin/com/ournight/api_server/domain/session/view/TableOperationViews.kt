package com.ournight.api_server.domain.session.view

import java.util.UUID

data class TableClearView(
    val tableId: UUID,
    val tableNumber: Int,
    val tableStatus: String,
    val closedSessionId: UUID?,
)

data class TableMoveView(
    val sessionId: UUID,
    val fromTableId: UUID,
    val toTableId: UUID,
    val fromTableStatus: String,
    val toTableStatus: String,
)

data class TableRejoinView(
    val targetSessionId: UUID,
    val sourceTableId: UUID,
    val targetTableId: UUID,
    val targetTableStatus: String,
)