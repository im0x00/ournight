package com.ournight.api_server.domain.store.application

import com.ournight.api_server.domain.store.model.StoreTimeSlot
import com.ournight.api_server.domain.store.repository.StoreTimeSlotRepository
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

@Component
class TimeSlotResolver(
    private val storeTimeSlotRepository: StoreTimeSlotRepository,
    private val clock: Clock,
) {
    fun resolveCurrent(storeId: UUID): StoreTimeSlot? {
        val now = LocalDateTime.now(clock)
        return storeTimeSlotRepository.findCurrentSlot(storeId, now)
    }
}