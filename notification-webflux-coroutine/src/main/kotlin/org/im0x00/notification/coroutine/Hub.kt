package org.im0x00.notification.coroutine

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.time.delay
import kotlinx.coroutines.time.withTimeoutOrNull
import kotlinx.coroutines.withTimeoutOrNull
import org.im0x00.notification.coroutine.StreamId.Companion.parseOrNull
import org.springframework.boot.web.server.context.WebServerGracefulShutdownLifecycle
import org.springframework.context.SmartLifecycle
import org.springframework.http.codec.ServerSentEvent
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlin.time.toKotlinDuration

sealed interface Msg {
    data class Join(
        val storeId: UUID,
        val channel: Channel<ServerSentEvent<SseEvent>>,
        val lastEventId: String?
    ): Msg
    data class Leave(
        val channel: Channel<ServerSentEvent<SseEvent>>
    ): Msg
    data class Event(
        val event: SseEvent
    ): Msg
    data object Tick: Msg
    data object ReaderDied: Msg
}

@Component
class Hub(
    private val redis: RedisService,
    private val properties: HubProperties
): SmartLifecycle {
    val stores = ConcurrentHashMap<UUID, StoreHub>()
    private val hubScope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    val parentHubJob = hubScope.coroutineContext[Job]
    private var running = false

    suspend fun register(
        storeId: UUID,
        lastEventId: String?,
    ): Flow<ServerSentEvent<SseEvent>> {

        var storeHub = ownerOf(storeId)

        val channel = Channel<ServerSentEvent<SseEvent>>(
            properties.eventPerSecond * properties.timeoutPerClient
        )
        runCatching { storeHub.send(Msg.Join(storeId, channel, lastEventId)) }
            .onFailure {
                storeHub = ownerOf(storeId)
                storeHub.send(Msg.Join(storeId, channel, lastEventId))
            }

        return channel.receiveAsFlow()
            .onCompletion {
                println("${who()} store=$storeId onCompletion")
                runCatching { storeHub.send(Msg.Leave(channel)) }
            }
    }

    override fun getPhase(): Int {
        return WebServerGracefulShutdownLifecycle.SMART_LIFECYCLE_PHASE + 1
    }
    override fun stop() {
        hubScope.cancel()
        runBlocking { withTimeoutOrNull(5_000) { parentHubJob?.join() } }   // 다 멈출 때까지, 최대 5초
        running = false
        println("hub shutdown done")
    }
    override fun start() {
        running = true
    }
    override fun isRunning(): Boolean = running

    private fun ownerOf(storeId: UUID) = stores.computeIfAbsent(storeId) {
        val storeScope = CoroutineScope(hubScope.coroutineContext + Job(parent = parentHubJob))
        StoreHub(storeId, storeScope, redis, properties) { me ->
            val removed = stores.remove(storeId, me)
            println("removed store=$storeId ok=$removed")   // log: hub-check C2
        }
    }

}

class StoreHub(
    val storeId: UUID,
    val scope: CoroutineScope,
    val redis: RedisService,
    val properties: HubProperties,
    val onDestroy: (StoreHub) -> Unit
) {

    private val inbox = Channel<Msg>(Channel.UNLIMITED)
    private val subscribers = mutableMapOf<Channel<ServerSentEvent<SseEvent>>, StreamId>()
    lateinit var last: StreamId
    private lateinit var buffer: ReplayBuffer
    var idleSince: TimeMark? = null

    val handlingInbox: Job
    val healthCheck: Job
    lateinit var reading: Job

    init {
        handlingInbox = scope.launch(CoroutineName("owner-$storeId")) {
            try {
                val start = redis.fetchLatestId(storeId) ?: StreamId.ZERO
                last = start
                buffer = ReplayBuffer(properties.bufferCapacity, last)
                reading = scope.launch(CoroutineName("owner-$storeId - reading loop")) {
                    var cursor = start
                    try {
                        while (isActive) {
                            val events = redis.poll(storeId, cursor)
                            events.map{ event ->
                                cursor = event.id
                                send(Msg.Event(event))
                            }
                        }
                    } catch(e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        println("reader failed store=$storeId: $e | cause: ${generateSequence(e.cause) { it.cause }.joinToString(" <- ")}")   // cause chain
                        inbox.trySend(Msg.ReaderDied)
                    } finally {
                        println("reader ended store=$storeId")
                    }
                }
                serve()
                drain()
            } finally {
                subscribers.keys.forEach { it.close() }
                onDestroy(this@StoreHub)
                inbox.close()
                while (true) {
                    val msg = inbox.tryReceive().getOrNull() ?: break
                    if (msg is Msg.Join) msg.channel.close()
                }
            }
            scope.cancel()
        }

        healthCheck = scope.launch(CoroutineName("owner-$storeId - health check loop")) {
            while (isActive) {
                delay(Duration.ofSeconds(1L))
                inbox.trySend(Msg.Tick)
            }
        }
    }

    suspend fun send(msg: Msg) {
        inbox.send(msg)
    }

    private suspend fun serve() {
        for (msg in inbox) {
            when (msg) {
                is Msg.Join -> {
                    val upTo = tryReplay(msg.lastEventId, msg.channel) ?: continue
                    subscribers[msg.channel] = upTo
                    println("${who()} store=$storeId Join subscribers=${subscribers.size}")
                }
                is Msg.Leave -> {
                    subscribers.remove(msg.channel)
                    println("${who()} store=$storeId Leave subscribers=${subscribers.size}")
                }
                is Msg.Event -> {
                    last = msg.event.id
                    buffer.add(msg.event)
                    subscribers.forEach { (subscriber, upTo) ->
                        if (msg.event.id <= upTo) return@forEach
                        val boxedEvent = msg.event.toServerSentEvent()
                        subscriber.trySend(boxedEvent)
                            .onFailure { cause ->
                                println("cause $cause when sending $boxedEvent")
                            }
                    }
                }
                is Msg.Tick -> {
                    if (subscribers.isEmpty()) {
                        if (idleSince == null) {
                            idleSince = TimeSource.Monotonic.markNow()
                            println("${who()} idle start store=$storeId")
                        } else if (idleSince!!.elapsedNow() >= properties.broadcastLoopTimeout.toKotlinDuration()) {
                            destroy()
                            break
                        }
                    } else {
                        idleSince = null
                    }
                }
                Msg.ReaderDied -> {
                    destroy()
                    break
                }
            }
        }
    }

    private suspend fun drain() {
        for (msg in inbox) {
            if (msg is Msg.Join) { msg.channel.close() }
        }
    }

    private fun destroy() {
        println("close store=$storeId subscribers=${subscribers.size}")
        onDestroy(this)
        inbox.close()
        reading.cancel()
    }

    private fun tryReplay(lastEventId: String?, channel: Channel<ServerSentEvent<SseEvent>>): StreamId? {
        if (lastEventId == null) return last

        val from = parseOrNull(lastEventId).getOrNull() ?: run {
            sendReloadRequest(channel)
            return null
        }
        val events = buffer.since(from) ?: run {
            sendReloadRequest(channel)
            return null
        }

        events.forEach{ event ->
            val sseEvent = event.toServerSentEvent()
            channel.trySend(sseEvent)
                .onFailure { cause ->
                    println("cause $cause when sending $sseEvent")
                    sendReloadRequest(channel)
                    return null
                }
        }
        return from
    }

    private fun sendReloadRequest(channel: Channel<ServerSentEvent<SseEvent>>) {
        val reloadEvent = SseEvent.reloadRequired(storeId, last).toServerSentEvent()
        channel.trySend(reloadEvent)
        channel.close()
    }
}