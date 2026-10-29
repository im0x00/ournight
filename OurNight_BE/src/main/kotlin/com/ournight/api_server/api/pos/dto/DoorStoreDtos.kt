package com.ournight.api_server.api.pos.dto

import com.ournight.api_server.domain.store.view.DoorStatusView
import com.ournight.api_server.domain.store.view.StoreCongestionLevel

data class DoorStatusResponse(
    val congestion: StoreCongestionLevel,
    val remainingTableCount: Int,
    val waitingEnabled: Boolean
)

data class UpdateWaitingStatusRequest(
    val waitingEnabled: Boolean
)

fun DoorStatusView.toResponse(): DoorStatusResponse =
    DoorStatusResponse(
        congestion = congestion,
        remainingTableCount = remainingTableCount,
        waitingEnabled = waitingEnabled
    )