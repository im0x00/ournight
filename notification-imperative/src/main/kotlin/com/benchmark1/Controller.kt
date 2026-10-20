package org.im0x00.com.benchmark1

import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.UUID

@RestController
@RequestMapping("/api/pos/{storeId}/notifications")
class Controller(
    private val hub: Hub
) {
    @PostMapping()
    fun subscribe(
        @PathVariable storeId: UUID,
        @RequestHeader(value = "Last-Event-ID", required = false) lastEventId: String?
    ): SseEmitter {
        return hub.subscribe(storeId, lastEventId)
    }
}