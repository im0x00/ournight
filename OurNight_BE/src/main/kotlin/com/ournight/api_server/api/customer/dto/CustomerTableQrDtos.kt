package com.ournight.api_server.api.customer.dto

import com.ournight.api_server.domain.store.view.StoreTableView
import java.util.UUID

data class CustomerTableQrResponse(
    val id: UUID,
    val tableNumber: Int,
    val status: String,
    val qrLink: String?,
    val zoneId: UUID?,
    val zoneName: String?,
)

fun StoreTableView.toCustomerTableQrResponse() = CustomerTableQrResponse(
    id = id,
    tableNumber = tableNumber,
    status = status,
    qrLink = qrLink,
    zoneId = zoneId,
    zoneName = zoneName,
)
