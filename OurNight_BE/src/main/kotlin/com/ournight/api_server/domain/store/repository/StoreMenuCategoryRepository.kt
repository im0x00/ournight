package com.ournight.api_server.domain.store.repository

import com.ournight.api_server.domain.store.domain.Store
import com.ournight.api_server.domain.store.domain.StoreMenu
import com.ournight.api_server.domain.store.model.StoreMenuCategory
import jakarta.persistence.EntityListeners
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID
import org.springframework.data.repository.query.Param
import java.time.Instant

@Repository
@EntityListeners(AuditingEntityListener::class)
interface StoreMenuCategoryRepository : JpaRepository<StoreMenuCategory, UUID> {

    fun findByNameAndStore(name: String, store: Store): StoreMenuCategory?

    // 특정 스토어의 카테고리와 메뉴를 한 번에 조회 (순서 정렬 포함)
    @Query("""
        SELECT DISTINCT c 
        FROM StoreMenuCategory c 
        LEFT JOIN FETCH c.menus m 
        WHERE c.store.id = :storeId 
        ORDER BY c.sequence ASC, m.sequence ASC
    """)
    fun findAllWithMenusByStoreId(storeId: UUID): List<StoreMenuCategory>

    @Query("SELECT MAX(c.updatedAt) FROM StoreMenuCategory c WHERE c.store.id = :storeId")
    fun findMaxUpdatedAtByStoreId(@Param("storeId") storeId: UUID): Instant?

    fun findAllByStoreIdOrderBySequenceAsc(storeId: UUID): List<StoreMenuCategory>

}