package com.ournight.api_server.api.customer.controller

import com.ournight.api_server.api.customer.dto.CartCommitRequest
import com.ournight.api_server.api.customer.dto.CartCommitResponse
import com.ournight.api_server.api.customer.dto.CartItemDeltaRequest
import com.ournight.api_server.api.customer.dto.CartMetaResponse
import com.ournight.api_server.api.customer.dto.CartSnapshotResponse
import com.ournight.api_server.domain.order.service.CartServiceImpl
import com.ournight.api_server.domain.session.service.SessionService
import com.ournight.api_server.domain.user.model.User
import org.springframework.http.CacheControl
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID
import com.ournight.api_server.api.customer.mapper.toResponse
import com.ournight.api_server.global.annotation.LoginUser

@RestController
@RequestMapping("/api/customer/tables/{tableId}/cart")
class CustomerCartController(
    private val cartServiceImpl: CartServiceImpl,
    private val sessionService: SessionService,
) {

    @GetMapping("/meta")
    fun getCartMeta(
        @LoginUser user: User,
        @PathVariable("tableId") tableId: UUID,
    ): ResponseEntity<CartMetaResponse> {
        val meta = cartServiceImpl.getMeta(tableId).toResponse()
        return ResponseEntity
            .ok()
            .cacheControl(CacheControl.noCache())
            .body(meta)
    }

    @GetMapping("/snapshot")
    fun getSnapshot(
        @LoginUser user: User,
        @PathVariable("tableId") tableId: UUID,
    ): ResponseEntity<CartSnapshotResponse> {
        val snapshot = cartServiceImpl.getSnapshot(tableId).toResponse()
        return ResponseEntity.ok(snapshot)
    }

    @PostMapping("/items/delta")
    fun addItemDelta(
        @LoginUser user: User,
        @PathVariable("tableId") tableId: UUID,
        @RequestBody request: CartItemDeltaRequest
    ): ResponseEntity<CartMetaResponse> {
        sessionService.requireOrderableParticipant(tableId, user.id!!)
        val updatedMeta = cartServiceImpl.addDelta(tableId, request.menuId, request.delta).toResponse()
        return ResponseEntity.ok(updatedMeta)
    }

    @PostMapping("/commit")
    fun commitToOrder(
        @LoginUser user: User,
        @PathVariable("tableId") tableId: UUID,
        @RequestBody request: CartCommitRequest
    ): ResponseEntity<CartCommitResponse> {
        sessionService.requireOrderableParticipant(tableId, user.id!!)
        val orderId = cartServiceImpl.commitCart(
            tableId = tableId,
            commitId = request.commitId,
            seenVersion = request.seenVersion,
            userId = user.id!!
        )
        return ResponseEntity.ok(CartCommitResponse(orderId))
    }

    //idempotency 보장 목적의 조회
    @GetMapping("/commit/{commitId}")
    fun checkCommit(
        @LoginUser user: User,
        @PathVariable("tableId") tableId: UUID,
        @PathVariable("commitId") commitId: UUID,
    ): ResponseEntity<CartCommitResponse> {
        val orderId = cartServiceImpl.getCommitResult(tableId, commitId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(CartCommitResponse(orderId))
    }

}
