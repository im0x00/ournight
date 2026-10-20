package org.im0x00.com.benchmark1

import jakarta.annotation.PreDestroy
import org.im0x00.com.benchmark1.redis.RedisReader
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.TimeMark
import kotlin.time.toKotlinDuration
import kotlin.time.TimeSource

private val channelLog = LoggerFactory.getLogger(StoreChannel::class.java)
private val hubLog = LoggerFactory.getLogger(Hub::class.java)

class StoreChannel(
    private val properties: HubProperties,
    val storeId: UUID,
    val reader: RedisReader,
    private val executor: ExecutorService,
    private val onDestroy: (UUID) -> Unit,
) {
    private val subscribers: CopyOnWriteArrayList<SseEmitter> = CopyOnWriteArrayList()
    private val startId = reader.fetchLatest(storeId).lastOrNull()?.id ?: StreamId.ZERO
    private val buffer: ReplayBuffer = ReplayBuffer(properties.bufferCapacity, startId)
    private var reading: AtomicBoolean = AtomicBoolean(true)
    private var closed = false


    fun removeEmitter(emitter: SseEmitter) = subscribers.remove(emitter)

    fun broadcast() {
        executor.execute {
            try {
                var last = startId
                var emptySince: TimeMark? = null

                while (reading.get()) {
                    val data = reader.poll(storeId, last)

                    synchronized(this) {
                        data.forEach { d ->
                            last = StreamId.parse(d.id.toString())
                            buffer.add(d)
                        }
                        subscribers.forEach { emitter ->
                            data.forEach {
                                try {
                                    sendToEmitter(emitter, it)
                                } catch (e: Exception) {
                                    channelLog.warn("remove + {} (send failed: {})", emitter, e.toString())
                                    removeEmitter(emitter)
                                }
                            }
                        }
                    }

                    if (subscribers.isNotEmpty()) {
                        emptySince = null
                    } else if (emptySince == null) {
                        emptySince = TimeSource.Monotonic.markNow()
                    } else if (emptySince.elapsedNow() >= properties.broadcastLoopTimeout.toKotlinDuration() && tryClose()) {
                        reading.set(false)
                        channelLog.info("time_wait for 30 seconds in store: {}", storeId)
                        break
                    }
                }
            } finally {
                if (!closed) {
                    onDestroy(storeId)
                }
                channelLog.info("reading loop(broadcast) is ended: {}", storeId)
            }
        }
    }

    fun getReplayList(lastEventId: String?): List<SseEvent>? {
        if (lastEventId == null) return emptyList()                    // header 없음 → 지금부터
        val from = StreamId.parseOrNull(lastEventId) ?: return null    // 이상한 id → RELOAD
        return buffer.since(from)                                      // 놓친 것, 너무 오래됐으면 null → RELOAD
    }

    private fun sendToEmitter(emitter: SseEmitter, event: SseEvent) {
        emitter.send(
            SseEmitter.event()
                .id(event.id.toString())
                .data(event)
                .build()
        )
    }

    @Synchronized
    fun tryAdd(e: SseEmitter): Boolean {
        if (closed) return false
        subscribers.add(e)
        return true
    }

    @Synchronized
    fun tryClose(): Boolean {
        if (subscribers.isNotEmpty()) return false
        closed = true
        onDestroy(storeId)
        return true
    }

    @Synchronized
    fun shutdown() { // 앱 종료 or redisError
        closed = true
        subscribers.forEach { it.complete() }
        reading.set(false)
    }

}

@Component
class Hub(
    val reader: RedisReader,
    val properties: HubProperties
) {
    val channels: ConcurrentHashMap<UUID, StoreChannel> = ConcurrentHashMap()
    private val executor: ExecutorService = Executors.newVirtualThreadPerTaskExecutor()

    fun subscribe(
        storeId: UUID,
        lastEventId: String?
    ): SseEmitter {
        val channel = constructChannel(storeId)

        val emitter = SseEmitter(properties.sseEmitterTtl)
            .apply {
                onCompletion { channel.removeEmitter(this) }
                onTimeout { channel.removeEmitter(this) }
                onError { channel.removeEmitter(this) }
            }

        synchronized(channel) {
            if (!replay(channel, emitter, lastEventId)) {
                return emitter
            }
            if (!channel.tryAdd(emitter)) {
                val newChannel = constructChannel(storeId)
                replay(newChannel, emitter, lastEventId)
                newChannel.tryAdd(emitter)
            }
        }

        return emitter
    }

    @PreDestroy
    fun destroy() {
        hubLog.info("Initiating ExecutorService shutdown...")

        channels.values.forEach { it.shutdown() }
        executor.shutdown()

        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                hubLog.warn("Tasks did not complete in time. Forcing shutdownNow()...")
                executor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            executor.shutdownNow()
            Thread.currentThread().interrupt()
        }
        hubLog.info("ExecutorService cleanly terminated.")
    }

    private fun constructChannel(storeId: UUID): StoreChannel {
        return channels.computeIfAbsent(storeId) { storeId ->
            lateinit var storeChannel: StoreChannel
            storeChannel = StoreChannel(properties, storeId, reader, executor) { storeId ->
                storeChannel.shutdown()
                channels.remove(storeId, storeChannel)
                hubLog.info("StoreChannel for {} removed from Map.", storeId)
            }
            storeChannel.broadcast()
            storeChannel
        }
    }

    private fun replay(channel: StoreChannel, emitter: SseEmitter, lastEventId: String?): Boolean {
        channel.getReplayList(lastEventId) ?.let { events ->
            events.forEach { event ->
                emitter.send(
                    event()
                        .id(event.id.toString())
                        .data(event)
                        .build()
                )
            }
        } ?: run {
            emitter.send(
                SseEvent.reloadRequired(channel.storeId, StreamId.ZERO)
            )
            emitter.complete()
            return false
        }
        return true
    }

}