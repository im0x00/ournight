package com.ournight.api_server.domain.store.repository

import com.ournight.api_server.domain.store.model.StoreTimeSlot
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime
import java.util.UUID

interface StoreTimeSlotRepository : JpaRepository<StoreTimeSlot, UUID> {
    fun findAllByStoreId(storeId: UUID): List<StoreTimeSlot>
    fun findByStore_IdAndStartTimeAndEndTime(
        storeId: UUID,
        startTime: LocalDateTime,
        endTime: LocalDateTime,
    ): StoreTimeSlot?

    fun findAllByStore_IdAndStartTimeLessThanEqualAndEndTimeGreaterThan(
        storeId: UUID,
        now: LocalDateTime,
        now2: LocalDateTime
    ): List<StoreTimeSlot>

    fun findByIdAndStore_Id(slotId: UUID, storeId: UUID): StoreTimeSlot?

    @Query(
        """
        select s 
        from StoreTimeSlot s
        where s.store.id = :storeId
        and :now between s.startTime and s.endTime
        """
    )
    fun findCurrentSlot(
        @Param("storeId") storeId: UUID,
        @Param("now") now: LocalDateTime
    ): StoreTimeSlot?

    fun findAllByStore_IdAndStartTimeGreaterThanEqualAndStartTimeLessThan(
        storeId: UUID,
        start: LocalDateTime,
        end: LocalDateTime,
    ): List<StoreTimeSlot>

    @Query("""
        SELECT s
        FROM StoreTimeSlot s
        WHERE s.store.id = :storeId
          AND s.startTime <= :target
          AND s.endTime > :target
    """)
    fun findSlotAt(
        @Param("storeId") storeId: UUID,
        @Param("target") target: LocalDateTime
    ): StoreTimeSlot?
}
