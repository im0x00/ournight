package com.ournight.api_server.domain.pass.repository

import com.ournight.api_server.domain.pass.model.PassKey
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface PassKeyRepository : JpaRepository<PassKey, UUID> {
    fun findBySlotId(slotId: UUID): PassKey?

    fun existsBySlotId(slotId: UUID): Boolean

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        delete from PassKey pk
        where pk.slotId in (
            select ts.id
            from StoreTimeSlot ts
            where ts.store.id = :storeId
        )
        """
    )
    fun deleteAllByStoreId(@Param("storeId") storeId: UUID): Int
}
