package com.ournight.api_server.global.loadtest

import com.ournight.api_server.domain.pass.model.Pass
import com.ournight.api_server.domain.pass.model.PassStatus
import com.ournight.api_server.domain.pass.repository.PassRepository
import com.ournight.api_server.domain.store.dao.StoreMenuRepository
import com.ournight.api_server.domain.store.dao.StoreRepository
import com.ournight.api_server.domain.store.dao.StoreTableRepository
import com.ournight.api_server.domain.store.domain.Store
import com.ournight.api_server.domain.store.domain.StoreMenu
import com.ournight.api_server.domain.store.domain.StoreType
import com.ournight.api_server.domain.store.domain.StoreTable
import com.ournight.api_server.domain.store.model.StoreMenuCategory
import com.ournight.api_server.domain.store.model.StoreTableZone
import com.ournight.api_server.domain.store.model.StoreTimeSlot
import com.ournight.api_server.domain.store.repository.StoreMenuCategoryRepository
import com.ournight.api_server.domain.store.repository.StoreTableZoneRepository
import com.ournight.api_server.domain.store.repository.StoreTimeSlotRepository
import com.ournight.api_server.domain.user.model.User
import com.ournight.api_server.domain.user.model.UserRole
import com.ournight.api_server.domain.user.repository.UserRepository
import jakarta.transaction.Transactional
import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID
import kotlin.math.ceil

@RestController
@Profile("local", "loadtest")
@RequestMapping("/__load-test")
class LoadTestFixtureController(
    private val userRepository: UserRepository,
    private val storeRepository: StoreRepository,
    private val timeSlotRepository: StoreTimeSlotRepository,
    private val categoryRepository: StoreMenuCategoryRepository,
    private val menuRepository: StoreMenuRepository,
    private val tableZoneRepository: StoreTableZoneRepository,
    private val tableRepository: StoreTableRepository,
    private val passRepository: PassRepository,
    private val clock: Clock,
) {
    @PostMapping("/fixtures/festival")
    @Transactional
    fun createFestivalFixture(@RequestBody request: LoadTestFixtureRequest): LoadTestFixtureResponse {
        val now = LocalDateTime.now(clock)
        val runId = request.runId ?: "lt-${System.currentTimeMillis()}"
        val host = createUser(kakaoId = stableKakao(runId, 0), nickname = "$runId-host")
        val stores = (1..request.storeCount.coerceAtLeast(1)).map { storeNo ->
            val store = storeRepository.save(
                Store(
                    name = "$runId-store-$storeNo",
                    description = "Local load-test fixture",
                    type = StoreType.FESTIVAL,
                    host = host,
                    bankName = "load",
                    accountNumber = "000-$storeNo",
                    isActive = true,
                    timeLimit = 240,
                )
            )
            val slot = timeSlotRepository.save(
                StoreTimeSlot(
                    store = store,
                    startTime = now.minusHours(1),
                    endTime = now.plusHours(6),
                    hasWaiting = false,
                )
            )
            val category = categoryRepository.save(
                StoreMenuCategory(
                    store = store,
                    name = "기본",
                    sequence = 1,
                )
            )
            val menus = listOf(
                createMenu(store, category, "김치볶음밥", 9000, true, 1),
                createMenu(store, category, "오돌뼈", 18000, true, 2),
                createMenu(store, category, "소주", 5000, false, 3),
            )
            val zone = tableZoneRepository.save(
                StoreTableZone(
                    store = store,
                    name = "홀",
                    displayOrder = 1,
                    isActive = true,
                )
            )
            val columns = ceil(kotlin.math.sqrt(request.tablesPerStore.toDouble())).toInt().coerceAtLeast(1)
            val tables = (1..request.tablesPerStore.coerceAtLeast(1)).map { tableNo ->
                tableRepository.save(
                    StoreTable(
                        store = store,
                        tableNumber = tableNo,
                        xPosition = ((tableNo - 1) % columns) * 140.0,
                        yPosition = ((tableNo - 1) / columns) * 120.0,
                        capacity = 4,
                        qrLink = "load-test://$runId/$storeNo/$tableNo",
                        zone = zone,
                    )
                )
            }
            val customers = (1..request.customersPerStore.coerceAtLeast(1)).map { customerNo ->
                val user = createUser(
                    kakaoId = stableKakao(runId, storeNo * 100_000 + customerNo),
                    nickname = "$runId-s$storeNo-c$customerNo",
                )
                val pass = Pass(
                    user = user,
                    timeSlot = slot,
                    status = PassStatus.ACTIVATED,
                    activatedAt = now,
                    expiresAt = now.plusHours(6),
                )
                passRepository.save(pass)
                LoadTestCustomer(userId = user.id!!, passId = pass.id!!)
            }

            LoadTestStoreFixture(
                storeId = store.id!!,
                slotId = slot.id!!,
                menuIds = menus.map { it.id!! },
                tableIds = tables.map { it.id!! },
                customers = customers,
            )
        }

        return LoadTestFixtureResponse(
            runId = runId,
            hostUserId = host.id!!,
            stores = stores,
        )
    }

    private fun createUser(kakaoId: Long, nickname: String): User {
        return userRepository.findByKakaoId(kakaoId)
            ?: userRepository.save(User(kakaoId = kakaoId, nickname = nickname, role = UserRole.USER))
    }

    private fun createMenu(
        store: Store,
        category: StoreMenuCategory,
        name: String,
        price: Int,
        requiresCooking: Boolean,
        sequence: Int,
    ): StoreMenu {
        return menuRepository.save(
            StoreMenu(
                store = store,
                category = category,
                name = name,
                salePrice = BigDecimal.valueOf(price.toLong()),
                description = "load-test menu",
                imageUrl = null,
                sequence = sequence,
                requiresCooking = requiresCooking,
            )
        )
    }

    private fun stableKakao(runId: String, suffix: Int): Long {
        val base = runId.fold(1_700_000_000L) { acc, c -> (acc * 31 + c.code) % 8_000_000_000L }
        return 1_000_000_000L + base + suffix
    }
}

data class LoadTestFixtureRequest(
    val runId: String? = null,
    val storeCount: Int = 1,
    val tablesPerStore: Int = 30,
    val customersPerStore: Int = 500,
)

data class LoadTestFixtureResponse(
    val runId: String,
    val hostUserId: UUID,
    val stores: List<LoadTestStoreFixture>,
)

data class LoadTestStoreFixture(
    val storeId: UUID,
    val slotId: UUID,
    val menuIds: List<UUID>,
    val tableIds: List<UUID>,
    val customers: List<LoadTestCustomer>,
)

data class LoadTestCustomer(
    val userId: UUID,
    val passId: UUID,
)
