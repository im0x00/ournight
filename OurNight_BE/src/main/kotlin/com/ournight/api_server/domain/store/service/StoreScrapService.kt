package com.ournight.api_server.domain.store.application

import com.ournight.api_server.api.customer.dto.CustomerStoreByDateResponse
import com.ournight.api_server.api.customer.dto.CustomerStoreTimeSlotResponse
import com.ournight.api_server.domain.store.dao.StoreRepository
import com.ournight.api_server.domain.store.model.StoreScrap
import com.ournight.api_server.domain.store.repository.StoreScrapRepository
import com.ournight.api_server.domain.user.repository.UserRepository
import com.ournight.api_server.global.error.ErrorCode
import com.ournight.api_server.global.error.exception.BusinessException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class StoreScrapService(
    private val storeScrapRepository: StoreScrapRepository,
    private val storeRepository: StoreRepository,
    private val userRepository: UserRepository,
) {
    fun findScrappedStoreIds(userId: UUID?, storeIds: Collection<UUID>): Set<UUID> {
        if (userId == null || storeIds.isEmpty()) {
            return emptySet()
        }
        return storeScrapRepository
            .findScrappedStoreIdsByUserIdAndStoreIds(userId, storeIds)
            .toSet()
    }

    @Transactional
    fun scrapStore(userId: UUID, storeId: UUID) {
        if (storeScrapRepository.findByUserIdAndStoreId(userId, storeId) != null) {
            return
        }

        val store = storeRepository.findById(storeId).orElseThrow {
            BusinessException(ErrorCode.STORE_NOT_FOUND)
        }
        if (!store.isActive) {
            throw BusinessException(ErrorCode.STORE_NOT_FOUND)
        }

        val user = userRepository.findById(userId).orElseThrow {
            BusinessException(ErrorCode.USER_NOT_FOUND)
        }

        try {
            storeScrapRepository.save(
                StoreScrap(
                    user = user,
                    store = store,
                )
            )
        } catch (_: DataIntegrityViolationException) {
            // unique(user_id, store_id) 충돌은 중복 스크랩으로 보고 멱등 성공 처리
        }
    }

    @Transactional
    fun unscrapStore(userId: UUID, storeId: UUID) {
        storeScrapRepository.deleteByUserIdAndStoreId(userId, storeId)
    }

    fun getMyScrappedStores(userId: UUID): List<CustomerStoreByDateResponse> {
        return storeScrapRepository.findActiveScrapsByUserIdWithStore(userId)
            .map { it.store }
            .map { store ->
                val venue = store.venue ?: error("venue must not be null")
                val university = venue.university
                val slots = store.timeSlots
                    .sortedBy { slot -> slot.startTime }
                    .map { slot ->
                        CustomerStoreTimeSlotResponse(
                            slotId = slot.id ?: error("slot id is null"),
                            startTime = slot.startTime,
                            endTime = slot.endTime,
                            hasWaiting = slot.hasWaiting,
                        )
                    }

                CustomerStoreByDateResponse(
                    id = store.id ?: error("store id is null"),
                    name = store.name,
                    imageUrl = store.mainImageUrls.firstOrNull(),
                    type = store.type.name,
                    address = venue.address,
                    universityId = university?.id,
                    universityName = university?.name,
                    isScrapped = true,
                    timeSlots = slots,
                )
            }
    }
}
