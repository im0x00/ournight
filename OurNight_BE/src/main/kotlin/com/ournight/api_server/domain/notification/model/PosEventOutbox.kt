package com.ournight.api_server.domain.notification.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "pos_event_outbox")
class PosEventOutbox(
    @Id
    @Column(name = "id", columnDefinition = "UUID")
    val id: UUID = UUID.randomUUID(),

    @Column(name = "store_id", nullable = false, columnDefinition = "UUID")
    val storeId: UUID,

    @Column(name = "event_type", nullable = false, length = 80)
    val eventType: String,

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    val payload: String,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "published_at")
    var publishedAt: Instant? = null,

    @Column(name = "redis_stream_id", length = 80)
    var redisStreamId: String? = null,

    @Column(name = "attempts", nullable = false)
    var attempts: Int = 0,

    @Column(name = "next_attempt_at", nullable = false)
    var nextAttemptAt: Instant = Instant.now(),

    @Column(name = "last_error", columnDefinition = "TEXT")
    var lastError: String? = null,
) {
    fun markPublished(streamId: String, now: Instant = Instant.now()) {
        redisStreamId = streamId
        publishedAt = now
        lastError = null
    }

    // event별 backoff 없음: 이 event만 미루면 같은 store의 뒤 event가 앞지름.
    fun markFailed(error: Throwable) {
        attempts += 1
        lastError = (error.message ?: error::class.java.simpleName).take(1000)
    }
}
