package com.ournight.api_server.domain.store.repository

import com.ournight.api_server.domain.store.model.StoreScrap
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface StoreScrapRepository : JpaRepository<StoreScrap, UUID> {
    fun findByUserIdAndStoreId(userId: UUID, storeId: UUID): StoreScrap?

    @Query(
        """
        select ss.store.id
        from StoreScrap ss
        where ss.user.id = :userId
          and ss.store.id in :storeIds
        """
    )
    fun findScrappedStoreIdsByUserIdAndStoreIds(
        @Param("userId") userId: UUID,
        @Param("storeIds") storeIds: Collection<UUID>,
    ): List<UUID>

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        delete from StoreScrap ss
        where ss.user.id = :userId and ss.store.id = :storeId
        """
    )
    fun deleteByUserIdAndStoreId(
        @Param("userId") userId: UUID,
        @Param("storeId") storeId: UUID,
    ): Int

    @Query(
        """
        select distinct ss
        from StoreScrap ss
        join fetch ss.store s
        join fetch s.venue v
        left join fetch v.university
        left join fetch s.timeSlots
        where ss.user.id = :userId
          and s.isActive = true
        order by ss.createdAt desc
        """
    )
    fun findActiveScrapsByUserIdWithStore(@Param("userId") userId: UUID): List<StoreScrap>

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        delete from StoreScrap ss
        where ss.store.id = :storeId
        """
    )
    fun deleteAllByStoreId(@Param("storeId") storeId: UUID): Int
}
