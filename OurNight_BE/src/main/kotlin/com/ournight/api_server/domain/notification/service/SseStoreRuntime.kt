package com.ournight.api_server.domain.notification.service

import org.slf4j.LoggerFactory
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class SseStoreRuntime(
    val chanKey: String,
    val broadcastQueue: ArrayBlockingQueue<SseMsg>,
    val connectionRuntimes: ConcurrentHashMap<SseEmitter, ConnectionRuntime>
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun accumulateMsg(msg: SseMsg): Boolean =
        broadcastQueue.offer(msg, 100, TimeUnit.MILLISECONDS)

    fun enqueueLive(msg: SseMsg): List<SseEmitter> {
        val failedEmitters = mutableListOf<SseEmitter>()

        connectionRuntimes.forEach { (emitter, runtime) ->
            if (!runtime.enqueueLive(chanKey, msg)) {
                failedEmitters.add(emitter)
            }
        }

        return failedEmitters
    }
    fun pollOutbound(emitter: SseEmitter, pollSize: Long, timeUnit: TimeUnit): SseMsg? {
        val connectionRuntime = connectionRuntimes[emitter] ?: return null
        return connectionRuntime.pollOutbound(pollSize, timeUnit)
    }

    fun drainCatchUpBuffer(emitter: SseEmitter, limitPerEmitter: Int): List<SseMsg> {
        val connectionRuntime = connectionRuntimes[emitter] ?: return emptyList()
        return connectionRuntime.drainCatchUpBuffer(limitPerEmitter)
    }


    fun sendToEmitter(emitter: SseEmitter, name: String, data: String, id: String?): Boolean {
        if (!connectionRuntimes.containsKey(emitter)) return false

        var success = true
        connectionRuntimes[emitter]?.let { _ ->
            runCatching {
                val event = SseEmitter.event()
                    .name(name)
                    .data(data)
                    .reconnectTime(2000)
                if (id != null) event.id(id)
                emitter.send(event)
            }.onFailure { e ->
                logger.debug("Failed to send SSE event: ${e.message}")
                success = false
                emitter.completeWithError(e)
            }
        }
        return success
    }

    fun requireReload(emitter: SseEmitter): Boolean {
        return sendToEmitter(emitter, "RELOAD_REQUIRED", "{}", null)
    }

}
