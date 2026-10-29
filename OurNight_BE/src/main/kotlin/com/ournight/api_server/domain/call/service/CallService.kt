package com.ournight.api_server.domain.call.service

import com.ournight.api_server.api.customer.dto.CustomerCallCreateRequest
import com.ournight.api_server.api.customer.dto.CustomerCallOptionResponse
import com.ournight.api_server.api.host.dto.HostCallOptionUpsertRequest
import com.ournight.api_server.api.host.dto.HallCallRequestResponse
import com.ournight.api_server.domain.call.model.CallOption
import com.ournight.api_server.domain.call.model.CallRequest
import com.ournight.api_server.domain.call.model.CallStatus
import com.ournight.api_server.domain.call.repository.CallOptionRepository
import com.ournight.api_server.domain.call.repository.CallRequestRepository
import com.ournight.api_server.domain.notification.event.CustomerCallEvent
import com.ournight.api_server.domain.notification.event.CustomerCallResolvedEvent
import com.ournight.api_server.domain.pass.repository.PassRepository
import com.ournight.api_server.domain.session.model.SessionStatus
import com.ournight.api_server.domain.session.repository.TableSessionRepository
import com.ournight.api_server.domain.session.service.SessionTimeResolver
import com.ournight.api_server.domain.store.dao.StoreRepository
import com.ournight.api_server.domain.store.dao.StoreTableRepository
import com.ournight.api_server.domain.store.service.StoreTimeSlotService
import com.ournight.api_server.global.error.ErrorCode
import com.ournight.api_server.global.error.exception.BusinessException
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

@Service
@Transactional(readOnly = true)
class CallService(
    private val callOptionRepository: CallOptionRepository,
    private val callRequestRepository: CallRequestRepository,
    private val storeTableRepository: StoreTableRepository,
    private val storeRepository: StoreRepository,
    private val tableSessionRepository: TableSessionRepository,
    private val passRepository: PassRepository,
    private val storeTimeSlotService: StoreTimeSlotService,
    private val sessionTimeResolver: SessionTimeResolver,
    private val eventPublisher: ApplicationEventPublisher,
    private val clock: Clock,
) {

    // --- Host 전용 로직 ---
//    @Transactional
//    fun createCallOption(storeId: UUID, request: HostCallOptionUpsertRequest) {
//        val store = storeRepository.findByIdOrNull(storeId)
//            ?: throw BusinessException(ErrorCode.STORE_NOT_FOUND)
//
//        val option = CallOption(
//            store = store,
//            name = request.name,
//            isActive = request.isActive
//        )
//        callOptionRepository.save(option)
//    }
//
    fun getActiveCallRequests(storeId: UUID): List<HallCallRequestResponse> {
        // 완료되지 않은(PENDING) 호출만 조회
        return callRequestRepository.findAllByStoreIdAndStatusNot(storeId, CallStatus.COMPLETED)
            .map { it.toHallResponse() }
    }

//    @Transactional
//    fun updateCallOption(storeId: UUID, optionId: UUID, request: HostCallOptionUpsertRequest) {
//        val option = callOptionRepository.findByIdOrNull(optionId)
//            ?: throw BusinessException(ErrorCode.CALL_OPTION_NOT_FOUND)
//
//        if (option.store.id != storeId) {
//            throw BusinessException(ErrorCode.UNAUTHORIZED_STORE)
//        }
//
//        option.update(request.name, request.isActive)
//    }

//    @Transactional
//    fun deleteCallOption(storeId: UUID, optionId: UUID) {
//        val option = callOptionRepository.findByIdOrNull(optionId)
//            ?: throw BusinessException(ErrorCode.CALL_OPTION_NOT_FOUND)
//
//        if (option.store.id != storeId) {
//            throw BusinessException(ErrorCode.UNAUTHORIZED_STORE)
//        }
//
//        callOptionRepository.delete(option)
//    }


    // ---- POS 전용 로직 -----
    @Transactional
    fun completeCall(storeId: UUID, requestId: UUID) {
        val request = callRequestRepository.findByIdOrNull(requestId)
            ?: throw BusinessException(ErrorCode.CALL_REQUEST_NOT_FOUND)
        val table = request.table
        val tableStoreId = table.store.id ?: throw BusinessException(ErrorCode.STORE_NOT_FOUND)

        if (tableStoreId != storeId) {
            throw BusinessException(ErrorCode.UNAUTHORIZED_STORE)
        }
        if (request.status == CallStatus.COMPLETED) return

        request.complete()

        eventPublisher.publishEvent(
            CustomerCallResolvedEvent(
                storeId = tableStoreId,
                requestId = requestId,
                sessionId = request.tableSession?.id,
                tableId = table.id,
                tableNumber = table.tableNumber,
                callStatus = request.status,
            )
        )
    }

    // --- Customer 전용 로직 ---

    @Transactional
    fun getAvailableOptions(storeId: UUID): List<CustomerCallOptionResponse> {
        val store = storeRepository.findByIdOrNull(storeId)
            ?: throw BusinessException(ErrorCode.STORE_NOT_FOUND)

        if (callOptionRepository.findAllByStoreId(storeId).isEmpty()) {
            callOptionRepository.saveAll(CallOption.onboardingDefaultsFor(store))
        }

        return callOptionRepository.findAllByStoreIdAndIsActiveTrue(storeId)
            .map { CustomerCallOptionResponse(it.id!!, it.name) }
    }

    @Transactional
    fun makeCall(userId: UUID, request: CustomerCallCreateRequest) {
        val table = storeTableRepository.findByIdWithStoreForUpdate(request.tableId)
            ?: throw BusinessException(ErrorCode.TABLE_NOT_FOUND)
        val tableId = table.id ?: throw BusinessException(ErrorCode.TABLE_NOT_FOUND)
        val storeId = table.store.id ?: throw BusinessException(ErrorCode.STORE_NOT_FOUND)
        val now = LocalDateTime.now(clock)

        val option = callOptionRepository.findByIdOrNull(request.optionId)
            ?: throw BusinessException(ErrorCode.CALL_OPTION_NOT_FOUND)
        if (option.store.id != storeId || !option.isActive) {
            throw BusinessException(ErrorCode.CALL_OPTION_NOT_FOUND)
        }

        val activeSlot = storeTimeSlotService.getActiveSlot(storeId, now)
        val pass = passRepository.findByUserIdAndTimeSlotId(userId, activeSlot.id!!)
            ?: throw BusinessException(ErrorCode.PASS_NOT_FOUND)
        if (!pass.isOrderable(now)) {
            throw BusinessException(ErrorCode.PASS_NOT_ORDERABLE)
        }

        val activeSession = tableSessionRepository.findActiveSessionByTableId(tableId)
            ?: throw BusinessException(ErrorCode.ACTIVE_SESSION_NOT_FOUND)
        if (activeSession.status != SessionStatus.ACTIVE) {
            throw BusinessException(ErrorCode.TIMESLOT_EXPIRED)
        }
        if (activeSession.timeSlot.id != activeSlot.id || sessionTimeResolver.resolve(activeSession, now).timeOver) {
            throw BusinessException(ErrorCode.TIMESLOT_EXPIRED)
        }
        if (activeSession.participants.none { it.pass.id == pass.id }) {
            throw BusinessException(ErrorCode.FORBIDDEN_USER_IN_SESSION)
        }

        val callRequest = CallRequest(
            table = table,
            callOption = option,
            tableSession = activeSession,
            status = CallStatus.PENDING,
            createdAt = LocalDateTime.now(clock),
        )
        val savedRequest = callRequestRepository.save(callRequest)

        eventPublisher.publishEvent(
            CustomerCallEvent(
                storeId = storeId,
                tableId = tableId,
                sessionId = activeSession?.id,
                requestId = savedRequest.id!!,
                tableNumber = table.tableNumber,
                optionId = option.id!!,
                optionName = option.name,
                callStatus = savedRequest.status,
                createdAt = savedRequest.createdAt
            )
        )
    }

    private fun CallRequest.toHallResponse() = HallCallRequestResponse(
        storeId = this.table.store.id!!,
        callRequestId = this.id!!,
        tableId = this.table.id!!,
        sessionId = this.tableSession?.id,
        tableNumber = this.table.tableNumber,
        optionName = this.callOption.name,
        callStatus = this.status,
        createdAt = this.createdAt
    )


}
