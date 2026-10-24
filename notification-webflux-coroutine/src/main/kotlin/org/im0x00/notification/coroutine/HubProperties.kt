package org.im0x00.notification.coroutine

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "app.hub")   // same prefix as mvc-vt
data class HubProperties(
    val eventPerSecond: Int = 8,
    val timeoutPerClient: Int = 4,
    val broadcastLoopTimeout: Duration = Duration.ofSeconds(30),
    val bufferCapacity: Int = 1000,
)
