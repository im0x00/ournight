package com.ournight.api_server.domain.waiting.service

import com.ournight.api_server.domain.waiting.model.WaitingStatus
import com.ournight.api_server.domain.waiting.repository.WaitingStatusRepository
import com.ournight.api_server.global.error.ErrorCode
import com.ournight.api_server.global.error.exception.BusinessException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class WaitingStatusService(
    private val waitingStatusRepository: WaitingStatusRepository,
) {
    @Transactional(readOnly = true)
    fun getAll(): List<WaitingStatus> =
        waitingStatusRepository.findAll().sortedBy { DISPLAY_ORDER[it.name] ?: Int.MAX_VALUE }

    @Transactional
    fun updateCount(id: UUID, count: Int): WaitingStatus {
        if (count < 0) {
            throw BusinessException(ErrorCode.BAD_REQUEST, message = "대기 인원은 0명 이상이어야 합니다.")
        }

        val status = waitingStatusRepository.findById(id)
            .orElseThrow { BusinessException(ErrorCode.WAITING_STATUS_NOT_FOUND) }
        status.count = count
        return status
    }

    companion object {
        private val DISPLAY_ORDER = listOf(
            "신소재공학관 앞",
            "사회과학관 앞",
            "국제관 옆",
            "GATE A",
            "GATE B",
            "GATE C",
        ).withIndex().associate { it.value to it.index }
    }
}
