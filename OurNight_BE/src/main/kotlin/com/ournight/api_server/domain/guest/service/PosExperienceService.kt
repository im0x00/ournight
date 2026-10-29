package com.ournight.api_server.domain.guest.service

import com.ournight.api_server.api.guest.dto.PosExperienceResponse
import com.ournight.api_server.domain.auth.model.RefreshToken
import com.ournight.api_server.domain.auth.repository.RefreshTokenRepository
import com.ournight.api_server.domain.pass.model.Pass
import com.ournight.api_server.domain.pass.repository.PassRepository
import com.ournight.api_server.domain.session.model.SessionStatus
import com.ournight.api_server.domain.session.model.SessionParticipant
import com.ournight.api_server.domain.session.model.TableSession
import com.ournight.api_server.domain.session.repository.SessionParticipantRepository
import com.ournight.api_server.domain.session.repository.TableSessionRepository
import com.ournight.api_server.domain.store.dao.StoreMenuRepository
import com.ournight.api_server.domain.store.dao.StoreRepository
import com.ournight.api_server.domain.store.dao.StoreTableRepository
import com.ournight.api_server.domain.store.domain.Store
import com.ournight.api_server.domain.store.domain.StoreMenu
import com.ournight.api_server.domain.store.domain.StoreTable
import com.ournight.api_server.domain.store.domain.StoreType
import com.ournight.api_server.domain.store.model.StoreMenuCategory
import com.ournight.api_server.domain.store.model.StoreTableZone
import com.ournight.api_server.domain.store.model.StoreTimeSlot
import com.ournight.api_server.domain.store.repository.StoreMenuCategoryRepository
import com.ournight.api_server.domain.store.repository.StoreTableZoneRepository
import com.ournight.api_server.domain.store.repository.StoreTimeSlotRepository
import com.ournight.api_server.domain.user.model.User
import com.ournight.api_server.domain.user.model.UserAuthType
import com.ournight.api_server.domain.user.model.UserRole
import com.ournight.api_server.domain.user.repository.UserRepository
import com.ournight.api_server.global.error.ErrorCode
import com.ournight.api_server.global.error.exception.BusinessException
import com.ournight.api_server.global.jwt.JwtTokenProvider
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

const val POS_EXPERIENCE_MARKER = "[POS_EXPERIENCE]"
const val POS_EXPERIENCE_TTL_MINUTES = 120L
private const val MAX_ACTIVE_EXPERIENCES = 100L

@Service
class PosExperienceService(
    private val userRepository: UserRepository,
    private val storeRepository: StoreRepository,
    private val tableRepository: StoreTableRepository,
    private val menuRepository: StoreMenuRepository,
    private val categoryRepository: StoreMenuCategoryRepository,
    private val tableZoneRepository: StoreTableZoneRepository,
    private val timeSlotRepository: StoreTimeSlotRepository,
    private val sessionRepository: TableSessionRepository,
    private val passRepository: PassRepository,
    private val participantRepository: SessionParticipantRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val jwtTokenProvider: JwtTokenProvider,
    private val clock: Clock,
) {
    @Transactional
    fun create(): PosExperienceResponse {
        val now = LocalDateTime.now(clock)
        val activeCount = storeRepository.countByDescriptionStartingWithAndCreatedAtAfter(
            POS_EXPERIENCE_MARKER,
            now.minusMinutes(POS_EXPERIENCE_TTL_MINUTES),
        )
        if (activeCount >= MAX_ACTIVE_EXPERIENCES) {
            throw BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED, "현재 체험 공간이 모두 사용 중입니다. 잠시 후 다시 시도해 주세요.")
        }

        val suffix = UUID.randomUUID().toString().take(8)
        val posUser = userRepository.save(
            User(
                nickname = "POS 체험자-$suffix",
                role = UserRole.USER,
                authType = UserAuthType.GUEST,
            ),
        )
        val store = storeRepository.save(
            Store(
                name = "OurNight POS 체험 매장",
                description = "$POS_EXPERIENCE_MARKER 방문자별 격리된 실제 POS 체험 공간",
                hostContact = "experience",
                type = StoreType.FESTIVAL,
                host = posUser,
                bankName = "체험은행",
                accountNumber = "000-0000-0000",
                isActive = true,
                timeLimit = 180,
            ),
        )
        val slot = timeSlotRepository.save(
            StoreTimeSlot(
                store = store,
                startTime = now.minusMinutes(10),
                endTime = now.plusMinutes(POS_EXPERIENCE_TTL_MINUTES),
                hasWaiting = false,
            ),
        )
        val category = categoryRepository.save(
            StoreMenuCategory(store = store, name = "체험 메뉴", sequence = 1),
        )
        createMenu(store, category, "김치볶음밥", 9_000, true, 1)
        createMenu(store, category, "콜라", 3_000, false, 2)
        val mainHall = tableZoneRepository.save(
            StoreTableZone(
                store = store,
                name = "메인 홀",
                displayOrder = 1,
                isActive = true,
            ),
        )
        val tables = tableRepository.saveAll(
            listOf(
                createTable(store, mainHall, tableNumber = 1, xPosition = 0.12, yPosition = 0.18),
                createTable(store, mainHall, tableNumber = 2, xPosition = 0.43, yPosition = 0.18),
                createTable(store, mainHall, tableNumber = 3, xPosition = 0.74, yPosition = 0.18),
            ),
        )
        val table = tables.first()
        table.occupy()
        val session = sessionRepository.save(
            TableSession(
                table = table,
                timeSlot = slot,
                status = SessionStatus.ACTIVE,
                startedAt = now,
                expiresAt = now.plusMinutes(POS_EXPERIENCE_TTL_MINUTES),
                currentOrderSequence = 0,
            ),
        )
        val customerUser = userRepository.save(
            User(
                nickname = "주문 체험자-$suffix",
                role = UserRole.GUEST,
                authType = UserAuthType.GUEST,
            ),
        )
        val pass = Pass(user = customerUser, timeSlot = slot).also {
            it.activate(now, POS_EXPERIENCE_TTL_MINUTES)
        }.let(passRepository::save)
        participantRepository.save(
            SessionParticipant(session = session, pass = pass),
        )

        val posUserId = requireNotNull(posUser.id)
        val accessToken = jwtTokenProvider.createAccessToken(posUserId, posUser.role, posUser.authType)
        val refreshToken = jwtTokenProvider.createRefreshToken(posUserId)
        refreshTokenRepository.save(RefreshToken.from(posUserId, refreshToken))
        val customerUserId = requireNotNull(customerUser.id)
        val customerAccessToken = jwtTokenProvider.createAccessToken(
            customerUserId,
            customerUser.role,
            customerUser.authType,
        )

        return PosExperienceResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
            userId = posUserId,
            nickname = posUser.nickname,
            storeId = requireNotNull(store.id),
            tableId = requireNotNull(table.id),
            customerAccessToken = customerAccessToken,
            customerUserId = customerUserId,
            customerNickname = customerUser.nickname,
            expiresInMinutes = POS_EXPERIENCE_TTL_MINUTES,
        )
    }

    private fun createMenu(
        store: Store,
        category: StoreMenuCategory,
        name: String,
        price: Long,
        requiresCooking: Boolean,
        sequence: Int,
    ): StoreMenu = menuRepository.save(
        StoreMenu(
            category = category,
            store = store,
            name = name,
            salePrice = BigDecimal.valueOf(price),
            description = "실제 POS 흐름 확인용 메뉴",
            sequence = sequence,
            requiresCooking = requiresCooking,
        ),
    )

    private fun createTable(
        store: Store,
        zone: StoreTableZone,
        tableNumber: Int,
        xPosition: Double,
        yPosition: Double,
    ): StoreTable = StoreTable(
        store = store,
        tableNumber = tableNumber,
        xPosition = xPosition,
        yPosition = yPosition,
        capacity = 4,
        zone = zone,
    )
}
