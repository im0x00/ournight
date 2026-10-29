package com.ournight.api_server.api.pos.controller

import com.ournight.api_server.api.pos.dto.CreateHallOrderRequest
import com.ournight.api_server.api.pos.dto.CreateHallOrderResponse
import com.ournight.api_server.api.pos.dto.HallOrderCardDto
import com.ournight.api_server.api.pos.dto.HallOrderItemDto
import com.ournight.api_server.api.pos.dto.HallOrderListResponse
import com.ournight.api_server.api.pos.dto.HallPaymentResponse
import com.ournight.api_server.api.pos.dto.HallServingResponse
import com.ournight.api_server.api.pos.dto.OrderCancelRequest
import com.ournight.api_server.api.pos.dto.OrderItemCancelRequest
import com.ournight.api_server.api.pos.dto.OrderItemDecreaseRequest
import com.ournight.api_server.domain.order.model.OrderCursor
import com.ournight.api_server.domain.order.model.OrderStatus
import com.ournight.api_server.domain.order.model.decodeCursor
import com.ournight.api_server.domain.order.model.encodeCursor
import com.ournight.api_server.domain.notification.service.PosStreamWatermarkService
import com.ournight.api_server.domain.order.service.OrderService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

@RestController
@RequestMapping("/api/pos/{storeId}/hall/orders")
class HallOrderController(
    private val orderService: OrderService,
    private val clock: Clock,
    private val posStreamWatermarkService: PosStreamWatermarkService,
) {
    // 1. 입금 확인 및 주문 확정
    @PostMapping("/{orderId}/confirm")
    fun confirmOrder(
        @PathVariable orderId: UUID,
        @PathVariable storeId: UUID,
    ): ResponseEntity<Void> {
        orderService.confirmOrder(storeId, orderId)
        return ResponseEntity.ok().build()
    }

    // 2. 서빙 완료 처리
    @PatchMapping("/{orderId}/items/{itemId}/serve")
    fun completeServingItem(
        @PathVariable itemId: UUID
    ): ResponseEntity<Void> {
        orderService.completeServingItem(itemId)
        return ResponseEntity.ok().build()
    }

    // 3. 주문 취소
    @PostMapping("/{orderId}/cancel")
    fun cancelOrder(
        @PathVariable storeId: UUID,
        @PathVariable orderId: UUID,
        @RequestBody request: OrderCancelRequest
    ): ResponseEntity<Void> {
        orderService.cancelOrder(orderId, request)
        return ResponseEntity.noContent().build()
    }

    // 4. 일부 아이템 취소
    @PostMapping("/{orderId}/order-items/{orderItemId}/cancel")
    fun cancelOrderItem(
        @PathVariable orderItemId: UUID,
        @RequestBody request: OrderItemCancelRequest
    ): ResponseEntity<Void> {
        orderService.cancelOrderItem(orderItemId, request)
        return ResponseEntity.noContent().build()
    }


    @PostMapping
    fun createHallOrder(
        @PathVariable storeId: UUID,
        @RequestBody request: CreateHallOrderRequest
    ): ResponseEntity<CreateHallOrderResponse> {
        val result = orderService.createHallOrder(
            storeId = storeId,
            request = request
        )

        return ResponseEntity.ok(
            CreateHallOrderResponse(
                orderId = result
            )
        )
    }

    @PatchMapping("/{orderId}/order-items/{orderItemId}/decrease")
    fun decreaseOrderItem(
        @PathVariable orderItemId: UUID,
        @RequestBody request: OrderItemDecreaseRequest
    ): ResponseEntity<Void> {
        orderService.decreaseOrderItem(orderItemId, request)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/pending-payments")
    fun getPendingPayments(
        @PathVariable storeId: UUID
    ): ResponseEntity<List<HallPaymentResponse>> {
        val watermark = posStreamWatermarkService.currentWatermark(storeId)
        val orders = orderService.getPendingPaymentOrders(storeId)
        return posStreamWatermarkService.okWithWatermark(watermark, orders)
    }

    @GetMapping("/pending-servings")
    fun getPendingServings(
        @PathVariable storeId: UUID
    ): ResponseEntity<List<HallServingResponse>> {
        val watermark = posStreamWatermarkService.currentWatermark(storeId)
        val items = orderService.getServingPendingItems(storeId)
        return posStreamWatermarkService.okWithWatermark(watermark, items)
    }

    private val ZONE: ZoneId = ZoneId.of("Asia/Seoul")

    @GetMapping
    fun listHallOrders(
        @PathVariable storeId: UUID,
        @RequestParam status: OrderStatus,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false, defaultValue = "10") limit: Int,
    ): ResponseEntity<HallOrderListResponse> {
        val watermark = posStreamWatermarkService.currentWatermark(storeId)

        val safeLimit = limit.coerceIn(1, 50)
        val decodedCursor: OrderCursor? = cursor?.let { decodeCursor(it) }

        val result = orderService.listHallOrders(
            storeId = storeId,
            status = status,
            cursor = decodedCursor,
            limit = safeLimit
        )

        val now = LocalDateTime.now(clock)

        val response = HallOrderListResponse(
            items = result.items.map { c ->
                HallOrderCardDto(
                    orderId = c.orderId,
                    tableId = c.tableId,
                    tableNumber = c.tableNumber,
                    tableOrderSequence = c.tableOrderSequence,
                    status = c.status,
                    totalPrice = c.totalAmount.toInt(),
                    createdAt = c.orderedAt.atZone(ZONE).toInstant(),
                    elapsedMinutes = Duration.between(c.orderedAt, now).toMinutes(),
                    items = c.items.map { i ->
                        HallOrderItemDto(
                            orderItemId = i.orderItemId,
                            menuId = i.menuId,
                            menuName = i.name,
                            qty = i.quantity,
                            status = i.status
                        )
                    }
                )
            },
            nextCursor = result.nextCursor?.let { encodeCursor(it) },
            hasNext = result.hasNext,
        )

        return posStreamWatermarkService.okWithWatermark(watermark, response)
    }

    @GetMapping("/by-ids")
    fun listHallOrdersByIds(
        @PathVariable storeId: UUID,
        @RequestParam ids: List<UUID>,
    ): ResponseEntity<HallOrderListResponse> {
        val watermark = posStreamWatermarkService.currentWatermark(storeId)
        val now = LocalDateTime.now(clock)
        val cards = orderService.listHallOrdersByIds(storeId, ids.distinct().take(100))

        val response = HallOrderListResponse(
            items = cards.map { c ->
                HallOrderCardDto(
                    orderId = c.orderId,
                    tableId = c.tableId,
                    tableNumber = c.tableNumber,
                    tableOrderSequence = c.tableOrderSequence,
                    status = c.status,
                    totalPrice = c.totalAmount.toInt(),
                    createdAt = c.orderedAt.atZone(ZONE).toInstant(),
                    elapsedMinutes = Duration.between(c.orderedAt, now).toMinutes(),
                    items = c.items.map { i ->
                        HallOrderItemDto(
                            orderItemId = i.orderItemId,
                            menuId = i.menuId,
                            menuName = i.name,
                            qty = i.quantity,
                            status = i.status
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
