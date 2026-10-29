package com.ournight.api_server.domain.order.repository

import com.ournight.api_server.domain.order.model.StoreSalesSummary
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import java.util.UUID

interface StoreSalesSummaryRepository : JpaRepository<StoreSalesSummary, UUID> {
    // 이벤트 업데이트용
    fun findByStoreIdAndTimeSlotIdAndTimeQuantum(storeId: UUID, timeSlotId: UUID, timeQuantum: Int): StoreSalesSummary?

    fun findAllByStoreIdAndTimeSlotIdOrderByTimeQuantumAsc(storeId: UUID, timeSlotId: UUID): List<StoreSalesSummary>

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    fun deleteAllByStoreId(storeId: UUID): Int
}
