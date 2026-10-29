package com.ournight.api_server.api.pos.controller

import com.ournight.api_server.api.pos.dto.PosTableOrderDetailResponse
import com.ournight.api_server.api.pos.dto.PosTableOrderSummaryListResponse
import com.ournight.api_server.domain.order.service.OrderQueryService
import com.ournight.api_server.domain.user.model.User
import com.ournight.api_server.global.annotation.CheckStoreOwner
import com.ournight.api_server.global.annotation.LoginUser
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/pos/{storeId}/orders")
class PosOrderHistoryController(
    private val posOrderQueryService: OrderQueryService
) {

    @GetMapping("/active")
    @CheckStoreOwner
    fun listActiveOrders(
        @LoginUser user: User,
        @PathVariable storeId: UUID,
        @RequestParam(required = false) slotId: UUID?,
    ): PosTableOrderSummaryListResponse =
        posOrderQueryService.listActiveTables(storeId, slotId)

    @GetMapping("/active/{tableId}")
    @CheckStoreOwner
    fun getActiveOrderByTable(
        @LoginUser user: User,
        @PathVariable storeId: UUID,
        @PathVariable tableId: UUID,
    ): PosTableOrderDetailResponse =
        posOrderQueryService.getActiveTableDetail(storeId, tableId)

    @GetMapping("/history")
    @CheckStoreOwner
    fun listOrderHistory(
        @LoginUser user: User,
        @PathVariable storeId: UUID,
        @RequestParam slotId: UUID,
    ): PosTableOrderSummaryListResponse =
        posOrderQueryService.listHistoryTables(storeId, slotId)

    @GetMapping("/history/{sessionId}")
    @CheckStoreOwner
    fun getOrderHistoryBySession(
        @LoginUser user: User,
        @PathVariable storeId: UUID,
        @PathVariable sessionId: UUID,
        @RequestParam slotId: UUID,
    ): PosTableOrderDetailResponse =
        posOrderQueryService.getHistorySessionDetail(storeId, sessionId, slotId)
}
