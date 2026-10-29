package com.ournight.api_server.domain.store.repository

import com.ournight.api_server.domain.store.model.StoreTableZone
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface StoreTableZoneRepository : JpaRepository<StoreTableZone, UUID> {
    fun findByStoreIdOrderByDisplayOrderAsc(storeId: UUID): List<StoreTableZone>
    fun existsByStoreIdAndName(storeId: UUID, name: String): Boolean
}