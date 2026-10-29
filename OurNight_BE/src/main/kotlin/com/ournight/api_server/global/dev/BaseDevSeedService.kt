package com.ournight.api_server.global.dev

import com.ournight.api_server.domain.store.dao.StoreRepository
import com.ournight.api_server.domain.store.domain.Store
import com.ournight.api_server.domain.store.domain.StoreType
import com.ournight.api_server.domain.store.model.StoreTimeSlot
import com.ournight.api_server.domain.store.repository.StoreTimeSlotRepository
import com.ournight.api_server.domain.user.model.User
import com.ournight.api_server.domain.user.model.UserRole
import com.ournight.api_server.domain.user.repository.UserRepository
import com.ournight.api_server.domain.venue.model.Venue
import com.ournight.api_server.domain.venue.repository.VenueRepository
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.cos
import kotlin.math.sin

data class BaseDevSeedSummary(
    val createdCount: Int,
    val reusedCount: Int,
    val details: Map<String, Int>,
)

@Service
class BaseDevSeedService(
    private val universityDevSeedService: UniversityDevSeedService,
    private val venueRepository: VenueRepository,
    private val userRepository: UserRepository,
    private val storeRepository: StoreRepository,
    private val storeTimeSlotRepository: StoreTimeSlotRepository,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(BaseDevSeedService::class.java)

    @Transactional
    fun seed(): BaseDevSeedSummary {
        var createdCount = 0
        var reusedCount = 0
        val details = linkedMapOf<String, Int>()

        val universityStats = universityDevSeedService.seed()
        createdCount += universityStats.createdCount
        reusedCount += universityStats.reusedCount
        details["universityCreated"] = universityStats.createdCount
        details["universityReused"] = universityStats.reusedCount

        val hostUpsert = upsertSeedHost()
        if (hostUpsert.created) createdCount++ else reusedCount++
        details["hostCreated"] = if (hostUpsert.created) 1 else 0
        details["hostReused"] = if (hostUpsert.created) 0 else 1

        val universities = universityStats.universitiesByName
        var venueCreated = 0
        var venueReused = 0

        val today = LocalDate.now(clock)
        val seedRows = buildSeedRows(today)

        var storeCreated = 0
        var storeReused = 0
        var slotCreated = 0
        var slotReused = 0

        seedRows.forEach { row ->
            val venueUpsert = upsertVenue(row.venueTemplate, universities)
            val venue = venueUpsert.entity
            if (venueUpsert.created) venueCreated++ else venueReused++

            val storeUpsert = upsertStore(hostUpsert.entity, venue, row.storeTemplate)
            if (storeUpsert.created) storeCreated++ else storeReused++

            row.storeTemplate.slots.forEach { slotTemplate ->
                val slotUpsert = upsertTimeSlot(storeUpsert.entity, slotTemplate)
                if (slotUpsert.created) slotCreated++ else slotReused++
            }
        }

        createdCount += venueCreated
        reusedCount += venueReused
        details["venueCreated"] = venueCreated
        details["venueReused"] = venueReused

        createdCount += storeCreated + slotCreated
        reusedCount += storeReused + slotReused
        details["storeCreated"] = storeCreated
        details["storeReused"] = storeReused
        details["slotCreated"] = slotCreated
        details["slotReused"] = slotReused

        log.info(
            "[BaseDevSeedService] seed completed. created={}, reused={}, details={}",
            createdCount,
            reusedCount,
            details,
        )

        return BaseDevSeedSummary(
            createdCount = createdCount,
            reusedCount = reusedCount,
            details = details,
        )
    }

    private fun buildSeedRows(today: LocalDate): List<SeedRow> {
        return buildCampusRows(
            campusLabel = "한양",
            universityName = "한양대학교",
            district = "성동구",
            road = "왕십리로",
            centerLat = 37.5566087,
            centerLng = 127.0466647,
            storeCount = 40,
            today = today,
        ) + buildCampusRows(
            campusLabel = "세종",
            universityName = "세종대학교",
            district = "광진구",
            road = "능동로",
            centerLat = 37.55080,
            centerLng = 127.07423,
            storeCount = 30,
            today = today,
        )
    }

    private fun buildCampusRows(
        campusLabel: String,
        universityName: String,
        district: String,
        road: String,
        centerLat: Double,
        centerLng: Double,
        storeCount: Int,
        today: LocalDate,
    ): List<SeedRow> {
        val rows = mutableListOf<SeedRow>()
        for (index in 1..storeCount) {
            val suffix = index.toString().padStart(2, '0')
            val spread = calculateSpreadCoordinate(centerLat, centerLng, index)
            val venueTemplate = VenueTemplate(
                name = "BASE $campusLabel 베뉴 $suffix",
                address = "서울 $district $road ${200 + index} BASE $campusLabel 베뉴 $suffix",
                latitude = spread.latitude,
                longitude = spread.longitude,
                universityName = universityName,
            )

            val firstSlotStart = today.plusDays((index % 5).toLong()).atTime(17 + (index % 3), 0)
            val secondSlotStart = today.plusDays(((index + 1) % 5).toLong()).atTime(12 + (index % 4), 0)
            val storeTemplate = StoreTemplate(
                name = "BASE $campusLabel 스토어 $suffix",
                description = "Base seed - $campusLabel 지역 기본 스토어",
                type = if (index % 4 == 0) StoreType.RENTAL else StoreType.FESTIVAL,
                isActive = true,
                slots = listOf(
                    SlotTemplate(firstSlotStart, firstSlotStart.plusHours(4)),
                    SlotTemplate(secondSlotStart, secondSlotStart.plusHours(3)),
                ),
            )
            rows += SeedRow(venueTemplate = venueTemplate, storeTemplate = storeTemplate)
        }
        return rows
    }

    private fun calculateSpreadCoordinate(centerLat: Double, centerLng: Double, index: Int): Coordinate {
        val angle = ((index * 37) % 360) * (Math.PI / 180.0)
        val radius = 0.00045 + ((index % 6) * 0.00012)
        val latitude = centerLat + radius * sin(angle)
        val longitude = centerLng + radius * cos(angle)
        return Coordinate(latitude = latitude, longitude = longitude)
    }

    private fun upsertSeedHost(): UpsertResult<User> {
        val existing = userRepository.findByKakaoId(BASE_HOST_KAKAO_ID)
        if (existing != null) {
            existing.nickname = BASE_HOST_NICKNAME
            val saved = userRepository.save(existing)
            return UpsertResult(entity = saved, created = false)
        }

        val created = userRepository.save(
            User(
                kakaoId = BASE_HOST_KAKAO_ID,
                nickname = BASE_HOST_NICKNAME,
                role = UserRole.USER,
            )
        )
        return UpsertResult(entity = created, created = true)
    }

    private fun upsertVenue(template: VenueTemplate, universities: Map<String, com.ournight.api_server.domain.university.model.University>): UpsertResult<Venue> {
        val university = universities[template.universityName]
            ?: error("university not found for ${template.universityName}")
        val existing = venueRepository.findByNameAndAddress(template.name, template.address)

        if (existing != null) {
            existing.latitude = template.latitude
            existing.longitude = template.longitude
            existing.university = university
            existing.isPartner = false
            existing.detailLocation = template.detailLocation
            val updated = venueRepository.save(existing)
            return UpsertResult(entity = updated, created = false)
        }

        val created = venueRepository.save(
            Venue(
                name = template.name,
                address = template.address,
                latitude = template.latitude,
                longitude = template.longitude,
                isPartner = false,
                detailLocation = template.detailLocation,
                university = university,
            )
        )
        return UpsertResult(entity = created, created = true)
    }

    private fun upsertStore(host: User, venue: Venue, template: StoreTemplate): UpsertResult<Store> {
        val existing = storeRepository.findByHostAndName(host, template.name)
        if (existing != null) {
            existing.description = template.description
            existing.type = template.type
            existing.isActive = template.isActive
            existing.venue = venue
            existing.hostContact = BASE_HOST_CONTACT
            existing.bankName = BASE_BANK_NAME
            existing.accountNumber = BASE_ACCOUNT_NUMBER
            existing.timeLimit = BASE_TIME_LIMIT
            val updated = storeRepository.save(existing)
            return UpsertResult(entity = updated, created = false)
        }

        val created = storeRepository.save(
            Store(
                name = template.name,
                description = template.description,
                type = template.type,
                host = host,
                venue = venue,
                isActive = template.isActive,
                hostContact = BASE_HOST_CONTACT,
                bankName = BASE_BANK_NAME,
                accountNumber = BASE_ACCOUNT_NUMBER,
                timeLimit = BASE_TIME_LIMIT,
            )
        )
        return UpsertResult(entity = created, created = true)
    }

    private fun upsertTimeSlot(store: Store, template: SlotTemplate): UpsertResult<StoreTimeSlot> {
        val storeId = store.id ?: error("store id is null")
        val existing = storeTimeSlotRepository.findByStore_IdAndStartTimeAndEndTime(
            storeId = storeId,
            startTime = template.startTime,
            endTime = template.endTime,
        )

        if (existing != null) {
            return UpsertResult(entity = existing, created = false)
        }

        val created = storeTimeSlotRepository.save(
            StoreTimeSlot(
                store = store,
                startTime = template.startTime,
                endTime = template.endTime,
                hasWaiting = false,
            )
        )
        return UpsertResult(entity = created, created = true)
    }

    private data class VenueTemplate(
        val name: String,
        val address: String,
        val latitude: Double,
        val longitude: Double,
        val universityName: String,
        val detailLocation: String? = null,
    )

    private data class StoreTemplate(
        val name: String,
        val description: String,
        val type: StoreType,
        val isActive: Boolean,
        val slots: List<SlotTemplate>,
    )

    private data class SeedRow(
        val venueTemplate: VenueTemplate,
        val storeTemplate: StoreTemplate,
    )

    private data class Coordinate(
        val latitude: Double,
        val longitude: Double,
    )

    private data class SlotTemplate(
        val startTime: LocalDateTime,
        val endTime: LocalDateTime,
    )

    private data class UpsertResult<T>(
        val entity: T,
        val created: Boolean,
    )

    companion object {
        private const val BASE_HOST_KAKAO_ID = 888000111L
        private const val BASE_HOST_NICKNAME = "BASE_SEED_HOST"
        private const val BASE_HOST_CONTACT = "010-0000-0000"
        private const val BASE_BANK_NAME = "카카오뱅크"
        private const val BASE_ACCOUNT_NUMBER = "3333-01-0000000"
        private const val BASE_TIME_LIMIT = 120
    }
}
