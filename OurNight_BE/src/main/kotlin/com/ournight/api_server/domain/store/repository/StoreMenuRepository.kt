package com.ournight.api_server.domain.store.dao

import com.ournight.api_server.domain.store.domain.StoreMenuStatus
import com.ournight.api_server.domain.store.domain.StoreMenu
import org.springframework.data.repository.query.Param
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID
import java.time.Instant
import jakarta.persistence.LockModeType

@Repository
interface StoreMenuRepository : JpaRepository<StoreMenu, UUID> {
	@Query("SELECT m FROM StoreMenu m JOIN FETCH m.store WHERE m.id = :id")
	fun findByIdWithStore(@Param("id") id: UUID): StoreMenu?

	@Query("""
		SELECT DISTINCT m
		FROM StoreMenu m
		JOIN FETCH m.category c
		LEFT JOIN FETCH m.badges b
		WHERE m.store.id = :storeId
		  AND m.id = :menuId
		  AND m.isDeleted = false
	""")
	fun findByStoreIdAndIdWithCategoryAndBadges(
		@Param("storeId") storeId: UUID,
		@Param("menuId") menuId: UUID,
	): StoreMenu?

	@Query("""
        SELECT m.id
        FROM StoreMenu m
        WHERE m.store.id = :storeId
          AND m.id IN :menuIds
          AND (m.status = :status OR m.isDeleted = true)
    """)
	fun findUnavailableMenuIds(
		@Param("storeId") storeId: UUID,
		@Param("menuIds") menuIds: List<UUID>,
		@Param("status") status: StoreMenuStatus = StoreMenuStatus.NOT_AVAILABLE
	): List<UUID>

	@Query("SELECT m FROM StoreMenu m WHERE m.id IN :menuIds")
	fun findAllByIds(menuIds: List<UUID>): List<StoreMenu>


	@Query("SELECT MAX(m.updatedAt) FROM StoreMenu m WHERE m.store.id = :storeId")
	fun findMaxUpdatedAtByStoreId(@Param("storeId") storeId: UUID): Instant?

	@Query("""
		select sm
		from StoreMenu sm
		where sm.store.id = :storeId
		  and sm.id in :menuIds
		  and sm.isDeleted = false
	""")
	fun findAllByStoreIdAndIds(storeId: UUID, menuIds: List<UUID>): List<StoreMenu>

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		select sm
		from StoreMenu sm
		where sm.store.id = :storeId
		  and sm.id in :menuIds
		  and sm.isDeleted = false
	""")
	fun findAllByStoreIdAndIdsForUpdate(
		@Param("storeId") storeId: UUID,
		@Param("menuIds") menuIds: List<UUID>
	): List<StoreMenu>

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		select sm
		from StoreMenu sm
		where sm.store.id = :storeId
		  and sm.id = :menuId
		  and sm.isDeleted = false
	""")
	fun findByStoreIdAndIdForUpdate(
		@Param("storeId") storeId: UUID,
		@Param("menuId") menuId: UUID
	): StoreMenu?

	fun findAllByStoreId(storeId: UUID): List<StoreMenu>


	@Query("""
        SELECT m
        FROM StoreMenu m
        JOIN FETCH m.category c
        WHERE m.store.id = :storeId
          AND c.id = :categoryId
        ORDER BY m.sequence ASC
    """)
	fun findMenusByCategoryAndStore(
		@Param("categoryId") categoryId: UUID,
		@Param("storeId") storeId: UUID,
	): List<StoreMenu>
}
