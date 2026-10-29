package com.ournight.api_server.domain.pass.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "pass_keys")
class PassKey(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(name = "slot_id", nullable = false, unique = true)
    val slotId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var type: PassKeyType = PassKeyType.PIN, // PIN, QR(나중에 확장)

    @Column(nullable = false)
    var value: String, // PIN -> 4자리 숫자.

    @UpdateTimestamp
    var updatedAt: LocalDateTime? = null
) {
    fun refresh(newValue: String, newType: PassKeyType = this.type) {
        this.value = newValue
        this.type = newType
    }
}

enum class PassKeyType {
    PIN,
    QR // todo: QR 추가시 확장성용
}