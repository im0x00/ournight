package com.ournight.api_server.domain.store.view

import java.util.UUID

data class CreateStoreTableZoneCommand(
    val storeId: UUID,
    val name: String,
    val displayOrder: Int = 0,
)

data class UpdateStoreTableZoneCommand(
    val name: String,
    val displayOrder: Int,
    val isActive: Boolean,
)

data class SaveStoreTableLayoutCommand(
    val storeId: UUID,
    val tables: List<StoreTableLayoutItemCommand>
)

data class StoreTableLayoutItemCommand(
    val id: UUID?,
    val zoneId: UUID,
    val tableNumber: Int,
    val xPosition: Double,
    val yPosition: Double,
    val capacity: Int,
    val qrImageUrl: String?,
)