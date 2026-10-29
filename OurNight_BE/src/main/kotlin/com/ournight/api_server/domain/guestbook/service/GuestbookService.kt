package com.ournight.api_server.domain.guestbook.service

import com.ournight.api_server.domain.guestbook.model.GuestbookEntry
import com.ournight.api_server.domain.guestbook.repository.GuestbookEntryRepository
import com.ournight.api_server.domain.store.dao.StoreRepository
import com.ournight.api_server.domain.user.model.User
import com.ournight.api_server.global.error.ErrorCode
import com.ournight.api_server.global.error.exception.BusinessException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class GuestbookService(
    private val guestbookEntryRepository: GuestbookEntryRepository,
    private val storeRepository: StoreRepository,
) {
    fun getEntries(storeId: UUID): List<GuestbookEntry> {
        validateStoreExists(storeId)
        return guestbookEntryRepository.findByStoreIdOrderByCreatedAtDesc(storeId)
    }

    @Transactional
    fun createEntry(storeId: UUID, user: User, content: String, tags: List<String>): GuestbookEntry {
        val store = storeRepository.findById(storeId).orElseThrow {
            BusinessException(ErrorCode.STORE_NOT_FOUND)
        }

        val normalizedContent = content.trim()
        if (normalizedContent.isBlank() || normalizedContent.length > MAX_CONTENT_LENGTH) {
            throw BusinessException(ErrorCode.BAD_REQUEST, message = "방명록 내용은 1자 이상 ${MAX_CONTENT_LENGTH}자 이하로 입력해주세요.")
        }

        val normalizedTags = tags
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        if (normalizedTags.size > MAX_TAG_COUNT || normalizedTags.any { it.length > MAX_TAG_LENGTH }) {
            throw BusinessException(ErrorCode.BAD_REQUEST, message = "방명록 태그가 올바르지 않습니다.")
        }

        return guestbookEntryRepository.save(
            GuestbookEntry(
                store = store,
                user = user,
                authorSnapshot = user.nickname,
                content = normalizedContent,
                tags = normalizedTags.toMutableList(),
            )
        )
    }

    private fun validateStoreExists(storeId: UUID) {
        if (!storeRepository.existsById(storeId)) {
            throw BusinessException(ErrorCode.STORE_NOT_FOUND)
        }
    }

    companion object {
        private const val MAX_CONTENT_LENGTH = 120
        private const val MAX_TAG_COUNT = 3
        private const val MAX_TAG_LENGTH = 30
    }
}
