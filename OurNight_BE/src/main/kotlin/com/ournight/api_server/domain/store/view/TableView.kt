package com.ournight.api_server.domain.store.view

import java.util.UUID

data class StoreTableView(
    val id: UUID,
    val tableNumber: Int,
    val xPosition: Double,
    val yPosition: Double,
    val capacity: Int,
    val status: String,
    val qrLink: String?,
    val qrImageUrl: String?,
    val zoneId: UUID?,
    val zoneName: String?,

)

data class StoreTableZoneView(
    val id: UUID,
    val name: String,
    val displayOrder: Int,
    val isActive: Boolean,
)

data class StoreTableZoneWithTablesView(
    val zoneId: UUID?,
    val zoneName: String,
    val displayOrder: Int,
    val tables: List<StoreTableView>,
)