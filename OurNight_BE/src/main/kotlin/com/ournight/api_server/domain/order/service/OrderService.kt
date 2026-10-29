package com.ournight.api_server.domain.order.service

import com.ournight.api_server.api.customer.dto.UserOrderResponse
import com.ournight.api_server.api.pos.dto.CreateHallOrderRequest
import com.ournight.api_server.api.pos.dto.HallPaymentResponse
import com.ournight.api_server.api.pos.dto.HallServingResponse
import com.ournight.api_server.api.pos.dto.OrderCancelRequest
import com.ournight.api_server.api.pos.dto.OrderItemCancelRequest
import com.ournight.api_server.api.pos.dto.OrderItemDecreaseRequest
import com.ournight.api_server.api.pos.dto.OrderRoundResponse
import com.ournight.api_server.domain.order.model.CartItem
import com.ournight.api_server.domain.order.model.OrderCursor
import com.ournight.api_server.domain.order.model.OrderStatus
import com.ournight.api_server.domain.order.view.HallOrderListResult
import com.ournight.api_server.domain.order.view.HallOrderCard
import com.ournight.api_server.domain.order.view.KitchenOrderCard
import com.ournight.api_server.domain.order.view.KitchenOrderListResult
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class OrderService(
    private val creation: OrderCreationService,
    private val query: OrderQueryService,
    private val command: OrderCommandService,
) {
    // create
    fun createOrderFromCart(
        tableId: UUID,
        userId: UUID,
        commitId: UUID,
        items: List<CartItem>,
        cartVersion: Int
    ): UUID = creation.createOrderFromCart(tableId, userId, commitId, items, cartVersion)

    fun createHallOrder(storeId: UUID, request: CreateHallOrderRequest): UUID =
        creation.createHallOrder(storeId, request)

    // query
    fun listKitchenOrders(storeId: UUID, status: OrderStatus, cursor: OrderCursor?, limit: Int): KitchenOrderListResult =
        query.listKitchenOrders(storeId, status, cursor, limit)

    fun listHallOrders(storeId: UUID, status: OrderStatus, cursor: OrderCursor?, limit: Int): HallOrderListResult =
        query.listHallOrders(storeId, status, cursor, limit)

    fun listHallOrdersByIds(storeId: UUID, orderIds: List<UUID>): List<HallOrderCard> =
        query.listHallOrdersByIds(storeId, orderIds)

    fun listKitchenOrdersByIds(storeId: UUID, orderIds: List<UUID>): List<KitchenOrderCard> =
        query.listKitchenOrdersByIds(storeId, orderIds)

    fun getPendingPaymentOrders(storeId: UUID): List<HallPaymentResponse> =
        query.getPendingPaymentOrders(storeId)

    fun getServingPendingItems(storeId: UUID): List<HallServingResponse> =
        query.getServingPendingItems(storeId)

    fun listUserOrders(userId: UUID, storeId: UUID, tableId: UUID? = null): List<UserOrderResponse> =
        query.listUserOrders(userId, storeId, tableId)

    fun findRoundsByTable(storeId: UUID, tableId: UUID): List<OrderRoundResponse> =
        query.findRoundsByTable(storeId, tableId)


    // command
    fun notifyPaymentComplete(orderId: UUID, userId: UUID) =
        command.notifyPaymentComplete(orderId)

    fun confirmOrder(storeId: UUID, orderId: UUID) =
        command.confirmOrder(storeId, orderId)

    fun completeCookingItem(orderItemId: UUID) =
        command.completeCookingItem(orderItemId)

    fun completeServingItem(orderItemId: UUID) =
        command.completeServingItem(orderItemId)

    fun decreaseOrderItem(orderItemId: UUID, request: OrderItemDecreaseRequest) =
        command.decreaseOrderItem(orderItemId, request)

    fun cancelOrderItem(orderItemId: UUID, request: OrderItemCancelRequest) =
        command.cancelOrderItem(orderItemId, request)

    fun cancelOrder(orderId: UUID, request: OrderCancelRequest) =
        command.cancelOrder(orderId, request.clientRequestId)
}
