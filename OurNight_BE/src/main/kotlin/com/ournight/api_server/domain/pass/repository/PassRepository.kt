package com.ournight.api_server.domain.pass.repository

import com.ournight.api_server.domain.pass.model.Pass
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface PassRepository : JpaRepository<Pass, UUID> {
    @Query("select p from Pass p join fetch p.user where p.timeSlot.store.id = :storeId")
    fun findAllByStoreIdWithUser(@Param("storeId") storeId: UUID): List<Pass>

    fun findByUserIdAndTimeSlotId(userId: UUID, slotId: UUID): Pass?

    fun existsByTimeSlot_Id(timeSlotId: UUID): Boolean

    @Query(
        """
        select p
        from Pass p
        join fetch p.timeSlot ts
        join fetch ts.store s
        where p.user.id = :userId
        order by ts.startTime desc
        """
    )
    fun findAllByUserIdWithTimeSlotAndStore(@Param("userId") userId: UUID): List<Pass>

    @Query(
        """
        select p
        from Pass p
        join fetch p.user u
        join fetch p.timeSlot ts
        join fetch ts.store s
        order by ts.startTime desc, u.kakaoId asc
        """
    )
    fun findAllWithUserTimeSlotAndStoreForAdmin(): List<Pass>

    @Query(
        """
        select p
        from Pass p
        join fetch p.timeSlot ts
        join fetch ts.store s
        where p.user.id = :userId
          and s.id = :storeId
        order by ts.startTime desc
        """
    )
    fun findAllByUserIdAndStoreIdWithTimeSlotAndStore(
        @Param("userId") userId: UUID,
        @Param("storeId") storeId: UUID,
    ): List<Pass>

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        delete from Pass p
        where p.id in (
            select p2.id
            from Pass p2
            where p2.timeSlot.store.id = :storeId
        )
        """
    )
    fun deleteAllByStoreId(@Param("storeId") storeId: UUID): Int
}
