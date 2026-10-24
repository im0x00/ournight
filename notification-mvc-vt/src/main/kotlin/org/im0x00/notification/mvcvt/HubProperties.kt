package org.im0x00.notification.mvcvt

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "app.hub")
data class HubProperties(
    val bufferCapacity: Int = 1000,
    val broadcastLoopTimeout: Duration = Duration.ofSeconds(30),
    val sseEmitterTtl: Long = 5 * 60 * 1000
)