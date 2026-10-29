package com.ournight.api_server.global.dev

import com.ournight.api_server.domain.store.dao.StoreRepository
import com.ournight.api_server.domain.store.domain.Store
import com.ournight.api_server.domain.store.domain.StoreType
import com.ournight.api_server.domain.store.model.StoreTimeSlot
import com.ournight.api_server.domain.store.repository.StoreTimeSlotRepository
import com.ournight.api_server.domain.university.model.University
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

data class DevScenarioSeedSummary(
    val scenario: String,
    val createdCount: Int,
    val reusedCount: Int,
    val details: Map<String, Int>,
    val message: String,
)

@Service
class DevScenarioSeedService(
    private val baseDevSeedService: BaseDevSeedService,
    private val posDevSeedService: PosDevSeedService,
    private val universityDevSeedService: UniversityDevSeedService,
    private val userRepository: UserRepository,
    private val venueRepository: VenueRepository,
    private val storeRepository: StoreRepository,
    private val storeTimeSlotRepository: StoreTimeSlotRepository,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(DevScenarioSeedService::class.java)

    @Transactional
    fun seedStoreDiscoveryScenario(): DevScenarioSeedSummary {
        val baseSummary = baseDevSeedService.seed()
        val universityStats = universityDevSeedService.seed()

        var createdCount = baseSummary.createdCount
        var reusedCount = baseSummary.reusedCount + universityStats.reusedCount
        val details = linkedMapOf<String, Int>()
        details.putAll(baseSummary.details)

        val hostUpsert = upsertScenarioHost()
        if (hostUpsert.created) createdCount++ else reusedCount++

        val hanyang = universityStats.universitiesByName["한양대학교"]
            ?: error("한양대학교 not found")
        val sejong = universityStats.universitiesByName["세종대학교"]
            ?: error("세종대학교 not found")

        var venueCreated = 0
        var venueReused = 0
        val venueMain = upsertVenue(
            name = "SCENARIO 한양 플라자",
            address = "서울 성동구 왕십리로 222 SCENARIO 한양 플라자",
            latitude = 37.55390,
            longitude = 127.04010,
            university = hanyang,
        ).also { if (it.created) venueCreated++ else venueReused++ }.entity

        val venueBlank = upsertVenue(
            name = "SCENARIO 공백주소 베뉴",
            address = "   ",
            latitude = 37.55400,
            longitude = 127.04020,
            university = hanyang,
        ).also { if (it.created) venueCreated++ else venueReused++ }.entity

        val venueInactive = upsertVenue(
            name = "SCENARIO 비활성 베뉴",
            address = "서울 광진구 능동로 209 SCENARIO 비활성 베뉴",
            latitude = 37.55081,
            longitude = 127.07420,
            university = sejong,
        ).also { if (it.created) venueCreated++ else venueReused++ }.entity

        createdCount += venueCreated
        reusedCount += venueReused
        details["scenarioVenueCreated"] = venueCreated
        details["scenarioVenueReused"] = venueReused

        val today = LocalDate.now(clock)
        var storeCreated = 0
        var storeReused = 0
        var slotCreated = 0
        var slotReused = 0

        val multiDayStore = upsertStore(
            host = hostUpsert.entity,
            name = "SCENARIO 다일자 주점",
            description = "store-discovery 시나리오: 다일자 겹침",
            venue = venueMain,
            isActive = true,
            type = StoreType.FESTIVAL,
        ).also { if (it.created) storeCreated++ else storeReused++ }.entity

        listOf(
            SlotTemplate(today.atTime(5, 0), today.plusDays(2).atTime(21, 0)),
            SlotTemplate(today.plusDays(1).atTime(17, 0), today.plusDays(1).atTime(23, 0)),
        ).forEach { slot ->
            val upsert = upsertSlot(multiDayStore, slot)
            if (upsert.created) slotCreated++ else slotReused++
        }

        val blankAddressStore = upsertStore(
            host = hostUpsert.entity,
            name = "SCENARIO 공백주소 주점",
            description = "store-discovery 시나리오: 공백 주소 제외 대상",
            venue = venueBlank,
            isActive = true,
            type = StoreType.FESTIVAL,
        ).also { if (it.created) storeCreated++ else storeReused++ }.entity
        val blankSlot = upsertSlot(
            blankAddressStore,
            SlotTemplate(today.plusDays(1).atTime(18, 0), today.plusDays(1).atTime(22, 0)),
        )
        if (blankSlot.created) slotCreated++ else slotReused++

        val inactiveStore = upsertStore(
            host = hostUpsert.entity,
            name = "SCENARIO 비활성 주점",
            description = "store-discovery 시나리오: 비활성 제외 대상",
            venue = venueInactive,
            isActive = false,
            type = StoreType.RENTAL,
        ).also { if (it.created) storeCreated++ else storeReused++ }.entity
        val inactiveSlot = upsertSlot(
            inactiveStore,
            SlotTemplate(today.plusDays(1).atTime(19, 0), today.plusDays(1).atTime(21, 0)),
        )
        if (inactiveSlot.created) slotCreated++ else slotReused++

        createdCount += storeCreated + slotCreated
        reusedCount += storeReused + slotReused
        details["scenarioStoreCreated"] = storeCreated
        details["scenarioStoreReused"] = storeReused
        details["scenarioSlotCreated"] = slotCreated
        details["scenarioSlotReused"] = slotReused

        log.info(
            "[DevScenarioSeedService] store-discovery scenario completed. created={}, reused={}",
            createdCount,
            reusedCount,
        )

        return DevScenarioSeedSummary(
            scenario = "store-discovery",
            createdCount = createdCount,
            reusedCount = reusedCount,
            details = details,
            message = "store-discovery scenario seed completed",
        )
    }

    @Transactional
    fun seedPosScenario(): DevScenarioSeedSummary {
        val baseSummary = baseDevSeedService.seed()
        val posSummary = posDevSeedService.seed()

        val details = linkedMapOf<String, Int>()
        details.putAll(baseSummary.details)
        details["posExecuted"] = if (posSummary.executed) 1 else 0

        return DevScenarioSeedSummary(
            scenario = "pos",
            createdCount = baseSummary.createdCount + if (posSummary.executed) 1 else 0,
            reusedCount = baseSummary.reusedCount + if (posSummary.executed) 0 else 1,
            details = details,
            message = posSummary.message,
        )
    }

    private fun upsertScenarioHost(): UpsertResult<User> {
        val existing = userRepository.findByKakaoId(SCENARIO_HOST_KAKAO_ID)
        if (existing != null) {
            existing.nickname = SCENARIO_HOST_NICKNAME
            val updated = userRepository.save(existing)
            return UpsertResult(updated, false)
        }
        val created = userRepository.save(
            User(
                kakaoId = SCENARIO_HOST_KAKAO_ID,
                nickname = SCENARIO_HOST_NICKNAME,
                role = UserRole.USER,
            )
        )
        return UpsertResult(created, true)
    }

    private fun upsertVenue(
        name: String,
        address: String,
        latitude: Double,
        longitude: Double,
        university: University,
    ): UpsertResult<Venue> {
        val existing = venueRepository.findByNameAndAddress(name, address)
        if (existing != null) {
            existing.latitude = latitude
            existing.longitude = longitude
            existing.university = university
            val updated = venueRepository.save(existing)
            return UpsertResult(updated, false)
        }
        val created = venueRepository.save(
            Venue(
                name = name,
                address = address,
                latitude = latitude,
                longitude = longitude,
                isPartner = false,
                university = university,
            )
        )
        return UpsertResult(created, true)
    }

    private fun upsertStore(
        host: User,
        name: String,
        description: String,
        venue: Venue,
        isActive: Boolean,
        type: StoreType,
    ): UpsertResult<Store> {
        val existing = storeRepository.findByHostAndName(host, name)
        if (existing != null) {
            existing.description = description
            existing.venue = venue
            existing.isActive = isActive
            existing.type = type
            existing.hostContact = "010-9999-9999"
            existing.bankName = "카카오뱅크"
            existing.accountNumber = "3333-02-9999999"
            val updated = storeRepository.save(existing)
            return UpsertResult(updated, false)
        }
        val created = storeRepository.save(
            Store(
                name = name,
                description = description,
                type = type,
                host = host,
                venue = venue,
                isActive = isActive,
                hostContact = "010-9999-9999",
                bankName = "카카오뱅크",
                accountNumber = "3333-02-9999999",
            )
        )
        return UpsertResult(created, true)
    }

    private fun upsertSlot(store: Store, slotTemplate: SlotTemplate): UpsertResult<StoreTimeSlot> {
        val storeId = store.id ?: error("store id is null")
        val existing = storeTimeSlotRepository.findByStore_IdAndStartTimeAndEndTime(
            storeId = storeId,
            startTime = slotTemplate.startTime,
            endTime = slotTemplate.endTime,
        )
        if (existing != null) {
            return UpsertResult(existing, false)
        }
        val created = storeTimeSlotRepository.save(
            StoreTimeSlot(
                store = store,
                startTime = slotTemplate.startTime,
                endTime = slotTemplate.endTime,
                hasWaiting = false,
            )
        )
        return UpsertResult(created, true)
    }

    private data class SlotTemplate(
        val startTime: LocalDateTime,
        val endTime: LocalDateTime,
    )

    private data class UpsertResult<T>(
        val entity: T,
        val created: Boolean,
    )

    companion object {
        private const val SCENARIO_HOST_KAKAO_ID = 888000222L
        private const val SCENARIO_HOST_NICKNAME = "SCENARIO_SEED_HOST"
    }
}
