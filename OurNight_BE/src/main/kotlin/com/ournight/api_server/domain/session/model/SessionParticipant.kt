package com.ournight.api_server.domain.session.model

import com.ournight.api_server.domain.pass.model.Pass
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.CreationTimestamp
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(
    name = "session_participants",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_session_participant_session_pass",
            columnNames = ["pass_id"]
        ),
    ]
)
class SessionParticipant(

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "UUID")
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false, columnDefinition = "UUID")
    var session: TableSession,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pass_id", nullable = false, columnDefinition = "UUID")
    val pass: Pass,

    @Column(name = "joined_at", nullable = false, updatable = false)
    @CreationTimestamp
    val joinedAt: LocalDateTime? = null,

) {
    fun moveTo(targetSession: TableSession) {
        session = targetSession
    }
}
