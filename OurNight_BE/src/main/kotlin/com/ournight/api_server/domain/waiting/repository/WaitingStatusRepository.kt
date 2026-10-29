package com.ournight.api_server.domain.waiting.repository

import com.ournight.api_server.domain.waiting.model.WaitingStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface WaitingStatusRepository : JpaRepository<WaitingStatus, UUID> {
    fun findAllByOrderByNameAsc(): List<WaitingStatus>
}
