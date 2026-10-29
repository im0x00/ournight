package com.ournight.api_server.domain.notification.service

import org.slf4j.LoggerFactory
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

enum class ConnectionRuntimeState {
    STARTED,  // must handle the catchupBuffer
    ACTIVE,
    TERMINATING,
    CLOSED
}

class ConnectionRuntime (
    private var state: ConnectionRuntimeState = ConnectionRuntimeState.STARTED,
    val connectionId: String,
    val outboundQueue: ArrayBlockingQueue<SseMsg>,
    var catchUpBuffer: ArrayBlockingQueue<SseMsg>,
    var emitterTasks: Future<*>?
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val lock = ReentrantLock()
    private val stateChanged = lock.newCondition()

    fun assignTask(task: Future<*>) = lock.withLock {
        check (
            state in setOf(
                ConnectionRuntimeState.ACTIVE,
                ConnectionRuntimeState.STARTED,
            )
        )
        emitterTasks = task
    }

    fun open() = lock.withLock {
    check(state == ConnectionRuntimeState.STARTED) {
            "Cannot start from $state"
        }
        state = ConnectionRuntimeState.ACTIVE
        stateChanged.signal()
    }

    fun close() = lock.withLock {
        if (state == ConnectionRuntimeState.CLOSED) {
            return
        } else {
            logger.warn("ConnectionRuntime is closed from {}", state)
        }

        state = ConnectionRuntimeState.CLOSED
        outboundQueue.clear()
        catchUpBuffer.clear()
        emitterTasks?.cancel(false)
        stateChanged.signal()
    }

    fun enqueueOutbound(chanKey:String, msg: SseMsg): Boolean {
        val overflowed = lock.withLock {
            check(state == ConnectionRuntimeState.ACTIVE) {
                "impossible from $state"
            }
            if (outboundQueue.offer(msg)) return@withLock false
            else {
                state = ConnectionRuntimeState.TERMINATING
                true
            }
        }

        if (!overflowed) return true

        logger.warn(
            "[SSE-SLOW-CONSUMER] storeId={} connectionId={} outbound queue overflow",
            chanKey,
            connectionId
        )
        return false
    }

    fun enqueueLive(chanKey:String, msg: SseMsg): Boolean = lock.withLock {
        if (state == ConnectionRuntimeState.ACTIVE) {
            return@withLock enqueueOutbound(chanKey, msg)
        }
        if (state == ConnectionRuntimeState.STARTED) {
            if (!catchUpBuffer.offer(msg)) {
                return@withLock false
            }
            return@withLock true
        }
        false
    }

    fun drainCatchUpBuffer(limitPerEmitter: Int): List<SseMsg> = lock.withLock {
        when (state) {
            ConnectionRuntimeState.ACTIVE -> {
                return@withLock emptyList()
            }
            ConnectionRuntimeState.TERMINATING,
            ConnectionRuntimeState.CLOSED -> {
                error("Cannot drain from $state")
            }
            else -> Unit
        }

        val buffer = catchUpBuffer ?: return emptyList()
        val drained = ArrayList<SseMsg>(limitPerEmitter)

        buffer.drainTo(drained, limitPerEmitter)

        if (catchUpBuffer.size == 0) {
            state = ConnectionRuntimeState.ACTIVE
            stateChanged.signal()
        }
        return drained
    }

    fun pollOutbound(pollSize: Long, timeUnit: TimeUnit): SseMsg? {
        check(state == ConnectionRuntimeState.ACTIVE) {
            "Cannot poll from $state"
        }
        return outboundQueue.poll(pollSize, timeUnit)
    }

    fun awaitUntilActive(): Boolean {
        lock.lock()
        try {
            while (state != ConnectionRuntimeState.ACTIVE) {
                if (state == ConnectionRuntimeState.TERMINATING || state == ConnectionRuntimeState.CLOSED) {
                    return false
                }
                stateChanged.await()
            }
        } finally {
            lock.unlock()
        }
        return true
    }

}
