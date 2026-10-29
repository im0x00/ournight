package com.ournight.api_server.domain.session.repository

import com.ournight.api_server.domain.session.model.SessionStatus
import com.ournight.api_server.domain.session.model.TableSession
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface TableSessionRepository : JpaRepository<TableSession, UUID> {

    @Query(
        """
        select distinct ts
        from TableSession ts
        left join fetch ts.participants p
        left join fetch p.pass
        where ts.table.id = :tableId
        and ts.status = 'ACTIVE'
        """
    )
    fun findActiveSessionByTableId(tableId: UUID): TableSession?

    @Query("""
        select ts
        from TableSession ts
        join fetch ts.table t
        where ts.table.id = :tableId
          and ts.status = 'ACTIVE'
    """)
    fun findActiveSessionBasicByTableId(tableId: UUID): TableSession?


    @Query("""
        select distinct ts
        from TableSession ts
        join fetch ts.table t
        left join fetch ts.participants p
        left join fetch p.pass
        where ts.table.id = :tableId
          and ts.status = 'ACTIVE'
    """)
    fun findActiveSessionWithParticipantsByTableId(tableId: UUID): TableSession?

    @Query("""
        select distinct ts
        from TableSession ts
        join fetch ts.table t
        left join fetch ts.participants p
        left join fetch p.pass
        where t.store.id = :storeId
          and ts.status = 'ACTIVE'
    """)
    fun findAllActiveSessionsWithParticipantsByStoreId(storeId: UUID): List<TableSession>

    @Query("""
        select distinct ts
        from TableSession ts
        join fetch ts.table t
        left join fetch ts.participants p
        left join fetch p.pass
        where t.store.id = :storeId
          and ts.timeSlot.id = :timeSlotId
          and ts.status = 'ACTIVE'
    """)
    fun findAllLiveSessionsWithParticipantsByStoreIdAndTimeSlotId(storeId: UUID, timeSlotId: UUID): List<TableSession>

    @Query("""
        select ts
        from TableSession ts
        where ts.table.id = :tableId
          and ts.status = 'ACTIVE'
    """)
    fun findSimpleActiveSessionByTableId(tableId: UUID): TableSession?

    @Query(
        """
        select distinct ts
        from TableSession ts
        join fetch ts.table t
        left join fetch ts.participants p
        left join fetch p.pass
        where t.store.id = :storeId
          and ts.status = 'ACTIVE'
        """
    )
    fun findAllActiveSessionsByStoreId(storeId: UUID): List<TableSession>

    @Query(
        """
        select distinct ts
        from TableSession ts
        join fetch ts.table t
        left join fetch ts.participants p
        left join fetch p.pass
        where t.id = :tableId
          and ts.status in :statuses
        order by ts.startedAt desc
        """
    )
    fun findSessionsByTableIdAndStatuses(tableId: UUID, statuses: List<SessionStatus>): List<TableSession>

    @Query("""
        select distinct ts
        from TableSession ts
        join fetch ts.table t
        left join fetch ts.participants p
        left join fetch p.pass
        where t.id in :tableIds
          and ts.status = 'ACTIVE'
    """)
    fun findActiveSessionsByTableIds(tableIds: List<UUID>): List<TableSession>

    fun existsByTableId(tableId: UUID): Boolean

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        delete from TableSession ts
        where ts.id in (
            select ts2.id
            from TableSession ts2
            where ts2.table.store.id = :storeId
        )
        """
    )
    fun deleteAllByStoreId(@Param("storeId") storeId: UUID): Int
}
