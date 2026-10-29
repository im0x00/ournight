package com.ournight.api_server.domain.notification.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.ournight.api_server.api.pos.dto.PosStreamChangesResponse
import com.ournight.api_server.domain.notification.PosStreamKey
import org.springframework.data.domain.Range
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.util.UUID

private const val ZERO_STREAM_ID = "0-0"

@Service
class PosStreamChangeService(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
    private val posStreamWatermarkService: PosStreamWatermarkService,
) {
    fun collectChanges(
        storeId: UUID,
        sinceEventId: String?,
        maxEvents: Int,
    ): PosStreamChangesResponse {
        val normalizedSince = sinceEventId?.takeIf { it.isNotBlank() } ?: ZERO_STREAM_ID
        val currentWatermark = posStreamWatermarkService.currentWatermark(storeId)

        if (parseStreamId(normalizedSince) == null) {
            return reloadRequired(normalizedSince, currentWatermark, "INVALID_SINCE_EVENT_ID")
        }

        if (normalizedSince == ZERO_STREAM_ID) {
            return reloadRequired(normalizedSince, currentWatermark, "NO_BASELINE_EVENT_ID")
        }

        val streamKey = PosStreamKey.store(storeId)
        if (isReplayGapExpired(streamKey, normalizedSince)) {
            return reloadRequired(normalizedSince, currentWatermark, "REPLAY_GAP_EXPIRED")
        }

        val records = redisTemplate.opsForStream<String, String>()
            .range(streamKey, Range.rightOpen(normalizedSince, "+"))
            ?: emptyList()

        if (records.size > maxEvents) {
            return reloadRequired(normalizedSince, currentWatermark, "TOO_MANY_EVENTS")
        }

        val orderIds = linkedSetOf<UUID>()
        val tableIds = linkedSetOf<UUID>()
        val menuIds = linkedSetOf<UUID>()
        val callRequestIds = linkedSetOf<UUID>()

        for (record in records) {
            val type = record.value["type"] ?: return reloadRequired(
                normalizedSince,
                currentWatermark,
                "MISSING_EVENT_TYPE",
            )

            if (type == "RELOAD_REQUIRED") {
                return reloadRequired(normalizedSince, currentWatermark, "STREAM_RELOAD_REQUIRED")
            }

            val payload = parsePayload(record.value["payload"])
                ?: return reloadRequired(normalizedSince, currentWatermark, "INVALID_EVENT_PAYLOAD")

            when (type) {
                "HALL_PAYMENT",
                "HALL_PAYMENT_RESOLVED",
                "HALL_PAYMENT_CANCELED",
                "HALL_SERVE",
                "HALL_SERVE_RESOLVED",
                "KITCHEN_COOK",
                "KITCHEN_COOK_RESOLVED",
                "HALL_ORDER_ITEM_DECREASED",
                "KITCHEN_ORDER_ITEM_DECREASED",
                "HALL_ORDER_ITEM_CANCELED",
                "KITCHEN_ORDER_ITEM_CANCELED" -> {
                    payload.uuid("orderId")?.let(orderIds::add)
                    payload.uuid("tableId")?.let(tableIds::add)
                    payload.uuid("menuId")?.let(menuIds::add)
                }

                "CUSTOMER_CALL",
                "CUSTOMER_CALL_RESOLVED" -> {
                    payload.uuid("requestId")?.let(callRequestIds::add)
                    payload.uuid("tableId")?.let(tableIds::add)
                }

                "TABLE_SUMMARY_CHANGED" -> {
                    payload.uuid("tableId")?.let(tableIds::add)
                }

                "ORDERS_REASSIGNED_TO_SESSION" -> {
                    payload.uuid("sourceTableId")?.let(tableIds::add)
                    payload.uuid("targetTableId")?.let(tableIds::add)
                    payload["orderIds"]?.takeIf { it.isArray }?.forEach { node ->
                        node.asUuidOrNull()?.let(orderIds::add)
                    }
                }

                "KITCHEN_MENU_CHANGED" -> {
                    payload.uuid("menuId")?.let(menuIds::add)
                }

                else -> {
                    return reloadRequired(normalizedSince, currentWatermark, "UNKNOWN_EVENT_TYPE:$type")
                }
            }
        }

        return PosStreamChangesResponse(
            sinceEventId = normalizedSince,
            currentWatermark = currentWatermark,
            requiresReload = false,
            changedOrderIds = orderIds.toList(),
            changedTableIds = tableIds.toList(),
            changedMenuIds = menuIds.toList(),
            changedCallRequestIds = callRequestIds.toList(),
        )
    }

    private fun parsePayload(payload: String?): JsonNode? =
        try {
            objectMapper.readTree(payload ?: "{}")
        } catch (_: Exception) {
            null
        }

    private fun JsonNode.uuid(fieldName: String): UUID? = this[fieldName]?.asUuidOrNull()

    private fun JsonNode.asUuidOrNull(): UUID? =
        try {
            if (isNull) null else UUID.fromString(asText())
        } catch (_: Exception) {
            null
        }

    private fun reloadRequired(
        sinceEventId: String,
        currentWatermark: String,
        reason: String,
    ): PosStreamChangesResponse =
        PosStreamChangesResponse(
            sinceEventId = sinceEventId,
            currentWatermark = currentWatermark,
            requiresReload = true,
            reason = reason,
        )

    private fun isReplayGapExpired(streamKey: String, lastEventId: String): Boolean {
        val lastId = parseStreamId(lastEventId) ?: return true
        val info = try {
            redisTemplate.opsForStream<String, String>().info(streamKey)
        } catch (_: Exception) {
            return false
        }
        val firstId = info.firstEntryId() ?: return false
        return compareStreamIds(parseStreamId(firstId) ?: return false, lastId) > 0
    }

    private fun parseStreamId(id: String): Pair<Long, Long>? {
        val parts = id.split("-", limit = 2)
        if (parts.size != 2) return null
        val millisecondsTime = parts[0].toLongOrNull() ?: return null
        val sequence = parts[1].toLongOrNull() ?: return null
        return millisecondsTime to sequence
    }

    private fun compareStreamIds(a: Pair<Long, Long>, b: Pair<Long, Long>): Int {
        val timeCompare = a.first.compareTo(b.first)
        if (timeCompare != 0) return timeCompare
        return a.second.compareTo(b.second)
    }
}
