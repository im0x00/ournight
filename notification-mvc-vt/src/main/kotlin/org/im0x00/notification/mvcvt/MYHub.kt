package org.im0x00.notification.mvcvt

import jakarta.annotation.PreDestroy
import org.im0x00.notification.mvcvt.redis.RedisReader
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlin.time.toKotlinDuration
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.TimeUnit

private val channelLog = LoggerFactory.getLogger(MYStoreChannel::class.java)
private val hubLog = LoggerFactory.getLogger(MYHub::class.java)

// 내가 직접 한거는 MY prefix 추가
@Component
class MYHub(
    val properties: HubProperties,
    val reader: RedisReader,
) {
    val channels = ConcurrentHashMap<UUID, MYStoreChannel>()

    fun subscribe(
        storeId: UUID,
        lastEventId: String?,
    ): SseEmitter {

        val channel = constructOrGetChannel(storeId)

        val emitter = SseEmitter(properties.sseEmitterTtl)
        emitter.apply {
            onCompletion { channel.removeEmitter(emitter) }
            onTimeout { channel.removeEmitter(emitter) }
            onError { channel.removeEmitter(emitter) }
        }

        synchronized(channel) {
            if (!channel.replay(emitter, lastEventId)) {
                return emitter
            }
            if (!channel.tryAdd(emitter)) {
                val newChannel = constructOrGetChannel(storeId)
                newChannel.tryAdd(emitter)
            }
        }

        return emitter
    }

    @PreDestroy
    fun destroy() {
        val all = channels.values.toList()
        all.forEach { it.close() }
        all.forEach { it.awaitReader(Duration.ofSeconds(3)) }
        hubLog.info("ExecutorService cleanly terminated.")
    }

    private fun constructOrGetChannel(storeId: UUID) = channels.computeIfAbsent(storeId) {
        val chan = MYStoreChannel(properties, storeId, reader).apply {
            onDestroy = {
                channels.remove(storeId, this)
                hubLog.info("StoreChannel for {} removed from Map.", storeId)
            }
        }
        chan.broadcast()
        chan
    }

}

class MYStoreChannel(
    val properties: HubProperties,
    val storeId: UUID,
    val reader: RedisReader,
    var onDestroy: ((storeId: UUID) -> Unit)? = null
) {
    val subscribers: CopyOnWriteArrayList<SseEmitter> = CopyOnWriteArrayList()
    private var executor: ExecutorService = Executors.newVirtualThreadPerTaskExecutor()
    val stop = AtomicBoolean(false)
    private var last: StreamId
    val buffer: ReplayBuffer

    init {
        val startId = reader.fetchLatest(storeId).lastOrNull()?.id ?: StreamId.ZERO
        buffer = ReplayBuffer(properties.bufferCapacity, startId)
        last = startId
    }

    fun removeEmitter(emitter: SseEmitter) {
        subscribers.remove(emitter)
    }

    @Synchronized
    fun tryAdd(emitter: SseEmitter): Boolean {
        if (stop.get()) return false
        subscribers.add(emitter)
        return true
    }

    @Synchronized
    fun close() {
        if (stop.getAndSet(true)) return
        executor.shutdown()
        subscribers.forEach { s -> runCatching { s.complete() } }
        subscribers.clear()
        onDestroy?.invoke(storeId)
    }

    fun broadcast() {
        executor.submit {
            try {
                var emptySince: TimeMark? = null
                while (!stop.get()) {
                    val data = reader.poll(storeId, last)
                    synchronized(this) {
                        data.forEach { d ->
                            buffer.add(d)
                            last = d.id
                            subscribers.forEach { subscriber ->
                                try {
                                    subscriber.send(
                                        SseEmitter.event()
                                            .id(last.toString())
                                            .data(d)
                                    )
                                } catch (e: Exception) {
                                    channelLog.warn("remove + {} (send failed: {})", subscriber, e.toString())
                                    removeEmitter(subscriber)
                                }
                            }
                        }
                    }
                    synchronized(this) {
                        if (subscribers.isEmpty()) {
                            if (emptySince == null) {
                                emptySince = TimeSource.Monotonic.markNow()
                            } else if (emptySince.elapsedNow() > properties.broadcastLoopTimeout.toKotlinDuration()) {
                                channelLog.info("time_wait for 30 seconds in store: {}", storeId)
                                close()
                            }
                        } else {
                            emptySince = null
                        }
                    }
                }
            } catch (e: Exception) {
                channelLog.error("Error", e)
            } finally {
                close()
                channelLog.info("reading loop(broadcast) is ended: {}", storeId)
            }
        }
    }

    fun replay(
        emitter: SseEmitter,
        lastEventId: String?,
    ): Boolean {
        // header 없음 → 지금부터(R의 위치) / 이상한 값 → null → RELOAD / buffer보다 오래됨 → since가 null → RELOAD
        val from = if (lastEventId == null) last else StreamId.parseOrNull(lastEventId)
        val events = from?.let { buffer.since(it) }
        if (events == null) {
            emitter.send(SseEvent.reloadRequired(storeId, StreamId.ZERO))
            emitter.complete()
            return false
        }
        events.forEach { e ->
            emitter.send(
                SseEmitter.event()
                    .id(e.id.toString())
                    .data(e)
            )
        }
        return true
    }


    fun awaitReader(timeout: Duration) = executor.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS)

}