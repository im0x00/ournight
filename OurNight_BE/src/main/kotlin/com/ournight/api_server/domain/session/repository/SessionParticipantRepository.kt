package com.ournight.api_server.domain.session.repository

import com.ournight.api_server.domain.session.model.SessionParticipant
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface SessionParticipantRepository : JpaRepository<SessionParticipant, UUID> {
    fun findBySessionIdAndPassId(sessionId: UUID, passId: UUID): SessionParticipant?
    fun findAllBySessionId(sessionId: UUID): List<SessionParticipant>
    fun existsBySessionId(sessionId: UUID): Boolean
    fun findByPassId(passId: UUID): SessionParticipant?

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        delete from SessionParticipant sp
        where sp.id in (
            select sp2.id
            from SessionParticipant sp2
            where sp2.session.table.store.id = :storeId
               or sp2.pass.timeSlot.store.id = :storeId
        )
        """
    )
    fun deleteAllByStoreId(@Param("storeId") storeId: UUID): Int
}
