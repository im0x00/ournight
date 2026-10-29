//package org.im0x00.notification.coroutine
//
//import kotlinx.coroutines.CoroutineScope
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.Job
//import kotlinx.coroutines.SupervisorJob
//import kotlinx.coroutines.cancel
//import kotlinx.coroutines.channels.Channel
//import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
//import kotlinx.coroutines.channels.onFailure
//import kotlinx.coroutines.flow.Flow
//import kotlinx.coroutines.flow.receiveAsFlow
//import kotlinx.coroutines.isActive
//import kotlinx.coroutines.launch
//import kotlinx.coroutines.time.delay
//import org.springframework.http.codec.ServerSentEvent
//import org.springframework.stereotype.Component
//import java.time.Duration
//import java.util.UUID
//import java.util.concurrent.ConcurrentHashMap
//import kotlin.time.TimeMark
//import kotlin.time.TimeSource
//import kotlin.time.toKotlinDuration
//
//sealed interface MYMsg {
//    data class Join(
//        val channel: Channel<ServerSentEvent<SseEvent>>,
//        val lastEventId: String?
//    ): MYMsg
//    data class Leave(
//        val channel: Channel<ServerSentEvent<SseEvent>>
//    ): MYMsg
//    data class Event(
//        val event: SseEvent
//    ): MYMsg
//    data object Tick: MYMsg
//}
//
//@Component
//class MYHub(
//    val redis: RedisService,
//    val properties: HubProperties,
//) {
//    private val stores = ConcurrentHashMap<UUID, MYStoreHub>()
//    private val hubScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
//    private val parentHubJob = hubScope.coroutineContext[Job]
//
//    suspend fun register(storeId: UUID, lastEventId: String?): Flow<ServerSentEvent<SseEvent>> {
//        val storeHub = ownerOf(storeId)
//        val channel = Channel<ServerSentEvent<SseEvent>>(properties.eventPerSecond * properties.timeoutPerClient)
//        storeHub.trySend(MYMsg.Join(channel, lastEventId))
//            .onFailure {
//                val newStoreHub = ownerOf(storeId)
//                newStoreHub.trySend(MYMsg.Join(channel, lastEventId))
//            }
//        return channel.receiveAsFlow()
//    }
//
//    private fun ownerOf(storeId: UUID): MYStoreHub {
//        val storeHub = stores.computeIfAbsent(storeId) {
//            val perStoreScope = CoroutineScope(hubScope.coroutineContext + Job(parentHubJob))
//            MYStoreHub(perStoreScope, storeId, redis, properties) { me ->
//                stores.remove(storeId, me)
//            }
//        }
//        return storeHub
//    }
//}
//
//class MYStoreHub(
//    val scope: CoroutineScope,
//    val storeId: UUID,
//    val redis: RedisService,
//    val properties: HubProperties,
//    val onDestroy: (MYStoreHub) -> Unit
//) {
//    val inbox = Channel<MYMsg>(UNLIMITED)
//    val main: Job
//    val reading: Job
//    val healthChecking: Job
//
//    val subscribers = mutableMapOf<Channel<ServerSentEvent<SseEvent>>, StreamId>()
//    lateinit var last: StreamId
//    var idleSince: TimeMark? = null
//    lateinit var buffer: ReplayBuffer
//
//    init {
//        main = scope.launch {
//            try {
//                val start = redis.fetchLatestId(storeId) ?: StreamId.ZERO
//                buffer = ReplayBuffer(properties.bufferCapacity, start)
//                last = start
//                for (msg in inbox) {
//                    when (msg) {
//                        is MYMsg.Join -> {
//                            val upto = if (msg.lastEventId != null) StreamId.parseOrNull(msg.lastEventId) else last
//                            subscribers.put(msg.channel, upto)
//                            println("${who()} store=$storeId Join subscribers=${subscribers.size}")
//                        }
//                        is MYMsg.Leave -> {
//                            msg.channel.close()
//                            subscribers.remove(msg.channel)
//                            println("${who()} store=$storeId Leave subscribers=${subscribers.size}")
//                        }
//                        is MYMsg.Event -> {
//                            buffer.add(msg.event)
//                            last = msg.event.id
//                            subscribers.forEach { subscriber, _ ->
//                                val sseEvent = msg.event.toServerSentEvent()
//                                subscriber.trySend(sseEvent)
//                                    .onFailure {
//                                        inbox.trySend(MYMsg.Leave(subscriber))
//                                    }
//                            }
//                        }
//                        MYMsg.Tick -> {
//                            if (subscribers.isNotEmpty()) {
//                                idleSince = null
//                                continue
//                            }
//                            if (idleSince == null) {
//                                idleSince = TimeSource.Monotonic.markNow()
//                            } else {
//                                if (idleSince!!.elapsedNow() >= properties.broadcastLoopTimeout.toKotlinDuration()) {
//                                    destroy()
//                                    break
//                                }
//                            }
//                        }
//                    }
//                }
//                // drain
//                for (msg in inbox) {
//                    if (msg is MYMsg.Join) msg.channel.close()
//                }
//            } finally {
//                destroy()
//            }
//            scope.cancel()
//        }
//        reading = scope.launch {
//            var cursor = last
//            while (isActive) {
//                val data = redis.poll(storeId, cursor)
//                data.forEach { event ->
//                    cursor = event.id
//                    trySend(MYMsg.Event(event))
//                }
//            }
//        }
//        healthChecking = scope.launch {
//            while (isActive) {
//                delay(Duration.ofSeconds(1L))
//                inbox.trySend(MYMsg.Tick)
//            }
//        }
//    }
//
//    fun trySend(msg: MYMsg) = inbox.trySend(msg)
//
//
//    private fun destroy() {
//        onDestroy(this)
//        inbox.close()
//        reading.cancel()
//        subscribers.forEach { it.key.close() }
//    }
//
//}