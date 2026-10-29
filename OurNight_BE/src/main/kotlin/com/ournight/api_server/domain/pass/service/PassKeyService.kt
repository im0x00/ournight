package com.ournight.api_server.domain.pass.service

import com.ournight.api_server.domain.pass.model.PassKey
import com.ournight.api_server.domain.pass.model.PassKeyType
import com.ournight.api_server.domain.pass.repository.PassKeyRepository
import com.ournight.api_server.domain.pass.view.PassKeyView
import com.ournight.api_server.global.error.ErrorCode
import com.ournight.api_server.global.error.exception.BusinessException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class PassKeyService(
    private val passKeyRepository: PassKeyRepository
) {
    fun getActivationCode(slotId: UUID): PassKeyView {
        val passKey = passKeyRepository.findBySlotId(slotId)

        if (passKey != null) {
            return PassKeyView(
                code = passKey.value,
                type = passKey.type.name,
                configured = true
            )
        }

        return PassKeyView(
            code = null,
            type = null,
            configured = false
        )
    }

    @Transactional
    fun createActivationCode(slotId: UUID): PassKeyView {
        val passKey = passKeyRepository.findBySlotId(slotId)

        if (passKey != null) {
            return PassKeyView(
                code = passKey.value,
                type = passKey.type.name,
                configured = true
            )
        }

        val newKey = PassKey(
            slotId = slotId,
            type = PassKeyType.PIN,
            value = generateRandomPin()
        )
        passKeyRepository.save(newKey)

        return PassKeyView(
            code = newKey.value,
            type = newKey.type.name,
            configured = true
        )
    }

    @Transactional
    fun resetActivationCode(slotId: UUID): PassKeyView {
        val passKey = passKeyRepository.findBySlotId(slotId)
            ?: throw BusinessException(ErrorCode.PASS_KEY_NOT_FOUND)

        val newPin = generateRandomPin()
        passKey.refresh(newPin, PassKeyType.PIN)

        return PassKeyView(
            code = passKey.value,
            type = passKey.type.name,
            configured = true
        )
    }

    private fun generateRandomPin(): String = (1000..9999).random().toString()

}
