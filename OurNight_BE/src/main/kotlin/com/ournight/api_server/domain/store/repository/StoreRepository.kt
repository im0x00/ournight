package com.ournight.api_server.domain.store.dao

import com.ournight.api_server.domain.store.domain.QStore
import com.ournight.api_server.domain.store.domain.Store
import com.ournight.api_server.domain.store.model.QStoreTimeSlot
import com.ournight.api_server.domain.user.model.User
import com.ournight.api_server.domain.university.model.QUniversity
import com.ournight.api_server.domain.venue.model.QVenue
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.UUID

data class StoreTimeSlotOverlapProjection(
    val storeId: UUID,
    val slotStart: LocalDateTime,
    val slotEnd: LocalDateTime,
)

interface StoreRepository : JpaRepository<Store, UUID>, StoreRepositoryCustom {
    fun findAllByDescriptionStartingWithAndCreatedAtBefore(
        descriptionPrefix: String,
        createdBefore: LocalDateTime,
    ): List<Store>

    fun countByDescriptionStartingWithAndCreatedAtAfter(
        descriptionPrefix: String,
        createdAfter: LocalDateTime,
    ): Long

    fun countByHostId(hostId: UUID): Long

    fun existsByIdAndHostId(id: UUID, hostId: UUID): Boolean

    @EntityGraph(attributePaths = ["timeSlots", "venue"])
    fun findDetailById(id: UUID): java.util.Optional<Store>

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = ["timeSlots", "venue"])
    fun findByHost(host: User): List<Store>

    fun findByHostAndName(host: User, name: String): Store?

    @EntityGraph(attributePaths = ["timeSlots", "venue"])
    @Query(
        "SELECT s FROM Store s WHERE s.host = :host ORDER BY s.createdAt DESC NULLS LAST, s.id DESC",
    )
    fun findByHostOrderByNewestFirst(@Param("host") host: User): List<Store>

    @EntityGraph(attributePaths = ["host", "venue", "venue.university"])
    @Query("SELECT s FROM Store s ORDER BY s.createdAt DESC NULLS LAST, s.id DESC")
    fun findAllForAdminHostTransfer(): List<Store>
}

interface StoreRepositoryCustom {
	fun findActiveStores(): List<Store>
	fun findByNameContaining(name: String): List<Store>
    fun searchActiveStoresByNameOrUniversityContaining(query: String): List<Store>
    fun findActiveStoresByDate(dayStart: LocalDateTime, dayEnd: LocalDateTime): List<Store>
    fun findActiveStoresByBounds(minLat: Double, maxLat: Double, minLng: Double, maxLng: Double): List<Store>
    fun findActiveStoreSlotOverlaps(rangeStart: LocalDateTime, rangeEndExclusive: LocalDateTime): List<StoreTimeSlotOverlapProjection>
}

@Repository
class StoreRepositoryImpl(
	private val queryFactory: JPAQueryFactory
) : StoreRepositoryCustom {

	override fun findActiveStores(): List<Store> {
		val store = QStore.store
		return queryFactory
			.selectFrom(store)
			.where(
                store.isActive.eq(true),
                store.description.startsWith("[POS_EXPERIENCE]").not(),
            )
			.fetch()
	}

	override fun findByNameContaining(name: String): List<Store> {
		val store = QStore.store
		return queryFactory
			.selectFrom(store)
			.where(store.name.containsIgnoreCase(name))
			.fetch()
	}

    override fun searchActiveStoresByNameOrUniversityContaining(query: String): List<Store> {
        val store = QStore.store
        val slot = QStoreTimeSlot.storeTimeSlot
        val venue = QVenue.venue
        val university = QUniversity.university

        return queryFactory
            .selectFrom(store)
            .distinct()
            .join(store.venue, venue).fetchJoin()
            .leftJoin(venue.university, university).fetchJoin()
            .leftJoin(store.timeSlots, slot).fetchJoin()
            .where(
                store.isActive.isTrue,
                store.name.containsIgnoreCase(query)
                    .or(university.name.containsIgnoreCase(query)),
            )
            .fetch()
    }

    override fun findActiveStoresByDate(dayStart: LocalDateTime, dayEnd: LocalDateTime): List<Store> {
        val store = QStore.store
        val slot = QStoreTimeSlot.storeTimeSlot
        val venue = QVenue.venue
        val university = QUniversity.university

        val storeIds = queryFactory
            .select(store.id)
            .from(store)
            .join(store.venue, venue)
            .join(store.timeSlots, slot)
            .where(
                store.isActive.isTrue,
                slot.startTime.lt(dayEnd),
                slot.endTime.gt(dayStart),
            )
            .distinct()
            .fetch()
            .filterNotNull()

        if (storeIds.isEmpty()) {
            return emptyList()
        }

        return queryFactory
            .selectFrom(store)
            .distinct()
            .join(store.venue, venue).fetchJoin()
            .leftJoin(venue.university, university).fetchJoin()
            .leftJoin(store.timeSlots, slot).fetchJoin()
            .where(store.id.`in`(storeIds))
            .fetch()
    }

    override fun findActiveStoresByBounds(
        minLat: Double,
        maxLat: Double,
        minLng: Double,
        maxLng: Double
    ): List<Store> {
        val store = QStore.store
        val slot = QStoreTimeSlot.storeTimeSlot
        val venue = QVenue.venue
        val university = QUniversity.university

        return queryFactory
            .selectFrom(store)
            .distinct()
            .join(store.venue, venue).fetchJoin()
            .leftJoin(venue.university, university).fetchJoin()
            .leftJoin(store.timeSlots, slot).fetchJoin()
            .where(
                store.isActive.isTrue,
                venue.latitude.goe(minLat),
                venue.latitude.loe(maxLat),
                venue.longitude.goe(minLng),
                venue.longitude.loe(maxLng),
            )
            .fetch()
    }

    override fun findActiveStoreSlotOverlaps(
        rangeStart: LocalDateTime,
        rangeEndExclusive: LocalDateTime
    ): List<StoreTimeSlotOverlapProjection> {
        val store = QStore.store
        val slot = QStoreTimeSlot.storeTimeSlot
        val venue = QVenue.venue

        return queryFactory
            .select(
                Projections.constructor(
                    StoreTimeSlotOverlapProjection::class.java,
                    store.id,
                    slot.startTime,
                    slot.endTime,
                )
            )
            .from(store)
            .join(store.venue, venue)
            .join(store.timeSlots, slot)
            .where(
                store.isActive.isTrue,
                slot.startTime.lt(rangeEndExclusive),
                slot.endTime.gt(rangeStart),
            )
            .fetch()
    }
}
