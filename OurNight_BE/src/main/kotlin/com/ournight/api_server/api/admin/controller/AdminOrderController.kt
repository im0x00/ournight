package com.ournight.api_server.api.admin.controller

import com.ournight.api_server.domain.order.repository.OrderRepository
import com.ournight.api_server.domain.order.view.AdminOrderSummaryView
import com.ournight.api_server.domain.user.model.User
import com.ournight.api_server.global.annotation.LoginUser
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/orders")
class AdminOrderController(
    private val orderRepository: OrderRepository,
) {

    /** 전체 스토어 주문 요약(최신순). ADMIN 만 */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Suppress("UNUSED_PARAMETER")
    fun listAllOrders(
        @LoginUser user: User,
        @PageableDefault(size = 50, sort = ["orderedAt"], direction = Sort.Direction.DESC)
        pageable: Pageable,
    ): ResponseEntity<Page<AdminOrderSummaryView>> {
        return ResponseEntity.ok(orderRepository.findAllSummariesForAdmin(pageable))
    }
}
