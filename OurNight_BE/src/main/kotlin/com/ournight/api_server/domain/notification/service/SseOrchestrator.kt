package com.ournight.api_server.domain.notification.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.ournight.api_server.global.error.ErrorCode
import com.ournight.api_server.global.error.exception.BusinessException
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Range
import org.springframework.data.redis.connection.stream.ReadOffset
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.connection.stream.StreamReadOptions
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

@Service
class SseOrchestrator(
    private val objectMapper: ObjectMapper,
    private val redisTemplate: StringRedisTemplate,
    private val runtimeProperties: SseRuntimeProperties,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val MAX_MESSAGES_PER_STORE = 2000
    private val DISPATCHER_WORKERS = 16
    private val SENDER_LIMIT = 50
    val OUTBOUND_QUEUE_SIZE get() = runtimeProperties.outboundQueueSize
    val CATCHUP_QUEUE_SIZE get() = runtimeProperties.replayQueueSize
    private val DRAIN_LIMET_PER_EMITTER = 20
    private val MAX_REPLAYING_PER_EMITTER = 300
    private val MAX_TOTAL_REPLAYING = 1000

    private val storeConnections: ConcurrentMap<String, SseStoreRuntime> = ConcurrentHashMap()
    private val streamReaders = ConcurrentHashMap<String, Future<*>>()
    private val streamReaderExec = Executors.newCachedThreadPool()
    private val dispatchExec = Executors.newFixedThreadPool(DISPATCHER_WORKERS)
    private val emitterExec = Executors.newCachedThreadPool()

    fun createTicket(storeId: UUID): String {
        val ticket = UUID.randomUUID().toString().replace("-", "")
        redisTemplate.opsForValue().set("ticket:$ticket", storeId.toString(), Duration.ofSeconds(30))
        return ticket
    }

    fun subscribe(
        expectedStoreId: UUID,
        ticket: String,
        connectionId: String?,
        lastEventId: String?
    ): SseEmitter {
        val storeIdStr = redisTemplate.opsForValue().getAndDelete("ticket:$ticket")
            ?: throw BusinessException(ErrorCode.INVALID_TICKET)

        if (storeIdStr != expectedStoreId.toString()) {
            throw BusinessException(ErrorCode.INVALID_TICKET)
        }

        val emitter = SseEmitter(60 * 60 * 1000L)
        val chanKey = channelKey(storeIdStr)
        val sKey = streamKey(storeIdStr)
        val safeConnectionId = connectionId?.takeIf { it.isNotBlank() }?.take(128) ?: UUID.randomUUID().toString()
        val connectionRuntime = ConnectionRuntime(
            connectionId = safeConnectionId,
            outboundQueue = ArrayBlockingQueue(OUTBOUND_QUEUE_SIZE),
            catchUpBuffer = ArrayBlockingQueue(CATCHUP_QUEUE_SIZE),
            emitterTasks = null
        )
        val storeRuntime = storeConnections.compute(chanKey) { _, existing ->
            val runtime = existing ?: SseStoreRuntime(
                chanKey = chanKey,
                broadcastQueue = ArrayBlockingQueue(MAX_MESSAGES_PER_STORE),
                connectionRuntimes = ConcurrentHashMap()
            )

            runtime.connectionRuntimes[emitter] = connectionRuntime

            runtime
        }!!

        emitter.onCompletion { unregisterEmitter(chanKey, emitter, "completion") }
        emitter.onTimeout { emitter.complete(); unregisterEmitter(chanKey, emitter, "timeout") }
        emitter.onError {
            logger.warn("[SSE-ERROR] storeId={} connectionId={}", chanKey, connectionId)
            unregisterEmitter(chanKey, emitter, "error")
        }
        val tailKey = redisTemplate.opsForStream<String, String>().info(sKey).lastGeneratedId()
        if (!lastEventId.isNullOrBlank()) {
            val records = redisTemplate.opsForStream<String, String>()
                .range(sKey, Range.leftOpen(lastEventId, tailKey))
                ?: emptyList()
            var count = 0
            records.forEach { record ->
                val success = storeRuntime.sendToEmitter( emitter,
                    record.value["type"] ?: "UNKNOWN",
                    payloadWithOutboxEventId(
                        record.value["payload"] ?: "{}",
                        record.value["eventId"]
                    ),
                    record.id.toString()
                )
                count++
                if (!success || count > MAX_REPLAYING_PER_EMITTER) {
                    storeRuntime.requireReload(emitter)
                    unregisterEmitter(chanKey, emitter, "replaying failed")
                    return emitter
                }
            }
        }
        startEmitting(chanKey, emitter)
        if (connectionRuntime.awaitUntilActive() == true) {
            storeRuntime.sendToEmitter(emitter, "connect", "connected", null)
        }
        startRedisFetching(chanKey, sKey, tailKey)

        return emitter
    }

    private fun startRedisFetching(chanKey: String, streamKey: String, startKey: String) {
        val options = StreamReadOptions.empty()
            .count(100)
            .block(Duration.ofSeconds(2))

        var curKey = startKey

        var backoffMs = 100L
        val maxBackoffMs = 10_000L
        var outage = false

        streamReaders.compute(chanKey) { _, existing ->
            if (!storeConnections.containsKey(chanKey)) return@compute null
            if (existing != null && !existing.isDone && !existing.isCancelled) return@compute existing

            try {
                val task = streamReaderExec.submit {
                    val storeRuntime = storeConnections[chanKey]

                    while (!Thread.currentThread().isInterrupted && storeConnections.containsKey(chanKey)) {
                        try {
                            val records = redisTemplate.opsForStream<String, String>().read(
                                options,
                                StreamOffset.create(streamKey, ReadOffset.from(curKey))
                            ) ?: emptyList()

                            backoffMs = 100L
                            outage = false

                            for (record in records) {
                                val msg = SseMsg(
                                    record.value["type"] ?: "UNKNOWN",
                                    payloadWithOutboxEventId(
                                        record.value["payload"] ?: "{}",
                                        record.value["eventId"]
                                    ),
                                    record.id.toString()
                                )
                                check(storeRuntime?.accumulateMsg(msg) == true)
                                curKey = record.id.toString()
                            }
                        } catch (e: InterruptedException) {
                            Thread.currentThread().interrupt()
                            break

                        } catch (e: Exception) {
                            logger.error("Error while reading {}", curKey, e)

                            if (!outage) {
                                outage = true

                                storeRuntime?.connectionRuntimes?.forEach { emitter, _ ->
                                    storeRuntime.requireReload(emitter)
                                }
                            }

                            try {
                                Thread.sleep(backoffMs)
                            } catch (e: InterruptedException) {
                                Thread.currentThread().interrupt()
                                break
                            }

                            backoffMs = (backoffMs * 2).coerceAtMost(maxBackoffMs)
                        }
                    }
                }
                logger.info("Redis Stream Reader Started: chanKey={} streamKey={} startId={}", chanKey, streamKey, curKey)
                task
            }  catch (e: Exception) {
                logger.error("Failed to start redis stream reader", e)
                null
            }
        }
    }

    fun startDispatching() {
        repeat(DISPATCHER_WORKERS) { idx ->
            dispatchExec.submit {
                while (!Thread.currentThread().isInterrupted) {
                    var cnt = 0
                    storeConnections.forEach { chanKey, storeRuntime ->
                        val workerIdx = (chanKey.hashCode() and Int.MAX_VALUE) % DISPATCHER_WORKERS
                        if (workerIdx != idx) return@forEach
                        if (cnt >= SENDER_LIMIT || !storeConnections.containsKey(chanKey)) return@forEach
                        val msg = storeRuntime.broadcastQueue.poll() ?: return@forEach

                        val failedEmitters = storeRuntime.enqueueLive(msg)
                        failedEmitters.forEach { emitter ->
                            unregisterEmitter(chanKey, emitter, "queue overflow")
                        }
                        cnt++
                    }
                    if (cnt == 0) {
                        Thread.sleep(2)
                    }
                }
            }
        }
    }

    fun startEmitting(chanKey: String, emitter: SseEmitter) {
        val storeRuntime = storeConnections[chanKey] ?: return
        lateinit var task: Future<Unit>

        task = FutureTask {
            val connectionRuntime = storeRuntime.connectionRuntimes[emitter] ?: return@FutureTask
            connectionRuntime.assignTask(task)
            var replayCounter = 0
            while (!Thread.currentThread().isInterrupted) {
                val replayBatch = storeRuntime.drainCatchUpBuffer(emitter, DRAIN_LIMET_PER_EMITTER)

                if (replayCounter > MAX_TOTAL_REPLAYING) {
                    storeRuntime.requireReload(emitter)
                    unregisterEmitter(chanKey, emitter, "outreach the total replaying limit")
                    return@FutureTask
                }

                if (replayBatch.isNotEmpty()) {
                    replayBatch.forEach {
                        val success = storeRuntime.sendToEmitter(emitter, it.name, it.data, it.id)
                        replayCounter++
                        if (!success) {
                            storeRuntime.requireReload(emitter)
                            unregisterEmitter(chanKey, emitter, "replay emitting failed")
                            return@FutureTask
                        }
                    }
                    continue
                }

                if (!connectionRuntime.awaitUntilActive()) break

                val live = storeRuntime.pollOutbound(emitter, 30, TimeUnit.SECONDS)
                    ?: continue

                if (!storeRuntime.sendToEmitter(emitter, live.name, live.data, live.id)) {
                    unregisterEmitter(chanKey, emitter, "[] failed to sendToEmitter")
                    return@FutureTask
                }
            }
            logger.info("[SSE-SENDER-STOP] storeId={} connectionId={}", chanKey, storeRuntime.connectionRuntimes[emitter]?.connectionId)
        }

        emitterExec.execute(task)
    }

    @Scheduled(fixedRate = 20000)
    fun sendHeartbeat() {
        storeConnections.forEach { _, storeRuntime ->
            storeRuntime.accumulateMsg(
                SseMsg("PING", "{}", null)
            )
        }
    }

    @PostConstruct
    fun init() {
        startDispatching()
    }

    @PreDestroy
    fun shutdown() {
        logger.info("[SSE-SHUTDOWN] notifying active emitters before service stop")
        storeConnections.forEach { _, storeRuntime ->
            storeRuntime.connectionRuntimes.forEach { emitter, conn ->
                storeRuntime.sendToEmitter(emitter, "RELOAD_REQUIRED", "{}", null)
                conn.close()
            }
            storeRuntime.broadcastQueue.clear()
        }

        streamReaders.values.forEach { it.cancel(true) }
        streamReaderExec.shutdownNow()
        emitterExec.shutdownNow()
        dispatchExec.shutdownNow()
    }

    private fun payloadWithOutboxEventId(payload: String, outboxEventId: String?): String {
        if (outboxEventId.isNullOrBlank()) return payload

        return try {
            val node = objectMapper.readTree(payload)
            if (!node.isObject) return payload

            (node as com.fasterxml.jackson.databind.node.ObjectNode)
                .put("__outboxEventId", outboxEventId)
            objectMapper.writeValueAsString(node)
        } catch (_: Exception) {
            payload
        }
    }

    internal fun hasConnection(storeId: UUID, connectionId: String): Boolean =
        storeConnections[channelKey(storeId.toString())]
            ?.connectionRuntimes
            ?.values
            ?.any { runtime -> runtime.connectionId == connectionId }
            ?: false

    private fun unregisterEmitter(
        chanKey: String,
        emitter: SseEmitter,
        reason: String
    ) {
        storeConnections.computeIfPresent(chanKey) { _, store ->
            val connectionRuntime = store.connectionRuntimes.remove(emitter)
                    ?: return@computeIfPresent store
            val connectionId = connectionRuntime.connectionId
            connectionRuntime.close()

            logger.info("[SSE-DISCONNECT] storeId={} connectionId={} reason={}", chanKey, connectionId, reason)

            if (store.connectionRuntimes.isEmpty()) {
                streamReaders.computeIfPresent(chanKey) { _, task ->
                    try {
                        task.cancel(true)
                        logger.info("Redis Stream Reader Cancelled: chanKey={}", chanKey)
                    } catch (e: Exception) {
                        logger.warn("Cancel failed: chanKey={}", chanKey, e)
                    }
                    null
                }
                store.broadcastQueue.clear()
                null
            } else {
                store
            }
        }
    }
}
