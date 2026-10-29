package com.ournight.api_server.domain.pass.service

import com.ournight.api_server.domain.pass.model.Pass
import com.ournight.api_server.domain.pass.model.PassStatus
import com.ournight.api_server.domain.pass.repository.PassKeyRepository
import com.ournight.api_server.domain.pass.repository.PassRepository
import com.ournight.api_server.domain.pass.view.AdminPassView
import com.ournight.api_server.domain.pass.view.MyPassView
import com.ournight.api_server.domain.pass.view.PassStatusView
import com.ournight.api_server.domain.pass.view.PassView
import com.ournight.api_server.domain.store.repository.StoreTimeSlotRepository
import com.ournight.api_server.domain.user.repository.UserRepository
import com.ournight.api_server.global.error.ErrorCode
import com.ournight.api_server.global.error.exception.BusinessException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

@Service
@Transactional
class PassService(
    private val slotRepository: StoreTimeSlotRepository,
    private val passRepository: PassRepository,
    private val passKeyRepository: PassKeyRepository,
    private val userRepository: UserRepository,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun getAdminPasses(): List<AdminPassView> {
        val now = LocalDateTime.now(clock)
        return passRepository.findAllWithUserTimeSlotAndStoreForAdmin().map { it.toAdminPassView(now) }
    }

    @Transactional
    fun updateAdminPassStatus(passId: UUID, status: PassStatus): AdminPassView {
        val pass = passRepository.findById(passId)
            .orElseThrow { BusinessException(ErrorCode.PASS_NOT_FOUND) }
        val now = LocalDateTime.now(clock)

        when (status) {
            PassStatus.INACTIVE -> {
                pass.status = PassStatus.INACTIVE
                pass.activatedAt = null
                pass.expiresAt = null
            }
            PassStatus.ACTIVATED -> {
                if (pass.activatedAt == null || pass.expiresAt == null || pass.isExpired(now)) {
                    pass.activatedAt = null
                    pass.expiresAt = null
                    pass.status = PassStatus.INACTIVE
                    pass.activate(now = now, timeLimitMinutes = pass.store.timeLimit.toLong())
                } else {
                    pass.status = PassStatus.ACTIVATED
                }
            }
            PassStatus.EXPIRED -> {
                pass.expire()
            }
        }

        return passRepository.save(pass).toAdminPassView(now)
    }

    @Transactional(readOnly = true)
    fun getMyPasses(userId: UUID): List<MyPassView> {
        val now = LocalDateTime.now(clock)
        return passRepository.findAllByUserIdWithTimeSlotAndStore(userId).map { pass ->
            val expired = pass.isExpired(now)
            val orderable = pass.isOrderable(now)
            val effectiveStatus = if (expired && pass.status == PassStatus.ACTIVATED) {
                PassStatus.EXPIRED.name
            } else {
                pass.status.name
            }

            MyPassView(
                passId = pass.id!!,
                storeId = pass.store.id!!,
                storeName = pass.store.name,
                slotId = pass.timeSlot.id!!,
                slotStartTime = pass.timeSlot.startTime,
                slotEndTime = pass.timeSlot.endTime,
                status = pass.status.name,
                effectiveStatus = effectiveStatus,
                orderable = orderable,
                expired = expired,
                activatedAt = pass.activatedAt,
                expiredAt = pass.expiresAt,
            )
        }
    }

    @Transactional(readOnly = true)
    fun ensureOrderablePassForStore(userId: UUID, storeId: UUID) {
        val now = LocalDateTime.now(clock)
        val hasOrderablePass = passRepository
            .findAllByUserIdAndStoreIdWithTimeSlotAndStore(userId, storeId)
            .any { it.isOrderable(now) }

        if (!hasOrderablePass) {
            throw BusinessException(ErrorCode.PASS_NOT_ORDERABLE)
        }
    }

    @Transactional(readOnly = true)
    fun getPassStatus(userId: UUID, storeId: UUID, slotId: UUID): PassStatusView {
        slotRepository.findByIdAndStore_Id(slotId, storeId)
            ?: throw BusinessException(ErrorCode.TIMESLOT_INVALID, "해당 스토어의 타임슬롯이 아닙니다.")

        val pass = passRepository.findByUserIdAndTimeSlotId(userId, slotId)
            ?: return PassStatusView(
                slotId = slotId,
                hasPass = false,
                status = null,
                orderable = false,
                passId = null,
                activatedAt = null,
                expiresAt = null,
            )
        val now = LocalDateTime.now(clock)

        return PassStatusView(
            slotId = slotId,
            hasPass = true,
            status = pass.status.name,
            orderable = pass.isOrderable(now),
            passId = pass.id,
            activatedAt = pass.activatedAt,
            expiresAt = pass.expiresAt,
        )
    }

    fun activatePass(userId: UUID, storeId: UUID, slotId: UUID, inputCode: String): PassView {
        val slot = slotRepository.findByIdAndStore_Id(slotId, storeId)
            ?: throw BusinessException(ErrorCode.TIMESLOT_INVALID, "해당 스토어의 타임슬롯이 아닙니다.")

        val passKey = passKeyRepository.findBySlotId(slotId)
            ?: throw BusinessException(ErrorCode.PASS_KEY_NOT_FOUND)

        // 2. PIN 번호 일치 여부 확인 (QR 확장성 부분)
        if (passKey.value != inputCode) {
            throw BusinessException(ErrorCode.PASS_CODE_MISMATCH)
        }

        val user = userRepository.findById(userId)
            .orElseThrow { BusinessException(ErrorCode.BAD_REQUEST, message = "유저를 찾을 수 없음") }

        val pass: Pass = passRepository.findByUserIdAndTimeSlotId(userId, slotId)
            ?: passRepository.save(Pass(user = user, timeSlot = slot))

        val now = LocalDateTime.now(clock)

        if (pass.status == PassStatus.ACTIVATED && pass.isOrderable(now)) {
            return toPassView(pass)
        }

        if (pass.status == PassStatus.EXPIRED || (pass.status == PassStatus.ACTIVATED && !pass.isOrderable(now))) {
            pass.status = PassStatus.INACTIVE
            pass.activatedAt = null
            pass.expiresAt = null
        }

        if (pass.status != PassStatus.INACTIVE) {
            throw BusinessException(
                ErrorCode.PASS_INVALID_STATE,
                details = mapOf("status" to pass.status.name),
            )
        }

        pass.activate(now = now, timeLimitMinutes = slot.store.timeLimit.toLong())

        return toPassView(pass)
    }

    fun issuePass(userId: UUID, storeId: UUID, slotId: UUID): PassView {
        val slot = slotRepository.findByIdAndStore_Id(slotId, storeId)
            ?: throw BusinessException(ErrorCode.TIMESLOT_INVALID, "해당 스토어의 타임슬롯이 아닙니다.")

        val user = userRepository.findById(userId)
            .orElseThrow { BusinessException(ErrorCode.BAD_REQUEST, message = "유저를 찾을 수 없음") }

        val pass = passRepository.findByUserIdAndTimeSlotId(userId, slotId)
            ?: passRepository.save(Pass(user = user, timeSlot = slot))

        val now = LocalDateTime.now(clock)

        if (pass.status == PassStatus.EXPIRED || (pass.status == PassStatus.ACTIVATED && !pass.isOrderable(now))) {
            pass.status = PassStatus.INACTIVE
            pass.activatedAt = null
            pass.expiresAt = null
        }

        return toPassView(pass)
    }

    private fun toPassView(pass: Pass) = PassView(
        id = pass.id!!,
        slotId = pass.timeSlot.id!!,
        status = pass.status.name,
        activatedAt = pass.activatedAt,
        expiredAt = pass.expiresAt
    )

    private fun Pass.toAdminPassView(now: LocalDateTime): AdminPassView {
        val expired = this.isExpired(now)
        val orderable = this.isOrderable(now)
        val effectiveStatus = if (expired && this.status == PassStatus.ACTIVATED) {
            PassStatus.EXPIRED.name
        } else {
            this.status.name
        }

        return AdminPassView(
            passId = this.id!!,
            userId = this.user.id!!,
            kakaoId = this.user.kakaoId,
            nickname = this.user.nickname,
            storeId = this.store.id!!,
            storeName = this.store.name,
            slotId = this.timeSlot.id!!,
            slotStartTime = this.timeSlot.startTime,
            slotEndTime = this.timeSlot.endTime,
            status = this.status.name,
            effectiveStatus = effectiveStatus,
            orderable = orderable,
            expired = expired,
            activatedAt = this.activatedAt,
            expiredAt = this.expiresAt,
        )
    }
}
