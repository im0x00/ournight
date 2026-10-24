package org.im0x00.notification.reactor

import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import java.util.UUID

@RestController
@RequestMapping("/api/pos/{storeId}/notifications")
class Endpoint(
    private val hub: StoreEventHub
) {

    @PostMapping
    fun subscribe(
        @PathVariable storeId: UUID,
        @RequestHeader(value = "Last-Event-ID", required = false) lastEventId: String?
    ): Flux<ServerSentEvent<SseEvent>> {
        return hub.subscribe(storeId, lastEventId)
            .map { event ->
                ServerSentEvent
                    .builder(event)
                    .id(event.id)
                    .event(event.type)
                    .build()
            }
    }
}