package com.ournight.api_server.api.pos.controller

import com.ournight.api_server.domain.notification.service.PosStreamChangeService
import com.ournight.api_server.domain.notification.service.SseOrchestrator
import com.ournight.api_server.domain.store.application.StoreService
import com.ournight.api_server.domain.user.model.User
import com.ournight.api_server.global.annotation.LoginUser
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.UUID

@RestController
@RequestMapping("/api/pos/{storeId}/notifications")
class PosNotificationController(
    private val storeService: StoreService,
    private val sseOrchestrator: SseOrchestrator,
    private val posStreamChangeService: PosStreamChangeService,
) {
    @GetMapping("/ticket")
    fun getTicket(
        @LoginUser user: User,
        @PathVariable storeId: UUID,
    ): ResponseEntity<Map<String, String>> {
        storeService.assertHost(storeId, user.id!!)
        val ticket = sseOrchestrator.createTicket(storeId)
        return ResponseEntity.ok(mapOf("ticket" to ticket))
    }

    @GetMapping("/changes")
    fun getChangesSince(
        @LoginUser user: User,
        @PathVariable storeId: UUID,
        @RequestParam since: String?,
        @RequestParam(required = false, defaultValue = "500") limit: Int,
    ) = run {
        storeService.assertHost(storeId, user.id!!)
        ResponseEntity.ok(
            posStreamChangeService.collectChanges(
                storeId = storeId,
                sinceEventId = since,
                maxEvents = limit.coerceIn(1, 1000),
            ),
        )
    }

    @GetMapping("/subscribe", produces = ["text/event-stream"])
    fun subscribe(
        @PathVariable storeId: UUID,
        @RequestParam ticket: String,
        @RequestParam(required = false) connectionId: String?,
        @RequestParam(required = false) lastEventId: String?,
        @RequestHeader(value = "Last-Event-ID", required = false) lastEventIdHeader: String?
    ): SseEmitter {
        return sseOrchestrator.subscribe(
            expectedStoreId = storeId,
            ticket = ticket,
            connectionId = connectionId,
            lastEventId = lastEventId?.takeIf { it.isNotBlank() }
                ?: lastEventIdHeader?.takeIf { it.isNotBlank() }
        )
    }
}
