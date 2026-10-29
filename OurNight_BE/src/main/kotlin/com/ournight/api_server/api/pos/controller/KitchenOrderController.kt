package com.ournight.api_server.api.pos.controller

import com.ournight.api_server.api.pos.dto.KitchenOrderCardDto
import com.ournight.api_server.api.pos.dto.KitchenOrderItemDto
import com.ournight.api_server.api.pos.dto.KitchenOrderListResponse
import com.ournight.api_server.domain.order.model.OrderStatus
import com.ournight.api_server.domain.order.model.decodeCursor
import com.ournight.api_server.domain.order.model.encodeCursor
import com.ournight.api_server.domain.notification.service.PosStreamWatermarkService
import com.ournight.api_server.domain.order.service.OrderService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

@RestController
@RequestMapping("/api/pos/{storeId}/kitchen")
class KitchenOrderController(
    private val orderService: OrderService,
    private val clock: Clock,
    private val posStreamWatermarkService: PosStreamWatermarkService,
) {

    // 조리 완료
    // 음식이 완성되어 [조리 완료] 버튼 클릭 -> 홀 스태프 서빙 목록으로 이동
    @PatchMapping("/order-items/{itemId}/complete")
    fun completeCooking(
        @PathVariable itemId: UUID
    ): ResponseEntity<Void> {
        orderService.completeCookingItem(itemId)
        return ResponseEntity.ok().build()
    }

    private val ZONE: ZoneId = ZoneId.of("Asia/Seoul")

    //Order=CONFIRMED인 것만 보여주기. OrderItem -> COOKING, COOKED, SERVED, CANCELED 가능
    @GetMapping("/active-items")
    fun getActiveKitchenItems(
        @PathVariable storeId: UUID,
        @RequestParam(required = false, defaultValue = "CONFIRMED") status: OrderStatus,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false, defaultValue = "10") limit: Int,
    ): ResponseEntity<KitchenOrderListResponse> {
        val watermark = posStreamWatermarkService.currentWatermark(storeId)
        val safeLimit = limit.coerceIn(1, 50)

        val decodedCursor = cursor?.let { decodeCursor(it) }

        val result = orderService.listKitchenOrders(storeId, status, decodedCursor, safeLimit)
        val now = LocalDateTime.now(clock)

        val response = KitchenOrderListResponse(
            items = result.items.map { c ->
                KitchenOrderCardDto(
                    orderId = c.orderId,
                    tableId = c.tableId,
                    tableNumber = c.tableNumber,
                    tableOrderSequence = c.tableOrderSequence,
                    status = c.status,
                    totalPrice = c.totalAmount.toInt(),
                    createdAt = c.orderedAt.atZone(ZONE).toInstant(),
                    elapsedMinutes = Duration.between(c.orderedAt, now).toMinutes(),
                    items = c.items.map { i ->
                        KitchenOrderItemDto(
                            orderItemId = i.orderItemId,
                            menuId = i.menuId,
                            menuName = i.menuName,
                            qty = i.quantity,
                            status = i.status,
                            requiresCooking = i.requiresCooking
                        )
                    }
                )
            },
            nextCursor = result.nextCursor?.let { encodeCursor(it) },
            hasNext = result.hasNext,
        )

        return posStreamWatermarkService.okWithWatermark(watermark, response)
    }

    @GetMapping("/active-items/by-ids")
    fun getActiveKitchenItemsByIds(
        @PathVariable storeId: UUID,
        @RequestParam ids: List<UUID>,
    ): ResponseEntity<KitchenOrderListResponse> {
        val watermark = posStreamWatermarkService.currentWatermark(storeId)
        val now = LocalDateTime.now(clock)
        val cards = orderService.listKitchenOrdersByIds(storeId, ids.distinct().take(100))

        val response = KitchenOrderListResponse(
            items = cards.map { c ->
                KitchenOrderCardDto(
                    orderId = c.orderId,
                    tableId = c.tableId,
                    tableNumber = c.tableNumber,
                    tableOrderSequence = c.tableOrderSequence,
                    status = c.status,
                    totalPrice = c.totalAmount.toInt(),
                    createdAt = c.orderedAt.atZone(ZONE).toInstant(),
                    elapsedMinutes = Duration.between(c.orderedAt, now).toMinutes(),
                    items = c.items.map { i ->
                        KitchenOrderItemDto(
                            orderItemId = i.orderItemId,
                            menuId = i.menuId,
                            menuName = i.menuName,
                            qty = i.quantity,
                            status = i.status,
                            requiresCooking = i.requiresCooking
                        )
                    }
                )
            },
            nextCursor = null,
            hasNext = false,
        )

        return posStreamWatermarkService.okWithWatermark(watermark, response)
    }
}
