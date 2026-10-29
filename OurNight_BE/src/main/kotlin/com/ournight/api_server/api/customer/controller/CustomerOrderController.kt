package com.ournight.api_server.api.customer.controller

import com.ournight.api_server.api.customer.dto.UserOrderResponse
import com.ournight.api_server.domain.order.service.OrderService
import com.ournight.api_server.domain.user.model.User
import com.ournight.api_server.global.annotation.LoginUser
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/customer/stores/{storeId}/orders")
class CustomerOrderController(
    private val orderService: OrderService,
) {


    // 입금 완료 ("입금했어요" 버튼)
    @PostMapping("/{orderId}/payment-verification")
    fun notifyPaymentComplete(
        @LoginUser user: User,
        @PathVariable("storeId") storeId: UUID,
        @PathVariable orderId: UUID
    ): ResponseEntity<Void> {
        orderService.notifyPaymentComplete(orderId, user.id!!)
        return ResponseEntity.ok().build()
    }

    // 내 주문 내역 조회
    @GetMapping
    fun getMyOrders(
        @LoginUser user: User,
        @PathVariable storeId: UUID,
        @RequestParam(required = false) tableId: UUID?,
    ): ResponseEntity<List<UserOrderResponse>> {
        val response = orderService.listUserOrders(user.id!!, storeId, tableId)
        return ResponseEntity.ok(response)
    }

}
