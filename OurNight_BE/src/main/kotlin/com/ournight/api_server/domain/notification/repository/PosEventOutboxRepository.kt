package com.ournight.api_server.domain.notification.repository

import com.ournight.api_server.domain.notification.model.PosEventOutbox
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface PosEventOutboxRepository : JpaRepository<PosEventOutbox, UUID> {
    @Query(
        value = """
            select *
            from pos_event_outbox
            where published_at is null
                and next_attempt_at <= now()
            order by created_at
            limit :limit
            for update skip locked
        """,
        nativeQuery = true,
    )
    fun lockPublishable(@Param("limit") limit: Int): List<PosEventOutbox>
}
