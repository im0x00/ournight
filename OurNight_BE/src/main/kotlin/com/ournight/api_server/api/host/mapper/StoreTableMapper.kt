package com.ournight.api_server.api.host.mapper

import com.ournight.api_server.api.host.dto.*
import com.ournight.api_server.domain.store.view.*
import java.util.UUID

object HostStoreTableMapper {

    fun toCommand(
        storeId: UUID,
        request: SaveStoreTableLayoutRequest
    ): SaveStoreTableLayoutCommand =
        SaveStoreTableLayoutCommand(
            storeId = storeId,
            tables = request.tables.map {
                StoreTableLayoutItemCommand(
                    id = it.id,
                    zoneId = it.zoneId,
                    tableNumber = it.tableNumber,
                    xPosition = it.xPosition,
                    yPosition = it.yPosition,
                    capacity = it.capacity,
                    qrImageUrl = it.qrImageUrl,
                )
            }
        )

    fun toResponse(view: StoreTableView): HostStoreTableResponse =
        HostStoreTableResponse(
            id = view.id,
            tableNumber = view.tableNumber,
            capacity = view.capacity,
            status = view.status,
            qrLink = view.qrLink,
            qrImageUrl = view.qrImageUrl,
            zoneId = view.zoneId,
            zoneName = view.zoneName,
            xPosition = view.xPosition,
            yPosition = view.yPosition,
        )

    fun toDetailResponse(view: StoreTableView): HostStoreTableDetailResponse =
        HostStoreTableDetailResponse(
            id = view.id,
            tableNumber = view.tableNumber,
            xPosition = view.xPosition,
            yPosition = view.yPosition,
            capacity = view.capacity,
            qrLink = view.qrLink,
            qrImageUrl = view.qrImageUrl,
            status = view.status,
            zoneId = view.zoneId,
            zoneName = view.zoneName,
        )

    fun toZoneCreateCommand(request: StoreTableZoneCreateRequest): CreateStoreTableZoneCommand =
        CreateStoreTableZoneCommand(
            storeId = request.storeId,
            name = request.name,
            displayOrder = request.displayOrder,
        )

    fun toZoneUpdateCommand(request: StoreTableZoneUpdateRequest): UpdateStoreTableZoneCommand =
        UpdateStoreTableZoneCommand(
            name = request.name,
            displayOrder = request.displayOrder,
            isActive = request.isActive,
        )

    fun toZoneResponse(view: StoreTableZoneView): HostStoreTableZoneResponse =
        HostStoreTableZoneResponse(
            id = view.id,
            name = view.name,
            displayOrder = view.displayOrder,
            isActive = view.isActive,
        )

    fun toZoneWithTablesResponse(view: StoreTableZoneWithTablesView): HostStoreTableZoneWithTablesResponse =
        HostStoreTableZoneWithTablesResponse(
            zoneId = view.zoneId,
            zoneName = view.zoneName,
            displayOrder = view.displayOrder,
            tables = view.tables.map(::toResponse),
        )
}