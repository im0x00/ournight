package com.ournight.api_server.domain.store.dao

import com.ournight.api_server.domain.store.domain.StoreTable
import com.ournight.api_server.domain.store.model.StoreTableStatus
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface StoreTableRepository : JpaRepository<StoreTable, UUID> {
	fun countByStoreId(storeId: UUID): Long
	fun countByStoreIdAndStatus(storeId: UUID, status: StoreTableStatus): Long

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select st from StoreTable st join fetch st.store where st.id = :id")
	fun findByIdWithStoreForUpdate(@Param("id") id: UUID): StoreTable?

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select t from StoreTable t where t.id = :tableId")
	fun findByIdForUpdate(tableId: UUID): StoreTable?

	fun findByStoreIdOrderByTableNumberAsc(storeId: UUID): List<StoreTable>

	fun findByStoreIdAndIdInOrderByTableNumberAsc(storeId: UUID, ids: Collection<UUID>): List<StoreTable>

	fun findByStoreIdAndZoneIdOrderByTableNumberAsc(storeId: UUID, zoneId: UUID): List<StoreTable>

	fun findByQrLinkIn(qrLinks: List<String>): List<StoreTable>

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	fun deleteAllByStoreId(storeId: UUID): Int
}
