package com.ournight.api_server.domain.waiting.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "waiting_statuses")
class WaitingStatus(
    @Id
    @Column(name = "id", columnDefinition = "UUID")
    val id: UUID,

    @Column(name = "name", nullable = false, length = 100, unique = true)
    val name: String,

    @Column(name = "count_value", nullable = false)
    var count: Int = 0,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime? = null,

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime? = null,
)
