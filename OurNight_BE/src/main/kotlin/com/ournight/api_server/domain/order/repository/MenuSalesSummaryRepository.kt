package com.ournight.api_server.domain.order.repository

import com.ournight.api_server.domain.order.model.MenuSalesSummary
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import java.util.UUID

interface MenuSalesSummaryRepository : JpaRepository<MenuSalesSummary, UUID> {
    // 이벤트 업데이트용
    fun findByStoreIdAndTimeSlotIdAndStoreMenuId(storeId: UUID, timeSlotId: UUID, storeMenuId: UUID): MenuSalesSummary?

    fun findTop10ByStoreIdAndTimeSlotIdOrderByTotalAmountDesc(storeId: UUID, timeSlotId: UUID): List<MenuSalesSummary>

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    fun deleteAllByStoreId(storeId: UUID): Int
}
