package com.ournight.api_server.api.customer.controller

import com.ournight.api_server.api.customer.dto.CustomerStoreDetailResponse
import com.ournight.api_server.api.customer.dto.CustomerStoreByDateResponse
import com.ournight.api_server.api.customer.dto.CustomerStoreByBoundsResponse
import com.ournight.api_server.api.customer.dto.CustomerStoreDateRangeCountResponse
import com.ournight.api_server.api.customer.dto.CustomerStoreTimeSlotResponse
import com.ournight.api_server.api.customer.dto.CustomerStoreMenuCategoryResponse
import com.ournight.api_server.api.customer.dto.CustomerStoreSummaryResponse
import com.ournight.api_server.domain.store.application.StoreMenuService
import com.ournight.api_server.domain.store.application.StoreScrapService
import com.ournight.api_server.domain.store.application.StoreService
import com.ournight.api_server.domain.user.model.CustomUserDetails
import com.ournight.api_server.domain.user.model.User
import com.ournight.api_server.global.error.ErrorCode
import com.ournight.api_server.global.error.exception.BusinessException
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.CacheControl
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@RestController
@RequestMapping("/api/customer/stores")
class CustomerStoreController(
    private val storeService: StoreService,
    private val storeMenuService: StoreMenuService,
    private val storeScrapService: StoreScrapService,
) {

    // 1. 주점 목록 조회 (지도나 리스트용 - 가벼운 정보)
    @GetMapping
    @Transactional(readOnly = true)
    fun getActiveStores(): ResponseEntity<List<CustomerStoreSummaryResponse>> {
        val stores = storeService.getActiveStores() // 모든 스토어

        val response = stores.map { store ->
            val venue = store.venue
            CustomerStoreSummaryResponse(
                id = store.id!!,
                name = store.name,
                description = store.description,
                imageUrl = store.mainImageUrls.firstOrNull(),
                type = store.type.name, // enum 이름 사용 (FESTIVAL, RENTAL)
                latitude = venue?.latitude, // venue가 null이면 null 반환
                longitude = venue?.longitude // venue가 null이면 null 반환
            )
        }
        return ResponseEntity.ok(response)
    }

    // 날짜 기준 주점 목록 조회 (해당 날짜와 겹치는 슬롯이 있는 활성 스토어)
    @GetMapping("/by-date")
    @Transactional(readOnly = true)
    fun getStoresByDate(
        @RequestParam
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        date: LocalDate,
    ): ResponseEntity<List<CustomerStoreByDateResponse>> {
        val stores = storeService.getActiveStoresByDate(date)
        val storeIds = stores.mapNotNull { it.id }
        val scrappedStoreIds = storeScrapService.findScrappedStoreIds(currentUserIdOrNull(), storeIds)

        val response = stores.map { store ->
            val venue = store.venue ?: error("venue must not be null")
            val university = venue.university
            val slots = store.timeSlots
                .sortedBy { it.startTime }
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
                isScrapped = store.id?.let { scrappedStoreIds.contains(it) } ?: false,
                timeSlots = slots,
            )
        }

        return ResponseEntity.ok(response)
    }

    // 위경도 박스 기준 주점 목록 조회
    @GetMapping("/by-bounds")
    @Transactional(readOnly = true)
    fun getStoresByBounds(
        @RequestParam minLat: Double,
        @RequestParam maxLat: Double,
        @RequestParam minLng: Double,
        @RequestParam maxLng: Double,
    ): ResponseEntity<List<CustomerStoreByBoundsResponse>> {
        val stores = storeService.getActiveStoresByBounds(minLat, maxLat, minLng, maxLng)
        val storeIds = stores.mapNotNull { it.id }
        val scrappedStoreIds = storeScrapService.findScrappedStoreIds(currentUserIdOrNull(), storeIds)

        val response = stores.map { store ->
            val venue = store.venue ?: error("venue must not be null")
            val slots = store.timeSlots
                .sortedBy { it.startTime }
                .map { slot ->
                    CustomerStoreTimeSlotResponse(
                        slotId = slot.id ?: error("slot id is null"),
                        startTime = slot.startTime,
                        endTime = slot.endTime,
                        hasWaiting = slot.hasWaiting,
                    )
                }

            CustomerStoreByBoundsResponse(
                id = store.id ?: error("store id is null"),
                name = store.name,
                imageUrl = store.mainImageUrls.firstOrNull(),
                type = store.type.name,
                address = venue.address,
                latitude = venue.latitude,
                longitude = venue.longitude,
                isScrapped = store.id?.let { scrappedStoreIds.contains(it) } ?: false,
                timeSlots = slots,
            )
        }
        return ResponseEntity.ok(response)
    }

    @GetMapping("/by-search")
    @Transactional(readOnly = true)
    fun searchStores(
        @RequestParam query: String,
    ): ResponseEntity<List<CustomerStoreByBoundsResponse>> {
        if (query.isBlank()) {
            throw BusinessException(
                errorCode = ErrorCode.BAD_REQUEST,
                message = "query must not be blank",
            )
        }

        val stores = storeService.searchActiveStoresByNameOrUniversity(query)
        val storeIds = stores.mapNotNull { it.id }
        val scrappedStoreIds = storeScrapService.findScrappedStoreIds(currentUserIdOrNull(), storeIds)
        val response = stores.map { store ->
            val venue = store.venue ?: error("venue must not be null")
            val slots = store.timeSlots
                .sortedBy { it.startTime }
                .map { slot ->
                    CustomerStoreTimeSlotResponse(
                        slotId = slot.id ?: error("slot id is null"),
                        startTime = slot.startTime,
                        endTime = slot.endTime,
                        hasWaiting = slot.hasWaiting,
                    )
                }

            CustomerStoreByBoundsResponse(
                id = store.id ?: error("store id is null"),
                name = store.name,
                imageUrl = store.mainImageUrls.firstOrNull(),
                type = store.type.name,
                address = venue.address,
                latitude = venue.latitude,
                longitude = venue.longitude,
                isScrapped = store.id?.let { scrappedStoreIds.contains(it) } ?: false,
                timeSlots = slots,
            )
        }
        return ResponseEntity.ok(response)
    }

    @GetMapping("/count-by-date-range")
    @Transactional(readOnly = true)
    fun getStoreCountsByDateRange(
        @RequestParam
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        startDate: LocalDate,
        @RequestParam
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        endDate: LocalDate,
    ): ResponseEntity<CustomerStoreDateRangeCountResponse> {
        val counts = storeService.getStoreCountsByDateRange(startDate, endDate)

        return ResponseEntity.ok(
            CustomerStoreDateRangeCountResponse(
                startDate = startDate,
                endDate = endDate,
                counts = counts,
            )
        )
    }

    // 2. 주점 상세 조회 (입장 전 확인용 - 상세 정보)
    @GetMapping("/{storeId}")
    fun getStoreDetail(
        @PathVariable storeId: UUID
    ): ResponseEntity<CustomerStoreDetailResponse> {
        val store = storeService.getStoreById(storeId)
            ?: return ResponseEntity.notFound().build()

        // 만약 비활성화된 가게라면 404 처리할 수도 있음
        if (!store.isActive) return ResponseEntity.notFound().build()

        val timeSlots = store.timeSlots
            .sortedBy { it.startTime }
            .map {
                CustomerStoreTimeSlotResponse(
                    slotId = it.id!!,
                    startTime = it.startTime,
                    endTime = it.endTime,
                    hasWaiting = it.hasWaiting,
                )
            }

        val response = CustomerStoreDetailResponse(
            id = store.id!!,
            name = store.name,
            description = store.description,
            type = store.type.name,
            address = store.venue?.address, // venue가 null일 수 있음
            bankName = store.bankName,
            accountNumber = store.accountNumber,
            entranceFee = store.entranceFee,
            notices = store.notices.toList(),
            imageUrls = store.mainImageUrls.toList(),
            timeSlots = timeSlots,
        )
        return ResponseEntity.ok(response)
    }

    // 메뉴리스트 조회
    @GetMapping("/{storeId}/menus")
    fun getStoreMenus(
        @PathVariable storeId: UUID,
        @RequestHeader(HttpHeaders.IF_NONE_MATCH, required = false) ifNoneMatch: String?
    ): ResponseEntity<List<CustomerStoreMenuCategoryResponse>> {
        val latest = storeMenuService.getLastUpdatedAtForStoreMenus(storeId)
        val etag = buildEtag(storeId, latest)

        if (ifNoneMatch == etag) {
            return ResponseEntity.status(304)
                .cacheControl(CacheControl.noCache().mustRevalidate())
                .eTag(etag)
                .build()
        }

        val response = storeMenuService.getMenuAndCategoriesByStoreId(storeId)

        return ResponseEntity.ok()
            .cacheControl(CacheControl.noCache().mustRevalidate())
            .eTag(etag)
            .body(response)
    }

    private fun buildEtag(storeId: UUID, latest: Instant): String {
        val raw = "$storeId:${latest.toEpochMilli()}"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(StandardCharsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }
        return "\"$hex\""
    }

    private fun currentUserIdOrNull(): UUID? {
        val principal = SecurityContextHolder.getContext().authentication?.principal
        return when (principal) {
            is CustomUserDetails -> principal.getUser().id
            is User -> principal.id
            else -> null
        }
    }
}
