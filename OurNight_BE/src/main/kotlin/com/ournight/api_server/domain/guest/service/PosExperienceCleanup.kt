package com.ournight.api_server.domain.guest.service

import com.ournight.api_server.domain.store.application.StoreService
import com.ournight.api_server.domain.store.dao.StoreRepository
import com.ournight.api_server.domain.pass.repository.PassRepository
import com.ournight.api_server.domain.user.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Component
class PosExperienceCleanup(
    private val storeRepository: StoreRepository,
    private val storeService: StoreService,
    private val passRepository: PassRepository,
    private val userRepository: UserRepository,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 15 * 60 * 1000L, initialDelay = 15 * 60 * 1000L)
    @Transactional
    fun removeExpiredExperiences() {
        val cutoff = LocalDateTime.now(clock).minusMinutes(POS_EXPERIENCE_TTL_MINUTES)
        storeRepository.findAllByDescriptionStartingWithAndCreatedAtBefore(
            POS_EXPERIENCE_MARKER,
            cutoff,
        ).forEach { store ->
            val storeId = requireNotNull(store.id)
            val hostId = requireNotNull(store.host.id)
            val customerUserIds = passRepository
                .findAllByStoreIdWithUser(storeId)
                .mapNotNull { it.user.id }
            runCatching {
                storeService.hardDeleteStore(storeId)
                customerUserIds.forEach(userRepository::deleteById)
                if (storeRepository.countByHostId(hostId) == 0L) userRepository.deleteById(hostId)
            }.onFailure { error ->
                log.warn("POS experience cleanup failed. storeId={}", storeId, error)
            }
        }
    }
}
