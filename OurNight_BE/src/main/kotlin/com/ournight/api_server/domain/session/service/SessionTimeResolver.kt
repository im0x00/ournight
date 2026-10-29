package com.ournight.api_server.domain.session.service

import com.ournight.api_server.domain.session.model.TableSession
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime

data class SessionTimeSnapshot(
    val enteredAt: LocalDateTime,
    val expiresAt: LocalDateTime,
    val remainingMinutes: Long,
    val timeOver: Boolean,
)

@Component
class SessionTimeResolver(
    private val clock: Clock,
) {
    fun resolve(
        session: TableSession,
        now: LocalDateTime = LocalDateTime.now(clock),
    ): SessionTimeSnapshot {
        val defaultExpiresAt = session.startedAt.plusMinutes(session.table.store.timeLimit.toLong())
        val participantExpiresAt = session.participants
            .mapNotNull { it.pass.expiresAt }
            .maxOrNull()

        val expiresAt = participantExpiresAt
            ?: session.expiresAt
            ?: defaultExpiresAt

        return SessionTimeSnapshot(
            enteredAt = session.startedAt,
            expiresAt = expiresAt,
            remainingMinutes = Duration.between(now, expiresAt).toMinutes().coerceAtLeast(0),
            timeOver = !session.timeSlot.endTime.isAfter(now),
        )
    }
}
