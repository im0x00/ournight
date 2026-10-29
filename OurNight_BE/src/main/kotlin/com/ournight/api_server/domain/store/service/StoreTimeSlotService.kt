package com.ournight.api_server.domain.store.service

import com.ournight.api_server.api.host.dto.StoreTimeSlotItemResponse
import com.ournight.api_server.api.host.dto.StoreTimeSlotsResponse
import com.ournight.api_server.domain.store.dao.StoreRepository
import com.ournight.api_server.domain.store.dao.StoreTableRepository
import com.ournight.api_server.domain.store.model.StoreTableStatus
import com.ournight.api_server.domain.store.model.StoreTimeSlot
import com.ournight.api_server.domain.store.repository.StoreTimeSlotRepository
import com.ournight.api_server.domain.store.view.DoorStatusView
import com.ournight.api_server.domain.store.view.StoreCongestionLevel
import com.ournight.api_server.domain.user.model.User
import com.ournight.api_server.global.error.ErrorCode
import com.ournight.api_server.global.error.exception.BusinessException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Service
@Transactional(readOnly = true)
class StoreTimeSlotService(
    private val storeTimeSlotRepository: StoreTimeSlotRepository,
    private val storeTableRepository: StoreTableRepository,
    private val storeRepository: StoreRepository,
    private val clock: Clock,
) {

    fun getActiveSlot(storeId: UUID): StoreTimeSlot {
        return getActiveSlot(storeId, LocalDateTime.now(clock))
    }

    fun findActiveSlotOrNull(storeId: UUID): StoreTimeSlot? {
        return findActiveSlotOrNull(storeId, LocalDateTime.now(clock))
    }

    fun findActiveSlotOrNull(storeId: UUID, now: LocalDateTime): StoreTimeSlot? {
        val slots = storeTimeSlotRepository
            .findAllByStore_IdAndStartTimeLessThanEqualAndEndTimeGreaterThan(storeId, now, now)

        return when (slots.size) {
            1 -> slots[0]
            0 -> null
            else -> throw BusinessException(ErrorCode.MULTIPLE_ACTIVE_TIME_SLOTS)
        }
    }

    fun getActiveSlot(storeId: UUID, now: LocalDateTime): StoreTimeSlot {
        val slots = storeTimeSlotRepository
            .findAllByStore_IdAndStartTimeLessThanEqualAndEndTimeGreaterThan(storeId, now, now)

        return when (slots.size) {
            1 -> slots[0]
            0 -> throw BusinessException(ErrorCode.ACTIVE_TIME_SLOT_NOT_FOUND)
            else -> throw BusinessException(ErrorCode.MULTIPLE_ACTIVE_TIME_SLOTS)
        }
    }

    fun getSlot(storeId: UUID, slotId: UUID): StoreTimeSlot {
        return storeTimeSlotRepository.findByIdAndStore_Id(slotId, storeId)
            ?: throw BusinessException(ErrorCode.TIMESLOT_INVALID, "timeSlot not found for store")
    }

    fun getDoorStatus(user: User, storeId: UUID, slotId: UUID): DoorStatusView {
        val slot = validateDoorSlotAccess(user, storeId, slotId)

        return buildDoorStatus(storeId, slot)
    }

    fun getAllSlots(storeId: UUID): StoreTimeSlotsResponse {
        val store = storeRepository.findByIdOrNull(storeId)
            ?: throw BusinessException(ErrorCode.STORE_NOT_FOUND)

        val slots = storeTimeSlotRepository.findAllByStoreId(store.id!!)

        return StoreTimeSlotsResponse(
            slots = slots.map { slot ->
                StoreTimeSlotItemResponse(
                    timeSlotId = slot.id!!,
                    startTime = slot.startTime,
                    endTime = slot.endTime,
                    startDate = slot.startTime.toLocalDate()
                )
            }
        )
    }

    // slot이 시작한 calendar date
    fun getSlotStartedOn(storeId: UUID, startDate: LocalDate): StoreTimeSlot {
        val startOfDay = startDate.atStartOfDay()
        val endOfDay = startDate.plusDays(1).atStartOfDay()

        val slots = storeTimeSlotRepository
            .findAllByStore_IdAndStartTimeGreaterThanEqualAndStartTimeLessThan(
                storeId,
                startOfDay,
                endOfDay
            )

        return when (slots.size) {
            1 -> slots[0]
            0 -> throw BusinessException(ErrorCode.TIMESLOT_INVALID, "timeSlot not found for date=$startDate")
            else -> throw BusinessException(ErrorCode.MULTIPLE_ACTIVE_TIME_SLOTS, "multiple slots found for date=$startDate")
        }
    }

    @Transactional
    fun updateWaitingStatus(user: User, storeId: UUID, slotId: UUID, waitingEnabled: Boolean): DoorStatusView {

        val slot = validateDoorSlotAccess(user, storeId, slotId)

        slot.hasWaiting = waitingEnabled

        return buildDoorStatus(storeId, slot)
    }

    fun validateDoorSlotAccess(user: User, storeId: UUID, slotId: UUID): StoreTimeSlot {
        val slot = storeTimeSlotRepository.findByIdAndStore_Id(slotId, storeId)
            ?: throw BusinessException(ErrorCode.ACTIVE_TIME_SLOT_NOT_FOUND)

        if (slot.store.host.id != user.id) {
            throw BusinessException(ErrorCode.INCORRECT_HOST_FORBIDDEN)
        }

        return slot
    }

    private fun buildDoorStatus(storeId: UUID, slot: StoreTimeSlot): DoorStatusView {
        val totalTableCount = storeTableRepository.countByStoreId(storeId).toInt()

        if (totalTableCount == 0) {
            return DoorStatusView(
                congestion = StoreCongestionLevel.UNCONFIGURED,
                remainingTableCount = 0,
                waitingEnabled = slot.hasWaiting
            )
        }

        val remainingTableCount = storeTableRepository
            .countByStoreIdAndStatus(storeId, StoreTableStatus.VACANT)
            .toInt()

        val congestion = calculateCongestion(
            totalTableCount = totalTableCount,
            remainingTableCount = remainingTableCount
        )

        return DoorStatusView(
            congestion = congestion,
            remainingTableCount = remainingTableCount,
            waitingEnabled = slot.hasWaiting
        )
    }

    private fun calculateCongestion(
        totalTableCount: Int,
        remainingTableCount: Int
    ): StoreCongestionLevel {
        val vacantRatio = remainingTableCount.toDouble() / totalTableCount.toDouble()

        return when {
            vacantRatio >= 0.5 -> StoreCongestionLevel.RELAXED
            vacantRatio >= 0.2 -> StoreCongestionLevel.NORMAL
            else -> StoreCongestionLevel.CROWDED
        }
    }
}
