package com.ournight.api_server.domain.guestbook.repository

import com.ournight.api_server.domain.guestbook.model.GuestbookEntry
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface GuestbookEntryRepository : JpaRepository<GuestbookEntry, UUID> {
    @EntityGraph(attributePaths = ["tags", "user"])
    @Query("select g from GuestbookEntry g where g.store.id = :storeId order by g.createdAt desc")
    fun findByStoreIdOrderByCreatedAtDesc(@Param("storeId") storeId: UUID): List<GuestbookEntry>

    @Modifying
    @Query(
        value = """
            delete from guestbook_entry_tags
            where entry_id in (
                select id from guestbook_entries where store_id = :storeId
            )
        """,
        nativeQuery = true,
    )
    fun deleteTagsByStoreId(@Param("storeId") storeId: UUID): Int

    @Modifying
    @Query("delete from GuestbookEntry g where g.store.id = :storeId")
    fun deleteAllByStoreId(@Param("storeId") storeId: UUID): Int
}
