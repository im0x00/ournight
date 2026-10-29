package com.ournight.api_server.domain.notification.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class SseRuntimeProperties(
    @Value("\${pos.sse.outbound-queue-size:500}")
    val outboundQueueSize: Int,

    @Value("\${pos.sse.replay-queue-size:500}")
    val replayQueueSize: Int,
)
