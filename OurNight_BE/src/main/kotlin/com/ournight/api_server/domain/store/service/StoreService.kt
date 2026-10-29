package com.ournight.api_server.domain.store.application

import com.ournight.api_server.domain.call.model.CallOption
import com.ournight.api_server.domain.call.repository.CallOptionRepository
import com.ournight.api_server.domain.call.repository.CallRequestRepository
import com.ournight.api_server.domain.guestbook.repository.GuestbookEntryRepository
import com.ournight.api_server.domain.order.repository.IssueRepository
import com.ournight.api_server.domain.order.repository.MenuSalesSummaryRepository
import com.ournight.api_server.domain.order.repository.OrderItemChangeRepository
import com.ournight.api_server.domain.order.repository.OrderItemRepository
import com.ournight.api_server.domain.order.repository.OrderRepository
import com.ournight.api_server.domain.order.repository.StoreSalesSummaryRepository
import com.ournight.api_server.domain.store.dao.StoreRepository
import com.ournight.api_server.domain.store.dao.StoreMenuRepository
import com.ournight.api_server.domain.store.dao.StoreTableRepository
import com.ournight.api_server.domain.store.domain.Store
import com.ournight.api_server.domain.store.domain.StoreType
import com.ournight.api_server.domain.store.model.StoreTimeSlot
import com.ournight.api_server.domain.store.model.StoreMenuCategory
import com.ournight.api_server.domain.store.repository.StoreMenuCategoryRepository
import com.ournight.api_server.domain.store.repository.StoreScrapRepository
import com.ournight.api_server.domain.store.repository.StoreTableZoneRepository
import com.ournight.api_server.domain.store.view.StoreBasicUpdateCommand
import com.ournight.api_server.domain.store.view.StoreDetailResult
import com.ournight.api_server.domain.store.view.VenueInfoResult
import com.ournight.api_server.domain.store.view.StoreOnboardingCreateCommand
import com.ournight.api_server.domain.store.view.StoreOnboardingCreateResult
import com.ournight.api_server.domain.store.view.StoreOperatingUpdateCommand
import com.ournight.api_server.domain.store.view.StoreOpsUpdateCommand
import com.ournight.api_server.domain.store.view.StoreTimeSlotCommand
import com.ournight.api_server.domain.store.view.StoreTimeSlotResult
import com.ournight.api_server.domain.pass.repository.PassKeyRepository
import com.ournight.api_server.domain.pass.repository.PassRepository
import com.ournight.api_server.domain.session.repository.SessionParticipantRepository
import com.ournight.api_server.domain.session.repository.TableSessionRepository
import com.ournight.api_server.domain.user.model.User
import com.ournight.api_server.domain.user.repository.UserRepository
import com.ournight.api_server.domain.venue.service.VenueService
import com.ournight.api_server.api.host.dto.VenueCreateRequest
import com.ournight.api_server.api.host.dto.VenueUniversityInfo
import com.ournight.api_server.global.error.ErrorCode
import com.ournight.api_server.global.error.exception.BusinessException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID
import kotlin.collections.isNotEmpty

@Service
@Transactional(readOnly = true)
class StoreService(
	private val storeRepository: StoreRepository,
	private val storeMenuCategoryRepository: StoreMenuCategoryRepository,
	private val venueService: VenueService,
	private val passRepository: PassRepository,
	private val passKeyRepository: PassKeyRepository,
	private val callOptionRepository: CallOptionRepository,
	private val callRequestRepository: CallRequestRepository,
	private val sessionParticipantRepository: SessionParticipantRepository,
	private val tableSessionRepository: TableSessionRepository,
	private val orderRepository: OrderRepository,
	private val orderItemRepository: OrderItemRepository,
	private val orderItemChangeRepository: OrderItemChangeRepository,
	private val issueRepository: IssueRepository,
	private val storeSalesSummaryRepository: StoreSalesSummaryRepository,
	private val menuSalesSummaryRepository: MenuSalesSummaryRepository,
	private val storeMenuRepository: StoreMenuRepository,
	private val storeTableRepository: StoreTableRepository,
	private val storeTableZoneRepository: StoreTableZoneRepository,
	private val storeScrapRepository: StoreScrapRepository,
	private val guestbookEntryRepository: GuestbookEntryRepository,
	private val userRepository: UserRepository,
) {
	private fun appendLatestNotice(store: Store, noticePayload: List<String>) {
		val nextNotice = noticePayload.joinToString("\n")
		if (store.notices.lastOrNull() == nextNotice) return
		store.notices.add(nextNotice)
	}

	@Transactional
	fun createOnboarding(user: User, command: StoreOnboardingCreateCommand): StoreOnboardingCreateResult {
		// 같은 유저가 같은 이름의 스토어를 이미 가지고 있는지 확인
		val existingStores = storeRepository.findByHost(user)
		val duplicateNameStore = existingStores.find { it.name == command.name }
		if (duplicateNameStore != null) {
			throw BusinessException(ErrorCode.DUPLICATE_STORE_NAME)
		}

		require(command.timeSlots.isNotEmpty()) { "timeSlots must not be empty" }
		command.timeSlots.forEach {
			require(!it.endTime.isBefore(it.startTime)) { "endTime must be after startTime" }
			}

		val store = Store(
					name = command.name,
					description = command.description,
					hostContact = command.hostContact,
					type = command.type,
					host = user,
			mainImageUrls = (command.mainImageUrls ?: emptyList()).toMutableList(),
				)

		command.timeSlots.forEach { slot ->
			store.timeSlots += StoreTimeSlot(
				store = store,
				startTime = slot.startTime,
				endTime = slot.endTime,
			)
		}

		val saved = storeRepository.save(store)
		storeMenuCategoryRepository.saveAll(
			listOf(
				StoreMenuCategory(name = "메인메뉴", sequence = 1, store = saved),
				StoreMenuCategory(name = "사이드메뉴", sequence = 2, store = saved),
				StoreMenuCategory(name = "음료/주류", sequence = 3, store = saved),
			)
		)
		callOptionRepository.saveAll(CallOption.onboardingDefaultsFor(saved))
		return StoreOnboardingCreateResult(storeId = saved.id ?: error("saved store id is null"))
	}

	fun getStoreDetail(user: User, storeId: UUID): StoreDetailResult {
		val store = storeRepository.findDetailById(storeId).orElseThrow {
			BusinessException(ErrorCode.STORE_NOT_FOUND)
		}

		return StoreDetailResult(
			storeId = store.id ?: throw BusinessException(ErrorCode.STORE_NOT_FOUND),
			type = store.type,
			name = store.name,
			description = store.description,
			hostContact = store.hostContact,
			mainImageUrls = store.mainImageUrls.toList(),
			venueId = store.venue?.id,
			venue = store.venue?.let { venue ->
				VenueInfoResult(
					venueId = venue.id!!,
					name = venue.name,
					address = venue.address,
					detailLocation = venue.detailLocation,
					latitude = venue.latitude,
					longitude = venue.longitude,
					university = venue.university?.let { university ->
						VenueUniversityInfo(
							id = university.id!!,
							name = university.name,
							latitude = university.latitude,
							longitude = university.longitude,
						)
					},
				)
			},
			timeSlots = store.timeSlots
				.sortedBy { it.startTime }
				.map {
					StoreTimeSlotResult(
						slotId = it.id ?: error("timeSlot id is null"),
						startTime = it.startTime,
						endTime = it.endTime,
					)
				},
			bankName = store.bankName,
			accountNumber = store.accountNumber,
			entranceFee = store.entranceFee,
			timeLimit = store.timeLimit,
			notices = store.notices.toList(),
		)
	}

	@Transactional
	fun updateBasic(user: User, storeId: UUID, command: StoreBasicUpdateCommand): UUID {
		val store = storeRepository.findById(storeId).orElseThrow {
			BusinessException(ErrorCode.STORE_NOT_FOUND)
		}

		command.name?.let { store.name = it }
		command.description?.let { store.description = it }
		
		// 이미지 URL 리스트 업데이트: 기존과 비교하여 제거된 것은 DB 링크만 제거 (S3 파일은 유지)
		command.mainImageUrls?.let { newImageUrls ->
			// 기존 이미지 URL과 새 이미지 URL 비교
			val existingUrls = store.mainImageUrls.toSet()
			val newUrls = newImageUrls.toSet()
			
			// 제거된 이미지는 DB 링크만 제거 (S3 파일은 유지)
			// 추가된 이미지는 새로 추가 (이미 S3에 업로드된 URL을 받는 것으로 가정)
			store.mainImageUrls = newImageUrls.toMutableList()
		}
		
		command.venueId?.let { store.venue = venueService.getVenue(it) }

		// 운영일시: slotId 있으면 UPDATE, 없으면 INSERT, 요청에 없는 기존 슬롯은 DELETE(참조 시 거절)
		command.timeSlots?.let { timeSlots ->
			require(timeSlots.isNotEmpty()) { "timeSlots must not be empty" }
			timeSlots.forEach { slot ->
				require(slot.endTime.isAfter(slot.startTime)) {
					"endTime (${slot.endTime}) must be after startTime (${slot.startTime})"
				}
			}
			syncTimeSlots(store, timeSlots)
		}

		return store.id ?: error("store id is null")
	}

	@Transactional
	fun updateOps(user: User, storeId: UUID, command: StoreOpsUpdateCommand): UUID {
		val store = storeRepository.findById(storeId).orElseThrow {
			BusinessException(ErrorCode.STORE_NOT_FOUND)
		}

		command.bankName?.let { store.bankName = it }
		command.accountNumber?.let { store.accountNumber = it }
		command.entranceFee?.let { store.entranceFee = it }
		command.timeLimit?.let { store.timeLimit = it }
		command.notice?.let { appendLatestNotice(store, it) }

		return store.id ?: error("store id is null")
	}

	@Transactional
	fun updateOperating(user: User, storeId: UUID, command: StoreOperatingUpdateCommand): UUID {
		val store = storeRepository.findById(storeId).orElseThrow {
			BusinessException(ErrorCode.STORE_NOT_FOUND)
		}

		command.temporaryVenue?.let { temporaryVenue ->
			val venueRequest = VenueCreateRequest(
				name = store.name,
				address = temporaryVenue.detailLocation ?: "",
				latitude = temporaryVenue.latitude,
				longitude = temporaryVenue.longitude,
				detailLocation = temporaryVenue.detailLocation,
				isPartner = false,
				universityId = temporaryVenue.universityId,
			)
			store.venue = if (store.type == StoreType.FESTIVAL) {
				store.venue
					?.let { venueService.updateTemporaryVenue(it, venueRequest) }
					?: venueService.createTemporaryVenue(venueRequest)
			} else {
				venueService.createTemporaryVenue(venueRequest)
			}
		}
		command.venueId?.let { store.venue = venueService.getVenue(it) }
		command.notice?.let { appendLatestNotice(store, it) }
		command.bankName?.let { store.bankName = it }
		command.accountNumber?.let { store.accountNumber = it }
		command.entranceFee?.let { store.entranceFee = it }
		command.timeLimit?.let { store.timeLimit = it }

		return store.id ?: error("store id is null")
	}

	@Transactional
	fun replaceTimeSlots(user: User, storeId: UUID, timeSlots: List<StoreTimeSlotCommand>): UUID {
		require(timeSlots.isNotEmpty()) { "timeSlots must not be empty" }
		timeSlots.forEach {
			require(!it.endTime.isBefore(it.startTime)) { "endTime must be after startTime" }
		}

		val store = storeRepository.findById(storeId).orElseThrow {
			BusinessException(ErrorCode.STORE_NOT_FOUND)
		}

		syncTimeSlots(store, timeSlots)

		return store.id ?: error("store id is null")
	}

	/**
	 * - [slotId]가 있으면 해당 행의 start/end만 갱신
	 * - 없으면 신규 슬롯 추가
	 * - 기존에 있었는데 요청의 slotId 목록에 없으면 삭제 시도 (pass/pass_key 참조 시 STORE_018)
	 */
	private fun syncTimeSlots(store: Store, commands: List<StoreTimeSlotCommand>) {
		val storeId = store.id ?: error("store id is null")

		val requestedIds = commands.mapNotNull { it.slotId }
		require(requestedIds.size == requestedIds.toSet().size) { "timeSlots.slotId 중복" }

		val initialSlotIds = store.timeSlots.mapNotNull { it.id }.toSet()
		val idsToKeep = requestedIds.toSet()

		val currentById = store.timeSlots.mapNotNull { s -> s.id?.let { it to s } }.toMap()

		for (cmd in commands) {
			if (cmd.slotId != null) {
				val slot = currentById[cmd.slotId]
					?: throw BusinessException(ErrorCode.TIMESLOT_INVALID, "존재하지 않는 타임슬롯입니다.")
				if (slot.store.id != storeId) {
					throw BusinessException(ErrorCode.TIMESLOT_INVALID, "해당 스토어에 속하지 않는 타임슬롯입니다.")
				}
				slot.startTime = cmd.startTime
				slot.endTime = cmd.endTime
			} else {
				store.timeSlots += StoreTimeSlot(
					store = store,
					startTime = cmd.startTime,
					endTime = cmd.endTime,
				)
			}
		}

		val toRemove = store.timeSlots.filter { slot ->
			val id = slot.id ?: return@filter false
			id in initialSlotIds && id !in idsToKeep
		}
		for (slot in toRemove) {
			val id = slot.id!!
			if (
				passRepository.existsByTimeSlot_Id(id) ||
				passKeyRepository.existsBySlotId(id) ||
				orderRepository.existsByTimeSlot_Id(id)
			) {
				throw BusinessException(ErrorCode.TIMESLOT_REFERENCED)
			}
		}
		toRemove.forEach { store.timeSlots.remove(it) }
	}

	fun getMyStores(user: User): List<Store> {
		return storeRepository.findByHostOrderByNewestFirst(user)
			.onEach { it.initializeListViewFields() }
	}

	fun getActiveStores(): List<Store> {
		return storeRepository.findAll() // 활성화 여부와 관계없이 모든 스토어 반환
			.onEach { it.initializeListViewFields() }
	}

	fun getStoresForAdminHostTransfer(): List<Store> {
		return storeRepository.findAllForAdminHostTransfer()
	}

	@Transactional
	fun transferHost(storeId: UUID, targetUserId: UUID): Store {
		val store = storeRepository.findById(storeId).orElseThrow {
			BusinessException(ErrorCode.STORE_NOT_FOUND)
		}
		val targetUser = userRepository.findById(targetUserId).orElseThrow {
			BusinessException(ErrorCode.USER_NOT_FOUND)
		}

		store.host = targetUser
		return store
	}

	@Transactional
	fun createVenueForStore(storeId: UUID, request: VenueCreateRequest): Store {
		val store = storeRepository.findById(storeId).orElseThrow {
			BusinessException(ErrorCode.STORE_NOT_FOUND)
		}
		if (store.venue != null) {
			throw BusinessException(
				errorCode = ErrorCode.BAD_REQUEST,
				message = "이미 Venue가 연결된 주점입니다.",
			)
		}

		store.venue = venueService.createVenue(request)
		return store
	}

    fun searchActiveStoresByNameOrUniversity(query: String): List<Store> {
        val normalized = query.trim()
        if (normalized.isBlank()) {
            throw BusinessException(
                errorCode = ErrorCode.BAD_REQUEST,
                message = "query must not be blank",
            )
        }

        return storeRepository.searchActiveStoresByNameOrUniversityContaining(normalized)
            .filter { store ->
                store.isActive && store.venue != null
            }
            .onEach { it.initializeBoundsListViewFields() }
    }

    fun getActiveStoresByBounds(
        minLat: Double,
        maxLat: Double,
        minLng: Double,
        maxLng: Double,
    ): List<Store> {
        validateBounds(minLat, maxLat, minLng, maxLng)

        return storeRepository.findActiveStoresByBounds(minLat, maxLat, minLng, maxLng)
            .filter { store ->
                val venue = store.venue
                val isWithinBounds = venue != null &&
                    venue.latitude in minLat..maxLat &&
                    venue.longitude in minLng..maxLng
                store.isActive && isWithinBounds
            }
            .onEach { it.initializeBoundsListViewFields() }
    }

    fun getActiveStoresByDate(date: LocalDate): List<Store> {
        val dayStart = date.atStartOfDay()
        val dayEnd = date.plusDays(1).atStartOfDay()

        return storeRepository.findActiveStoresByDate(dayStart, dayEnd)
            .filter { store ->
                val overlaps = store.timeSlots.any { slot ->
                    slot.startTime.isBefore(dayEnd) && slot.endTime.isAfter(dayStart)
                }
                store.isActive && store.venue != null && overlaps
            }
            .onEach { it.initializeDateListViewFields() }
    }

    fun getStoreCountsByDateRange(startDate: LocalDate, endDate: LocalDate): Map<LocalDate, Int> {
        if (startDate.isAfter(endDate)) {
            throw BusinessException(
                errorCode = ErrorCode.BAD_REQUEST,
                message = "startDate must be on or before endDate",
            )
        }

        val rangeStart = startDate.atStartOfDay()
        val rangeEndExclusive = endDate.plusDays(1).atStartOfDay()
        val overlaps = storeRepository.findActiveStoreSlotOverlaps(rangeStart, rangeEndExclusive)

        val storesByDate = LinkedHashMap<LocalDate, MutableSet<UUID>>()
        var cursor = startDate
        while (!cursor.isAfter(endDate)) {
            storesByDate[cursor] = mutableSetOf()
            cursor = cursor.plusDays(1)
        }

        overlaps.forEach { overlap ->
            val clippedStart = if (overlap.slotStart.isAfter(rangeStart)) overlap.slotStart else rangeStart
            val clippedEndExclusive = if (overlap.slotEnd.isBefore(rangeEndExclusive)) overlap.slotEnd else rangeEndExclusive

            if (!clippedStart.isBefore(clippedEndExclusive)) return@forEach

            var dateCursor = clippedStart.toLocalDate()
            val lastDate = clippedEndExclusive.minusNanos(1).toLocalDate()
            while (!dateCursor.isAfter(lastDate)) {
                storesByDate[dateCursor]?.add(overlap.storeId)
                dateCursor = dateCursor.plusDays(1)
            }
        }

        val counts = LinkedHashMap<LocalDate, Int>()
        storesByDate.forEach { (date, storeIds) ->
            counts[date] = storeIds.size
        }
        return counts
    }

    private fun validateBounds(
        minLat: Double,
        maxLat: Double,
        minLng: Double,
        maxLng: Double,
    ) {
        if (minLat > maxLat) {
            throw BusinessException(
                errorCode = ErrorCode.BAD_REQUEST,
                message = "minLat must be less than or equal to maxLat",
            )
        }
        if (minLng > maxLng) {
            throw BusinessException(
                errorCode = ErrorCode.BAD_REQUEST,
                message = "minLng must be less than or equal to maxLng",
            )
        }
        if (minLat !in -90.0..90.0 || maxLat !in -90.0..90.0) {
            throw BusinessException(
                errorCode = ErrorCode.BAD_REQUEST,
                message = "latitude must be between -90 and 90",
            )
        }
        if (minLng !in -180.0..180.0 || maxLng !in -180.0..180.0) {
            throw BusinessException(
                errorCode = ErrorCode.BAD_REQUEST,
                message = "longitude must be between -180 and 180",
            )
        }
    }

	fun getStoreById(storeId: UUID): Store? {
		val store = storeRepository.findById(storeId).orElse(null) ?: return null
		store.initializeDetailViewFields()
		return store
	}

	private fun Store.initializeListViewFields() {
		venue?.let { v ->
			v.latitude
			v.longitude
		}
		mainImageUrls.toList()
		timeSlots.toList()
	}

	private fun Store.initializeDetailViewFields() {
		venue?.let { v ->
			v.address
			v.latitude
			v.longitude
		}
		mainImageUrls.toList()
		notices.toList()
		timeSlots.toList()
	}

    private fun Store.initializeDateListViewFields() {
        venue?.let { v ->
            v.address
            v.university?.let { u ->
                u.id
                u.name
            }
        }
        mainImageUrls.toList()
        timeSlots.toList()
    }

    private fun Store.initializeBoundsListViewFields() {
        venue?.let { v ->
            v.address
            v.latitude
            v.longitude
        }
        mainImageUrls.toList()
        timeSlots.toList()
    }

	@Transactional
	fun hardDeleteStore(storeId: UUID) {
		val store = storeRepository.findById(storeId).orElseThrow {
			BusinessException(ErrorCode.STORE_NOT_FOUND)
		}

		orderItemChangeRepository.deleteAllByStoreId(storeId)
		issueRepository.deleteAllByStoreId(storeId)
		storeSalesSummaryRepository.deleteAllByStoreId(storeId)
		menuSalesSummaryRepository.deleteAllByStoreId(storeId)
		callRequestRepository.deleteAllByStoreId(storeId)
		sessionParticipantRepository.deleteAllByStoreId(storeId)
		orderItemRepository.deleteAllByStoreId(storeId)
		orderRepository.deleteAllByStoreId(storeId)
		tableSessionRepository.deleteAllByStoreId(storeId)
		passRepository.deleteAllByStoreId(storeId)
		passKeyRepository.deleteAllByStoreId(storeId)
		storeMenuRepository.deleteAll(storeMenuRepository.findAllByStoreId(storeId))
		storeMenuCategoryRepository.deleteAll(storeMenuCategoryRepository.findAllByStoreIdOrderBySequenceAsc(storeId))
		callOptionRepository.deleteAll(callOptionRepository.findAllByStoreId(storeId))
		storeTableRepository.deleteAllByStoreId(storeId)
		storeTableZoneRepository.deleteAll(storeTableZoneRepository.findByStoreIdOrderByDisplayOrderAsc(storeId))
		storeScrapRepository.deleteAllByStoreId(storeId)
		guestbookEntryRepository.deleteTagsByStoreId(storeId)
		guestbookEntryRepository.deleteAllByStoreId(storeId)
		storeRepository.delete(store)
	}

	@Transactional
	fun assertHost(storeId: UUID, userId: UUID) {
		val store = storeRepository.findById(storeId)
			.orElseThrow { BusinessException(ErrorCode.STORE_NOT_FOUND, "존재하지 않는 store 입니다. storeId=$storeId")}

		val hostId = store.host.id
			?: throw BusinessException(ErrorCode.STORE_HOST_NOT_FOUND,"store.host.id 가 없습니다. storeId=$storeId")

		if (hostId != userId) {
			throw BusinessException(ErrorCode.INCORRECT_HOST_FORBIDDEN)
		}
	}
}
