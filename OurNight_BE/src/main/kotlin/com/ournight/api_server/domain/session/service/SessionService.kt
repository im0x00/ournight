package com.ournight.api_server.domain.session.service

import com.ournight.api_server.api.customer.dto.CustomerStoreInfo
import com.ournight.api_server.api.customer.dto.CustomerTableResponse
import com.ournight.api_server.api.pos.dto.TableClearResponse
import com.ournight.api_server.api.pos.dto.TableMergeResponse
import com.ournight.api_server.api.pos.dto.TableMoveResponse
import com.ournight.api_server.domain.notification.event.OrdersReassignedToSessionEvent
import com.ournight.api_server.domain.notification.service.TableSummaryEventFactory
import com.ournight.api_server.domain.order.model.OrderStatus
import com.ournight.api_server.domain.order.repository.CartRedisRepository
import com.ournight.api_server.domain.order.repository.OrderRepository
import com.ournight.api_server.domain.pass.model.Pass
import com.ournight.api_server.domain.pass.repository.PassRepository
import com.ournight.api_server.domain.session.model.SessionParticipant
import com.ournight.api_server.domain.session.model.SessionStatus
import com.ournight.api_server.domain.session.model.TableSession
import com.ournight.api_server.domain.session.repository.SessionParticipantRepository
import com.ournight.api_server.domain.session.repository.TableSessionRepository
import com.ournight.api_server.domain.store.application.StoreTableService
import com.ournight.api_server.domain.store.dao.StoreTableRepository
import com.ournight.api_server.domain.store.domain.StoreTable
import com.ournight.api_server.domain.store.model.StoreTableStatus
import com.ournight.api_server.domain.store.model.StoreTimeSlot
import com.ournight.api_server.domain.store.service.StoreTimeSlotService
import com.ournight.api_server.global.error.ErrorCode
import com.ournight.api_server.global.error.exception.BusinessException
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

@Service
@Transactional
class SessionService(
    private val tableSessionRepository: TableSessionRepository,
    private val sessionParticipantRepository: SessionParticipantRepository,
    private val passRepository: PassRepository,
    private val orderRepository: OrderRepository,
    private val cartRedisRepository: CartRedisRepository,
    private val storeTableService: StoreTableService,
    private val storeTimeSlotService: StoreTimeSlotService,
    private val storeTableRepository: StoreTableRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val tableSummaryEventFactory: TableSummaryEventFactory,
    private val sessionTimeResolver: SessionTimeResolver,
    private val clock: Clock,
) {

    private val OPEN_ORDER_STATUSES = setOf(
        OrderStatus.PAYMENT_REQUESTED,
        OrderStatus.PENDING_PAYMENT,
        OrderStatus.CONFIRMED,
    )

    @Transactional
    fun getOrCreateActiveSession(tableId: UUID): TableSession {
        val table = storeTableRepository.findByIdForUpdate(tableId)
            ?: throw BusinessException(ErrorCode.TABLE_STORE_MISMATCH)

        val activeSlot = storeTimeSlotService.getActiveSlot(table.store.id!!)
        val now = LocalDateTime.now(clock)
        val existing = tableSessionRepository.findSimpleActiveSessionByTableId(tableId)
        if (existing != null) {
            if (existing.status != SessionStatus.ACTIVE) {
                throw BusinessException(ErrorCode.ACTIVE_SESSION_NOT_FOUND)
            }
            if (existing.timeSlot.id != activeSlot.id) {
                throw BusinessException(ErrorCode.TIMESLOT_EXPIRED)
            }
            if (existing.expiresAt == null) {
                refreshSessionExpiresAt(existing)
            }
            if (sessionTimeResolver.resolve(existing, now).timeOver) {
                throw BusinessException(ErrorCode.PASS_NOT_ORDERABLE)
            }
            if (table.status != StoreTableStatus.OCCUPIED) {
                table.status = StoreTableStatus.OCCUPIED
            }
            return existing
        }

        table.status = StoreTableStatus.OCCUPIED

        val session = TableSession(
            table = table,
            timeSlot = activeSlot,
            status = SessionStatus.ACTIVE,
            startedAt = now,
            expiresAt = now.plusMinutes(table.store.timeLimit.toLong()),
        )
        return tableSessionRepository.save(session)
    }

    @Transactional(readOnly = true)
    fun findActiveSession(tableId: UUID): TableSession {
        return tableSessionRepository.findActiveSessionByTableId(tableId)
            ?: throw BusinessException(ErrorCode.ACTIVE_SESSION_NOT_FOUND)
    }

    @Transactional(readOnly = true)
    fun findActiveSessionOrNull(tableId: UUID): TableSession? {
        return tableSessionRepository.findActiveSessionByTableId(tableId)
    }


    @Transactional
    fun moveTable(storeId: UUID, fromTableId: UUID, toTableId: UUID): TableMoveResponse {
        // 1. 조회 및 검증 (영속성 컨텍스트 관리 시작)
        val (fromTable, targetTable) = lockTablesInStableOrder(fromTableId, toTableId)

        storeTableService.validateTableBelongsToStore(fromTable, storeId)
        storeTableService.validateTableBelongsToStore(targetTable, storeId)

        val session = findActiveSession(fromTableId)
        require(session.status == SessionStatus.ACTIVE) { "종료되지 않은 session만 이동할 수 있습니다." }

        // 2. 비즈니스 로직 실행
        val openOrders = orderRepository.findBySessionIdAndStatuses(session.id!!, OPEN_ORDER_STATUSES)
        val movedOrderIds = openOrders.mapNotNull { it.id }

        session.moveTo(targetTable)

        // 테이블 물리 상태 변경은 전용 서비스에 위임: 이 안에서 targetTable.occupy()와 fromTable.clear()가 처리됨
        storeTableService.moveTableState(fromTable, targetTable)

        // 3. 이벤트 발행
        if (movedOrderIds.isNotEmpty()) {
            eventPublisher.publishEvent(
                OrdersReassignedToSessionEvent(
                    storeId = storeId,
                    sourceSessionId = session.id!!,
                    targetSessionId = session.id!!,
                    sourceTableId = fromTable.id!!,
                    targetTableId = targetTable.id!!,
                    sourceTableNumber = fromTable.tableNumber,
                    targetTableNumber = targetTable.tableNumber,
                    orderIds = movedOrderIds,
                )
            )
        }

        eventPublisher.publishEvent(tableSummaryEventFactory.create(fromTable.id!!))
        eventPublisher.publishEvent(tableSummaryEventFactory.create(targetTable.id!!))

        // 4. DTO 반환 (엔티티 필드는 트랜잭션 종료 시 Dirty Check로 DB 반영됨)
        return TableMoveResponse(
            sessionId = session.id!!,
            fromTableId = fromTable.id!!,
            toTableId = targetTable.id!!,
            fromTableStatus = fromTable.status.name,
            toTableStatus = targetTable.status.name
        )
    }

    fun addParticipant(
        session: TableSession,
        pass: Pass,
    ): SessionParticipant {
        require(pass.store.id == session.table.store.id) { "pass와 session의 store가 다릅니다." }
        require(pass.isOrderable(LocalDateTime.now(clock))) { "주문 가능한 pass가 아닙니다." }
        require(session.status == SessionStatus.ACTIVE) { "종료된 session에는 참가할 수 없습니다." }

        val existingByPass = sessionParticipantRepository.findByPassId(pass.id!!)
        if (existingByPass != null && existingByPass.session.id != session.id) {
            throw BusinessException(ErrorCode.PASS_ALREADY_JOINED_OTHER_SESSION)
        }

        val existing = sessionParticipantRepository.findBySessionIdAndPassId(
            session.id!!,
            pass.id!!,
        )
        if (existing != null) return existing

        val participant = SessionParticipant(
            session = session,
            pass = pass,
        )

        val saved = sessionParticipantRepository.save(participant)
        session.addParticipant(saved)
        extendSessionExpiresAt(session, pass.expiresAt)
        return saved
    }

    fun enterByQr(tableId: UUID, userId: UUID): CustomerTableResponse {
        val table = storeTableService.getTableForUpdate(tableId)
        joinOrCreateCustomerSession(table.id!!, userId)

        return CustomerTableResponse(
            id = table.id!!,
            tableNumber = table.tableNumber,
            store = CustomerStoreInfo(
                table.store.id,
                table.store.name,
                table.store.description,
                table.store.type,
                table.store.bankName,
                table.store.accountNumber,
            )
        )
    }

    fun requireOrderableParticipant(tableId: UUID, userId: UUID): TableSession {
        val session = tableSessionRepository.findActiveSessionByTableId(tableId)
            ?: throw BusinessException(ErrorCode.ACTIVE_SESSION_NOT_FOUND)
        val storeId = session.table.store.id!!
        val activeSlot = storeTimeSlotService.getActiveSlot(storeId)
        val now = LocalDateTime.now(clock)

        if (session.timeSlot.id != activeSlot.id || sessionTimeResolver.resolve(session, now).timeOver) {
            throw BusinessException(ErrorCode.TIMESLOT_EXPIRED)
        }

        val pass = passRepository.findByUserIdAndTimeSlotId(userId, activeSlot.id!!)
            ?: throw BusinessException(ErrorCode.PASS_NOT_FOUND)

        if (!pass.isOrderable(now)) {
            throw BusinessException(ErrorCode.PASS_NOT_ORDERABLE)
        }
        if (session.participants.none { it.pass.id == pass.id }) {
            throw BusinessException(ErrorCode.FORBIDDEN_USER_IN_SESSION)
        }

        return session
    }

    private fun joinOrCreateCustomerSession(tableId: UUID, userId: UUID): SessionParticipant {
        val table = storeTableService.getTableForUpdate(tableId)
        val activeSlot = storeTimeSlotService.getActiveSlot(table.store.id!!)
        val now = LocalDateTime.now(clock)
        val pass = passRepository.findByUserIdAndTimeSlotId(userId, activeSlot.id!!)
            ?: throw BusinessException(ErrorCode.PASS_NOT_FOUND)

        if (!pass.isOrderable(now)) {
            throw BusinessException(ErrorCode.PASS_NOT_ORDERABLE)
        }

        val existingParticipant = sessionParticipantRepository.findByPassId(pass.id!!)
        val currentSession = existingParticipant?.session

        if (currentSession != null) {
            if (currentSession.expiresAt == null) {
                refreshSessionExpiresAt(currentSession)
            }

            val currentSessionCanContinue =
                currentSession.table.id == table.id &&
                    currentSession.status == SessionStatus.ACTIVE &&
                    currentSession.timeSlot.id == activeSlot.id &&
                    !sessionTimeResolver.resolve(currentSession, now).timeOver

            if (currentSessionCanContinue) {
                return existingParticipant
            }
        }

        val tableId = table.id!!
        val existingTableSession = tableSessionRepository.findSimpleActiveSessionByTableId(tableId)
        var tableSessionCanContinue = false

        if (existingTableSession != null) {
            if (existingTableSession.expiresAt == null) {
                refreshSessionExpiresAt(existingTableSession)
            }

            tableSessionCanContinue =
                existingTableSession.status == SessionStatus.ACTIVE &&
                    existingTableSession.timeSlot.id == activeSlot.id &&
                    !sessionTimeResolver.resolve(existingTableSession, now).timeOver

            if (!tableSessionCanContinue) {
                closeOrderlessSessionOrRequireClear(existingTableSession, now)
            }
        }

        val session = if (existingTableSession != null) {
            if (tableSessionCanContinue) {
                existingTableSession
            } else {
                createCustomerSession(table, activeSlot, pass, now)
            }
        } else {
            createCustomerSession(table, activeSlot, pass, now)
        }

        val participant = if (existingParticipant != null) {
            val previousTableId = currentSession?.table?.id
            existingParticipant.moveTo(session)
            extendSessionExpiresAt(session, pass.expiresAt)
            if (previousTableId != null && previousTableId != tableId) {
                publishTableSummary(previousTableId)
            }
            existingParticipant
        } else {
            addParticipant(session, pass)
        }

        publishTableSummary(tableId)
        return participant
    }

    private fun createCustomerSession(
        table: StoreTable,
        activeSlot: StoreTimeSlot,
        pass: Pass,
        now: LocalDateTime,
    ): TableSession {
        return tableSessionRepository.save(
            TableSession(
                table = table,
                timeSlot = activeSlot,
                status = SessionStatus.ACTIVE,
                startedAt = now,
                expiresAt = pass.expiresAt ?: now.plusMinutes(table.store.timeLimit.toLong()),
            )
        )
    }

    private fun closeOrderlessSessionOrRequireClear(session: TableSession, now: LocalDateTime) {
        val hasAnyOrder = orderRepository.existsBySessionId(session.id!!)
        if (hasAnyOrder) {
            throw BusinessException(ErrorCode.SESSION_REQUIRES_CLEAR)
        }

        val hasCartItems = cartRedisRepository.getSnapshotAtomic(session.table.id!!).items.isNotEmpty()
        if (hasCartItems) {
            throw BusinessException(ErrorCode.SESSION_REQUIRES_CLEAR)
        }

        session.close(now)
    }

    @Transactional
    fun clearTable(storeId: UUID, tableId: UUID, expectedSessionId: UUID): TableClearResponse {
        // 1. 엔티티 조회 및 검증
        val table = storeTableService.getTableForUpdate(tableId)
        storeTableService.validateTableBelongsToStore(table, storeId)

        val activeSession = findActiveSessionOrNull(table.id!!)
            ?: throw BusinessException(ErrorCode.ACTIVE_SESSION_NOT_FOUND)

        if (activeSession.id != expectedSessionId) {
            throw BusinessException(ErrorCode.SESSION_CHANGED)
        }

        val hasOpenOrders = orderRepository.existsBySessionIdAndStatuses(
            activeSession.id!!,
            OPEN_ORDER_STATUSES
        )
        if (hasOpenOrders) {
            throw BusinessException(ErrorCode.TABLE_CLEAR_NOT_ALLOWED_WITH_OPEN_ORDERS)
        }

        // 2. 비즈니스 로직 실행
        activeSession.close(LocalDateTime.now(clock))
        table.clear()

        eventPublisher.publishEvent(tableSummaryEventFactory.create(table.id!!))

        // 3. DTO 직접 반환
        return TableClearResponse(
            tableId = table.id!!,
            tableNumber = table.tableNumber,
            status = table.status.name,
            closedSessionId = activeSession.id,
        )
    }

    @Transactional
    fun mergeTables(storeId: UUID, sourceTableId: UUID, targetTableId: UUID): TableMergeResponse {
        // 1. 엔티티 조회 및 검증
        val (sourceTable, targetTable) = lockTablesInStableOrder(sourceTableId, targetTableId)

        storeTableService.validateTableBelongsToStore(sourceTable, storeId)
        storeTableService.validateTableBelongsToStore(targetTable, storeId)

        val sourceSession = findActiveSession(sourceTableId)
        require(sourceSession.status == SessionStatus.ACTIVE) { "source session이 종료되지 않은 상태가 아닙니다." }

        val targetSession = findActiveSessionOrNull(targetTable.id!!)
            ?: getOrCreateActiveSession(targetTable.id!!)
        require(sourceSession.id != targetSession.id) { "같은 session으로는 merge 할 수 없습니다." }

        // 2. 비즈니스 로직 실행
        val sourceExpiresAtBeforeMerge = resolveSessionExpiresAt(sourceSession)
        val targetExpiresAtBeforeMerge = resolveSessionExpiresAt(targetSession)

        moveParticipantsToTarget(
            sourceSession = sourceSession,
            targetSession = targetSession,
        )

        val reassignableOrders = orderRepository.findBySessionIdAndStatuses(
            sourceSession.id!!,
            OPEN_ORDER_STATUSES
        )

        reassignableOrders.forEach { order ->
            order.reassignTo(targetSession)
        }

        refreshSessionExpiresAt(sourceSession)
        refreshSessionExpiresAt(
            session = targetSession,
            additionalExpiresAt = listOf(sourceExpiresAtBeforeMerge, targetExpiresAtBeforeMerge),
        )

        if (targetTable.status == StoreTableStatus.VACANT) {
            targetTable.occupy()
        }

        closeMergedSourceSessionIfPossible(sourceSession)

        val sourceStillActive = tableSessionRepository.findActiveSessionByTableId(sourceTable.id!!) != null
        if (!sourceStillActive) {
            sourceTable.clear()
        }

        // 3. 이벤트 발행
        val movedOrderIds = reassignableOrders.mapNotNull { it.id }
        if (movedOrderIds.isNotEmpty()) {
            eventPublisher.publishEvent(
                OrdersReassignedToSessionEvent(
                    storeId = storeId,
                    sourceSessionId = sourceSession.id!!,
                    targetSessionId = targetSession.id!!,
                    sourceTableId = sourceTable.id!!,
                    targetTableId = targetTable.id!!,
                    orderIds = movedOrderIds,
                    sourceTableNumber = sourceTable.tableNumber,
                    targetTableNumber = targetTable.tableNumber,
                )
            )
        }

        eventPublisher.publishEvent(tableSummaryEventFactory.create(sourceTable.id!!))
        eventPublisher.publishEvent(tableSummaryEventFactory.create(targetTable.id!!))

        // 4. DTO 직접 반환
        return TableMergeResponse(
            mergedSessionId = targetSession.id!!,
            sourceTableId = sourceTable.id!!,
            targetTableId = targetTable.id!!,
            targetTableStatus = targetTable.status.name,
        )
    }

    private fun moveParticipantsToTarget(
        sourceSession: TableSession,
        targetSession: TableSession,
    ) {
        val sourceParticipants = sessionParticipantRepository.findAllBySessionId(sourceSession.id!!)

        sourceParticipants.forEach { participant ->
            val exists = sessionParticipantRepository.findBySessionIdAndPassId(
                targetSession.id!!,
                participant.pass.id!!
            )

            if (exists == null) {
                participant.moveTo(targetSession)
            }
        }
    }

    private fun closeMergedSourceSessionIfPossible(sourceSession: TableSession) {
        if (sourceSession.status != SessionStatus.ACTIVE) return

        val hasParticipants = sessionParticipantRepository.existsBySessionId(sourceSession.id!!)
        val hasOpenOrders = orderRepository.existsBySessionIdAndStatuses(
            sourceSession.id!!,
            OPEN_ORDER_STATUSES
        )

        if (hasParticipants) {
            throw BusinessException(ErrorCode.SESSION_INVALID_STATE_IN_MERGE)
        }

        if (hasOpenOrders) {
            throw BusinessException(ErrorCode.SESSION_INVALID_STATE_IN_MERGE)
        }

        sourceSession.close(LocalDateTime.now(clock))
    }

    private fun lockTablesInStableOrder(
        firstTableId: UUID,
        secondTableId: UUID,
    ): Pair<StoreTable, StoreTable> {
        require(firstTableId != secondTableId) { "같은 table로는 수행할 수 없습니다." }

        val orderedIds = listOf(firstTableId, secondTableId).sorted()
        val firstLocked = storeTableService.getTableForUpdate(orderedIds[0])
        val secondLocked = storeTableService.getTableForUpdate(orderedIds[1])
        val lockedById = mapOf(
            firstLocked.id!! to firstLocked,
            secondLocked.id!! to secondLocked,
        )

        return Pair(
            lockedById[firstTableId] ?: throw BusinessException(ErrorCode.TABLE_NOT_FOUND),
            lockedById[secondTableId] ?: throw BusinessException(ErrorCode.TABLE_NOT_FOUND),
        )
    }

    private fun refreshSessionExpiresAt(
        session: TableSession,
        additionalExpiresAt: List<LocalDateTime> = emptyList(),
    ) {
        session.expiresAt = resolveSessionExpiresAt(session, additionalExpiresAt)
    }

    private fun extendSessionExpiresAt(
        session: TableSession,
        additionalExpiresAt: LocalDateTime?,
    ) {
        val sessionDefaultExpiresAt = session.startedAt.plusMinutes(session.table.store.timeLimit.toLong())
        session.expiresAt = listOfNotNull(session.expiresAt, additionalExpiresAt)
            .maxOrNull()
            ?: sessionDefaultExpiresAt
    }

    private fun resolveSessionExpiresAt(
        session: TableSession,
        additionalExpiresAt: List<LocalDateTime> = emptyList(),
    ): LocalDateTime {
        val participants = sessionParticipantRepository.findAllBySessionId(session.id!!)
        val sessionDefaultExpiresAt = session.startedAt.plusMinutes(session.table.store.timeLimit.toLong())
        val participantExpiresAt = participants.mapNotNull { it.pass.expiresAt }

        return (participantExpiresAt + additionalExpiresAt)
            .maxOrNull()
            ?: sessionDefaultExpiresAt
    }

    private fun publishTableSummary(tableId: UUID) {
        eventPublisher.publishEvent(tableSummaryEventFactory.create(tableId))
    }

}
