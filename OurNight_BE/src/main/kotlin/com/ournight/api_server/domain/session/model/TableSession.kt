package com.ournight.api_server.domain.session.model

import com.ournight.api_server.domain.store.domain.StoreTable
import com.ournight.api_server.domain.store.model.StoreTimeSlot
import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "table_sessions")
class TableSession(

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "UUID")
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_table_id", nullable = false, columnDefinition = "UUID")
    var table: StoreTable,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "time_slot_id", nullable = false, columnDefinition = "UUID")
    val timeSlot: StoreTimeSlot,

    @OneToMany(
        mappedBy = "session",
        cascade = [CascadeType.ALL],
        orphanRemoval = true
    )
    val participants: MutableSet<SessionParticipant> = mutableSetOf(),

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    var status: SessionStatus = SessionStatus.ACTIVE,

    @Column(name = "started_at", nullable = false, updatable = false)
    val startedAt: LocalDateTime,

    @Column(name = "ended_at")
    var endedAt: LocalDateTime? = null,

    @Column(name = "expires_at")
    var expiresAt: LocalDateTime? = null,

    @Column(name = "merged_into_session_id", columnDefinition = "UUID")
    var mergedIntoSessionId: UUID? = null,

    @Column(name = "session_order_sequence", nullable = false)
    var currentOrderSequence: Int = 0,

    @Version
    @Column(nullable = false)
    var version: Long = 0L

) {

    fun nextOrderSeq(): Int {
        this.currentOrderSequence += 1
        return this.currentOrderSequence
    }

    fun close(now: LocalDateTime) {
        require(status == SessionStatus.ACTIVE) { "이미 종료된 session 입니다." }
        status = SessionStatus.CLOSED
        endedAt = now
    }

    fun moveTo(targetTable: StoreTable) {
        require(status == SessionStatus.ACTIVE) { "종료되지 않은 session만 이동할 수 있습니다." }
        this.table = targetTable
    }

    fun addParticipant(participant: SessionParticipant) {
        participants.add(participant)
    }

}
