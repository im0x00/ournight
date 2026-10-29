package com.ournight.api_server.domain.pass.model

import com.ournight.api_server.domain.store.domain.Store
import com.ournight.api_server.domain.store.model.StoreTimeSlot
import com.ournight.api_server.domain.user.model.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

/*
Store 1 - N Pass N - 1 User
Pass 1 - N SessionParticipant N - 1 TableSession
* */


@Entity
@Table(name = "passes")
class Pass(

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "UUID")
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_id", nullable = false)
    val timeSlot: StoreTimeSlot,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: PassStatus = PassStatus.INACTIVE,

    @Column(name = "activated_at")
    var activatedAt: LocalDateTime? = null,

    @Column(name = "expired_at")
    var expiresAt: LocalDateTime? = null,

) {

    val store: Store
        get() = timeSlot.store

    fun activate(
        now: LocalDateTime,
        timeLimitMinutes: Long
    ) {
        if (this.status != PassStatus.INACTIVE) {
            throw IllegalStateException("이미 활성화되었거나 만료된 패스임")
        }
        this.status = PassStatus.ACTIVATED
        this.activatedAt = now
        this.expiresAt = now.plusMinutes(timeLimitMinutes)
    }

    fun expire() {
        if (status == PassStatus.EXPIRED) return
        status = PassStatus.EXPIRED
    }

    fun isExpired(now: LocalDateTime): Boolean {
        val exp = expiresAt ?: return false
        return !exp.isAfter(now)
    }

    fun isOrderable(now: LocalDateTime): Boolean {
        if (status != PassStatus.ACTIVATED) return false
        val exp = expiresAt ?: return false
        return !now.isAfter(exp)
    }

}
