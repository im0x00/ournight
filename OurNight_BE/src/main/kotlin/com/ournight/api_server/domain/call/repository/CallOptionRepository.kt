package com.ournight.api_server.domain.call.repository

import com.ournight.api_server.domain.call.model.CallOption
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CallOptionRepository : JpaRepository<CallOption, UUID> {
    // 매장별 활성화된 호출 옵션 목록 조회 (고객용)
    fun findAllByStoreIdAndIsActiveTrue(storeId: UUID): List<CallOption>

    // 매장별 모든 호출 옵션 조회 (관리자용)
    fun findAllByStoreId(storeId: UUID): List<CallOption>
}