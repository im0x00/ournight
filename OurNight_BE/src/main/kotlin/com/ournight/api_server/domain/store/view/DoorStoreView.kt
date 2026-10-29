package com.ournight.api_server.domain.store.view

data class DoorStatusView(
    val congestion: StoreCongestionLevel,
    val remainingTableCount: Int,
    val waitingEnabled: Boolean
)

enum class StoreCongestionLevel {
    UNCONFIGURED,
    RELAXED,
    NORMAL,
    CROWDED
}

