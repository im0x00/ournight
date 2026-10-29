package com.ournight.api_server.api.pos.controller

import com.ournight.api_server.api.host.dto.StoreMenuCategoryResponse
import com.ournight.api_server.api.host.dto.StoreMenuResponse
import com.ournight.api_server.api.pos.dto.OrderRoundResponse
import com.ournight.api_server.api.pos.dto.StoreMenuCategoryWithMenusResult
import com.ournight.api_server.api.pos.dto.StoreMenuResult
import com.ournight.api_server.domain.notification.service.PosStreamWatermarkService
import com.ournight.api_server.domain.order.service.OrderService
import com.ournight.api_server.domain.store.application.StoreMenuService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/pos/{storeId}/hall")
class HallStoreOperationController(
    private val storeMenuService: StoreMenuService,
    private val orderService: OrderService,
    private val posStreamWatermarkService: PosStreamWatermarkService,
) {

    @GetMapping("/categories-with-menus")
    fun getCategoriesWithMenus(
        @PathVariable storeId: UUID
    ): ResponseEntity<List<StoreMenuCategoryWithMenusResult>> {
        return ResponseEntity.ok(storeMenuService.getCategoriesWithMenusForPos(storeId))
    }

    // 참고: 현재 세션의 주문만 표시
    @GetMapping("/tables/{tableId}/active-order-rounds")
    fun getActiveOrderRounds(
        @PathVariable storeId: UUID,
        @PathVariable tableId: UUID
    ): ResponseEntity<List<OrderRoundResponse>> {
        val watermark = posStreamWatermarkService.currentWatermark(storeId)
        val rounds = orderService.findRoundsByTable(storeId, tableId)
        return posStreamWatermarkService.okWithWatermark(watermark, rounds)
    }
}
