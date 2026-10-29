package com.ournight.api_server.domain.call.repository


import com.ournight.api_server.domain.call.model.CallRequest
import com.ournight.api_server.domain.call.model.CallStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface CallRequestRepository : JpaRepository<CallRequest, UUID> {

    // 매장 ID를 기준으로 완료되지 않은 호출 목록 조회
    // 관계: CallRequest -> StoreTable(table) -> Store(store)
    @Query("""
        SELECT cr FROM CallRequest cr 
        JOIN FETCH cr.table t 
        JOIN FETCH cr.callOption co 
        WHERE t.store.id = :storeId AND cr.status != :status
        ORDER BY cr.createdAt ASC
    """)
    fun findAllByStoreIdAndStatusNot(
        @Param("storeId") storeId: UUID,
        @Param("status") status: CallStatus
    ): List<CallRequest>

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        delete from CallRequest cr
        where cr.id in (
            select cr2.id
            from CallRequest cr2
            where cr2.table.store.id = :storeId
        )
        """
    )
    fun deleteAllByStoreId(@Param("storeId") storeId: UUID): Int
}
