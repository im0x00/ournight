package com.ournight.api_server.global.loadtest

import com.fasterxml.jackson.databind.ObjectMapper
import com.ournight.api_server.domain.notification.model.PosEventOutbox
import com.ournight.api_server.domain.notification.repository.PosEventOutboxRepository
import com.ournight.api_server.domain.notification.service.SseOrchestrator
import com.ournight.api_server.domain.notification.service.SseMsg
import com.ournight.api_server.domain.notification.service.SseStoreRuntime
import jakarta.transaction.Transactional
import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

/** Test-classpath-only visibility into the in-memory SSE runtime. */
@RestController
@Profile("loadtest")
@RequestMapping("/__load-test/notification-metrics")
class LoadTestNotificationMetricsController(
    private val sseOrchestrator: SseOrchestrator,
    private val outboxRepository: PosEventOutboxRepository,
    private val objectMapper: ObjectMapper,
) {
    private val storesField = SseOrchestrator::class.java
        .getDeclaredField("storeConnections")
        .also { it.isAccessible = true }
    private val unregisterEmitterMethod = SseOrchestrator::class.java
        .getDeclaredMethod(
            "unregisterEmitter",
            String::class.java,
            SseEmitter::class.java,
            String::class.java,
        )
        .also { it.isAccessible = true }
    private val faultInjectedQueueOverflowConnectionIds = ConcurrentHashMap.newKeySet<String>()

    @GetMapping
    fun metrics(): Map<String, Any> {
        @Suppress("UNCHECKED_CAST")
        val stores = storesField.get(sseOrchestrator) as ConcurrentMap<String, SseStoreRuntime>
        val connections = stores.values.flatMap { it.connectionRuntimes.values }

        val broadcastDepths = stores.values.map { it.broadcastQueue.size }
        val outboundDepths = connections.map { it.outboundQueue.size }
        val catchUpDepths = connections.map { it.catchUpBuffer.size }

        return mapOf(
            "at" to Instant.now().toString(),
            "activeSseConnections" to connections.size,
            "activeSseStores" to stores.size,
            "broadcastQueueDepth" to depths(broadcastDepths),
            "outboundQueueDepth" to depths(outboundDepths),
            "catchUpQueueDepth" to depths(catchUpDepths),
            "faultInjectedQueueOverflowConnectionIds" to faultInjectedQueueOverflowConnectionIds.toList(),
        )
    }

    @PostMapping("/events")
    @Transactional
    fun commitEvents(@RequestBody request: CommitLoadTestEventsRequest): Map<String, Any> {
        val outboxCreatedAt = Instant.now()
        val events = request.events.map { event ->
            PosEventOutbox(
                storeId = event.storeId,
                eventType = "TABLE_SUMMARY_CHANGED",
                createdAt = outboxCreatedAt,
                payload = objectMapper.writeValueAsString(
                    mapOf(
                        "loadTestLogicalEventId" to event.logicalEventId,
                        "loadTestValue" to 1,
                    )
                ),
            )
        }
        outboxRepository.saveAll(events)
        return mapOf(
            "committed" to events.size,
            "outboxCreatedAtEpochMs" to outboxCreatedAt.toEpochMilli(),
        )
    }

    @PostMapping("/flush-disconnects")
    fun flushDisconnects(): Map<String, Any> {
        sseOrchestrator.sendHeartbeat()
        return mapOf("accepted" to true)
    }

    @PostMapping("/faults/saturate-outbound")
    fun saturateOutbound(
        @RequestBody request: SaturateOutboundRequest,
    ): Map<String, Any> {
        @Suppress("UNCHECKED_CAST")
        val stores = storesField.get(sseOrchestrator) as ConcurrentMap<String, SseStoreRuntime>
        val requested = request.connectionIds.toSet()
        val matched = stores.entries.flatMap { (channelKey, store) ->
            store.connectionRuntimes.entries.mapNotNull { (emitter, runtime) ->
                SlowConsumerTarget(channelKey, store, emitter, runtime)
                    .takeIf { runtime.connectionId in requested }
            }
        }

        matched.forEach { target ->
            target.runtime.emitterTasks?.cancel(true)
            while (target.runtime.outboundQueue.offer(SseMsg("LOAD_TEST_BACKPRESSURE", "{}", null))) {
                // Fill the bounded queue after stopping only this test connection's sender.
            }
        }

        val overflowed = matched
            .groupBy { it.store }
            .flatMap { (store, targets) ->
                val targetByEmitter = targets.associateBy { it.emitter }
                store.enqueueLive(SseMsg("LOAD_TEST_BACKPRESSURE_PROBE", "{}", null))
                    .mapNotNull(targetByEmitter::get)
            }
        overflowed.forEach { target ->
            faultInjectedQueueOverflowConnectionIds += target.runtime.connectionId
            unregisterEmitterMethod.invoke(
                sseOrchestrator,
                target.channelKey,
                target.emitter,
                "queue overflow",
            )
        }

        return mapOf(
            "requestedConnections" to requested.size,
            "saturatedConnections" to matched.size,
            "overflowedConnections" to overflowed.size,
            "overflowedConnectionIds" to overflowed.map { it.runtime.connectionId },
        )
    }

    private fun depths(values: List<Int>): Map<String, Int> = mapOf(
        "total" to values.sum(),
        "max" to (values.maxOrNull() ?: 0),
    )
}

data class CommitLoadTestEventsRequest(
    val events: List<CommitLoadTestEvent>,
)

data class CommitLoadTestEvent(
    val storeId: UUID,
    val logicalEventId: String,
)

data class SaturateOutboundRequest(
    val connectionIds: List<String>,
)

private data class SlowConsumerTarget(
    val channelKey: String,
    val store: SseStoreRuntime,
    val emitter: SseEmitter,
    val runtime: com.ournight.api_server.domain.notification.service.ConnectionRuntime,
)
