package org.im0x00.notification.reactor

import org.im0x00.notification.reactor.redis.RedisPosEventReader
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import reactor.core.Disposable
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import sun.jvm.hotspot.runtime.PerfMemory.capacity
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

// How events are read (topology). Connection management (plain vs pool) is a separate axis:
// spring.data.redis.lettuce.pool.enabled — see application.yml.
interface PosEventSource {
    fun subscribe(storeId: UUID, lastEventId: String?): Flux<SseEvent>
}

@Component
class StoreEventHub(private val source: PosEventSource) {
    fun subscribe(storeId: UUID, lastEventId: String?) = source.subscribe(storeId, lastEventId)
}

// option 1 (pool off) / option 2 (pool on): one StreamReceiver poll loop per SSE subscription
@Component
@ConditionalOnProperty(name = ["notification.reader"], havingValue = "per-subscription", matchIfMissing = true)
class PerSubscriptionPosEventSource(private val reader: RedisPosEventReader) : PosEventSource {
    override fun subscribe(storeId: UUID, lastEventId: String?) = reader.read(storeId, lastEventId)
}

// option 3: one read per store + broadcast
@Component
@ConditionalOnProperty(name = ["notification.reader"], havingValue = "store-broadcast")
class StoreBroadcastPosEventSource(private val reader: RedisPosEventReader) : PosEventSource {

    private companion object {
        const val CAPACITY = 1000
        val GRACE = Duration.ofSeconds(30)
    }

    private val channels = ConcurrentHashMap<UUID, StoreChannel>()

    private class StoreChannel(
        storeId: UUID,
        startingId: String?,
        capacity: Int,
        reader: RedisPosEventReader,
    ) {
        // one stream read per store, shared by every SSE subscriber of that store.
        // replay buffer: a new subscriber gets the buffered events and then live ones with no gap in between
        private val sink = Sinks.many().replay().limit<SseEvent>(capacity)

        @Volatile private var latestId: StreamId = StreamId.ZERO
        @Volatile private var floorId: StreamId? = StreamId.parse(startingId)

        @Volatile private var subscription: Disposable? = null

        val subscribers = AtomicInteger(0)
        private val retained = ArrayDeque<StreamId>(capacity+1)

        init {
            subscription = reader.read(storeId, lastEventId = startingId)
                .subscribe(::onEvent, { sink.tryEmitError(it) }, { sink.tryEmitComplete() },)
        }

        private fun onEvent(event: SseEvent) {
            val id = StreamId.parse(event.id)!!
            sink.tryEmitNext(event)

            retained.addLast(id)
            if (retained.size > capacity) {
                floorId = retained.removeFirst()
            }
            latestId = id
        }

        fun stream(storeId: UUID, lastEventId: String?): Flux<SseEvent> {
            val from = lastEventId?.let { StreamId.parse(it) } ?: latestId

            return sink.asFlux()
                .switchOnFirst { signal, events ->
                    val oldest = signal.get()
                    val gap = lastEventId != null && oldest != null && floorId!! > from

                    if (gap) {
                        Flux.just(reloadRequired(storeId, from))
                    } else {
                        events.filter { StreamId.parse(it.id)!! > from }
                    }
                }
        }

        fun close() {
            subscription?.dispose()
            sink.tryEmitComplete()
        }

        private fun reloadRequired(storeId: UUID, from: StreamId) = SseEvent(
            id = from.toString(),
            eventId = UUID.randomUUID().toString(),
            storeId = storeId,
            type = "RELOAD_REQUIRED",
            payload = "{}",
            timestamp = System.currentTimeMillis(),
        )
    }

    override fun subscribe(storeId: UUID, lastEventId: String?): Flux<SseEvent> {
        val channel = channels.compute(storeId) { _, existing ->
            (existing ?: StoreChannel(storeId, lastEventId, CAPACITY, reader))
                .also{ it.subscribers.incrementAndGet()}
        }!!

        return channel
            .stream(storeId, lastEventId)
            .doFinally { release(storeId, channel) }
    }

    private fun release(storeId: UUID, channel: StoreChannel) {
        if (channel.subscribers.decrementAndGet() > 0) {
            return
        }
        channels.computeIfPresent(storeId) { _, current ->
            if (current === channel && current.subscribers.get() == 0) {
                current.close()
                null
            } else current
        }
    }
}
