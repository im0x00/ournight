package org.im0x00.notification.coroutine

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

@RestController
@RequestMapping("/api/pos/{storeId}/notifications")
class Endpoint(
    val hub: Hub
) {
    @PostMapping
    suspend fun subscribe(
        @PathVariable storeId: UUID,
        @RequestHeader(value = "Last-Event-Id", required = false) lastEventId: String?,
    ): Flow<ServerSentEvent<SseEvent>> = hub.register(storeId, lastEventId)
}