package com.ournight.api_server.domain.store.application

import com.ournight.api_server.api.customer.dto.CustomerMenuResponse
import com.ournight.api_server.api.customer.dto.CustomerStoreMenuCategoryResponse
import com.ournight.api_server.api.host.dto.StoreMenuCategoryCreateRequest
import com.ournight.api_server.api.host.dto.StoreMenuCategoryResponse
import com.ournight.api_server.api.host.dto.StoreMenuCategoryUpdateRequest
import com.ournight.api_server.domain.store.dao.StoreMenuRepository
import com.ournight.api_server.domain.store.dao.StoreRepository
import com.ournight.api_server.domain.store.domain.StoreMenu
import org.springframework.data.repository.findByIdOrNull
import com.ournight.api_server.api.host.dto.StoreMenuCreateRequest
import com.ournight.api_server.api.host.dto.StoreMenuUpdateRequest
import com.ournight.api_server.api.pos.dto.KitchenMenuCategoryResponse
import com.ournight.api_server.api.pos.dto.KitchenMenuDetailResponse
import com.ournight.api_server.api.pos.dto.KitchenMenuPageResponse
import com.ournight.api_server.api.pos.dto.KitchenMenuSummaryResponse
import com.ournight.api_server.api.pos.dto.StoreMenuCategoryWithMenusResult
import com.ournight.api_server.api.pos.dto.StoreMenuResult
import com.ournight.api_server.api.pos.dto.UpdateKitchenMenuRequest
import com.ournight.api_server.api.pos.dto.toKitchenMenuDetailResponse
import com.ournight.api_server.api.pos.dto.toKitchenMenuSummaryResponse
import com.ournight.api_server.domain.notification.event.KitchenMenuChangedEvent
import com.ournight.api_server.domain.notification.event.PosReloadRequiredEvent
import com.ournight.api_server.domain.store.domain.StoreMenuStatus
import com.ournight.api_server.domain.store.model.StoreMenuCategory
import com.ournight.api_server.domain.store.repository.StoreMenuCategoryRepository
import com.ournight.api_server.domain.user.model.User
import com.ournight.api_server.global.error.ErrorCode
import com.ournight.api_server.global.error.exception.BusinessException
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Service
@Transactional(readOnly = true)
class StoreMenuService(
	private val storeMenuRepository: StoreMenuRepository,
	private val storeMenuCategoryRepository: StoreMenuCategoryRepository,
	private val storeRepository: StoreRepository,
	private val eventPublisher: ApplicationEventPublisher,
	) {

	@Transactional(readOnly = true)
	fun getMenuAndCategoriesByStoreId(storeId: UUID): List<CustomerStoreMenuCategoryResponse> {

		validateStoreExists(storeId)

		// 1. 카테고리와 메뉴를 한방 쿼리로 조회
		val categories = storeMenuCategoryRepository.findAllWithMenusByStoreId(storeId)

		// 2. Entity -> DTO 변환
		return categories.map { category ->
			CustomerStoreMenuCategoryResponse(
				categoryId = category.id,
				categoryName = category.name,
				items = category.menus
					.filterNot { it.isDeleted }
					.map { menu ->
						CustomerMenuResponse(
							menuId = menu.id!!,
							name = menu.name,
							price = menu.salePrice,
							imageUrl = menu.imageUrl,
							description = menu.description,
							isRecommended = menu.isRecommended,
							isUnlimited = menu.isUnlimited,
							status = menu.status.name,
							badges = menu.badges.toList()
						)
					}
			)
		}
	}

	@Transactional(readOnly = true)
	fun getLastUpdatedAtForStoreMenus(storeId: UUID): Instant {
		validateStoreExists(storeId)

		val menuMax = storeMenuRepository.findMaxUpdatedAtByStoreId(storeId) ?: Instant.EPOCH
		val catMax = storeMenuCategoryRepository.findMaxUpdatedAtByStoreId(storeId) ?: Instant.EPOCH
		return if (menuMax.isAfter(catMax)) menuMax else catMax
	}

	@Transactional
	fun createMenu(storeId: UUID, request: StoreMenuCreateRequest): StoreMenu {
		validateSalePrice(request.salePrice)

		val store = storeRepository.findByIdOrNull(storeId)
			?: throw BusinessException(ErrorCode.STORE_NOT_FOUND)

		val category = storeMenuCategoryRepository.findByIdOrNull(request.categoryId)
			?: throw BusinessException(ErrorCode.CATEGORY_NOT_FOUND)

		if (category.store.id != store.id) {
			throw BusinessException(ErrorCode.CATEGORY_STORE_MISMATCH)
		}

		val storeMenu = StoreMenu(
			store = store,
			category = category,
			name = request.name,
			salePrice = request.salePrice,
			description = request.description,
			imageUrl = request.imageUrl,
			sequence = request.sequence,
			isRecommended = request.isRecommended,
			isUnlimited = request.isUnlimited,
			status = request.status,
			requiresCooking = request.requiresCooking,
		)
		storeMenu.badges = request.badges.toMutableList()
		return storeMenuRepository.save(storeMenu).also {
			eventPublisher.publishEvent(PosReloadRequiredEvent(storeId))
		}
	}

	@Transactional
	fun updateMenu(id: UUID, request: StoreMenuUpdateRequest): StoreMenu {
		validateSalePrice(request.salePrice)

		val menu = storeMenuRepository.findByIdOrNull(id)
			?: throw BusinessException(ErrorCode.MENU_NOT_FOUND)
		if (menu.isDeleted) {
			throw BusinessException(ErrorCode.MENU_NOT_FOUND)
		}

		if (request.categoryId != null && request.categoryId != menu.category.id) {
			val newCategory = storeMenuCategoryRepository.findByIdOrNull(request.categoryId)
				?: throw BusinessException(ErrorCode.CATEGORY_NOT_FOUND)

			if (newCategory.store.id != menu.store.id) {
				throw BusinessException(ErrorCode.CATEGORY_STORE_MISMATCH)
			}

			menu.category = newCategory
		}

		menu.name = request.name
		menu.salePrice = request.salePrice
		menu.description = request.description
		menu.imageUrl = request.imageUrl
		menu.sequence = request.sequence
		menu.isRecommended = request.isRecommended
		menu.isUnlimited = request.isUnlimited
		menu.status = request.status
		request.badges?.let { menu.badges = it.toMutableList() }

		return storeMenuRepository.save(menu).also {
			eventPublisher.publishEvent(PosReloadRequiredEvent(it.store.id!!))
		}
	}

	@Transactional
	fun deleteMenu(storeId: UUID, id: UUID): Boolean {
		val menu = storeMenuRepository.findByIdOrNull(id) ?: return false
		if (menu.store.id != storeId) {
			throw BusinessException(ErrorCode.MENU_NOT_FOUND)
		}

		menu.isDeleted = true
		menu.status = StoreMenuStatus.NOT_AVAILABLE
		eventPublisher.publishEvent(PosReloadRequiredEvent(storeId))
		return true
	}

	// 단건 조회
	fun getMenuById(menuId: UUID): StoreMenu? {
		return storeMenuRepository.findByIdOrNull(menuId)?.takeUnless { it.isDeleted }
	}

	// 호스트 메뉴 수정용 단건 조회
	fun getMenuByStoreIdAndId(storeId: UUID, menuId: UUID): StoreMenu? {
		return storeMenuRepository.findByStoreIdAndIdWithCategoryAndBadges(storeId, menuId)
	}

	@Transactional
	fun createCategory(storeId: UUID, req: StoreMenuCategoryCreateRequest): StoreMenuCategory {
		val store = storeRepository.findByIdOrNull(storeId)
			?: throw BusinessException(ErrorCode.STORE_NOT_FOUND)

		// (optional) name 중복 방지 원하면 여기서 체크
		// if (storeMenuCategoryRepository.existsByStoreIdAndName(storeId, req.name)) ...

		return storeMenuCategoryRepository.save(
			StoreMenuCategory(
				name = req.name,
				sequence = req.sequence,
				store = store
			)
		).also {
			eventPublisher.publishEvent(PosReloadRequiredEvent(storeId))
		}
	}

	@Transactional
	fun updateCategory(storeId: UUID, categoryId: UUID, req: StoreMenuCategoryUpdateRequest): StoreMenuCategory {
		val c = storeMenuCategoryRepository.findByIdOrNull(categoryId)
			?: throw BusinessException(ErrorCode.CATEGORY_NOT_FOUND)

		if (c.store.id != storeId) throw BusinessException(ErrorCode.CATEGORY_STORE_MISMATCH)

		c.name = req.name
		c.sequence = req.sequence
		return storeMenuCategoryRepository.save(c).also {
			eventPublisher.publishEvent(PosReloadRequiredEvent(storeId))
		}
	}

	@Transactional
	fun deleteCategory(storeId: UUID, categoryId: UUID) {
		val c = storeMenuCategoryRepository.findByIdOrNull(categoryId)
			?: throw BusinessException(ErrorCode.CATEGORY_NOT_FOUND)

		if (c.store.id != storeId) throw BusinessException(ErrorCode.CATEGORY_STORE_MISMATCH)

	if (c.menus.isNotEmpty()) throw BusinessException(ErrorCode.CATEGORY_NOT_EMPTY)

	storeMenuCategoryRepository.delete(c)
	eventPublisher.publishEvent(PosReloadRequiredEvent(storeId))
	}

	@Transactional
	fun moveMenusAndDeleteCategory(storeId: UUID, categoryId: UUID, targetCategoryId: UUID) {
		if (categoryId == targetCategoryId) {
			throw BusinessException(ErrorCode.CATEGORY_STORE_MISMATCH)
		}

		val source = storeMenuCategoryRepository.findByIdOrNull(categoryId)
			?: throw BusinessException(ErrorCode.CATEGORY_NOT_FOUND)
		val target = storeMenuCategoryRepository.findByIdOrNull(targetCategoryId)
			?: throw BusinessException(ErrorCode.CATEGORY_NOT_FOUND)

		if (source.store.id != storeId || target.store.id != storeId) {
			throw BusinessException(ErrorCode.CATEGORY_STORE_MISMATCH)
		}

		val targetMenus = storeMenuRepository.findMenusByCategoryAndStore(targetCategoryId, storeId)
		val movedMenus = storeMenuRepository.findMenusByCategoryAndStore(categoryId, storeId)
		val baseSequence = targetMenus.maxOfOrNull { it.sequence } ?: 0

		movedMenus.forEachIndexed { index, menu ->
			menu.category = target
			menu.sequence = baseSequence + index + 1
		}
		storeMenuRepository.saveAll(movedMenus)
		storeMenuCategoryRepository.delete(source)
		eventPublisher.publishEvent(PosReloadRequiredEvent(storeId))
	}

	@Transactional(readOnly = true)
	fun listCategories(storeId: UUID): List<StoreMenuCategory> {
		validateStoreExists(storeId)
		return storeMenuCategoryRepository.findAllByStoreIdOrderBySequenceAsc(storeId)
	}

	@Transactional(readOnly = true)
	fun getCategoriesWithMenusForPos(storeId: UUID): List<StoreMenuCategoryWithMenusResult> {
		validateStoreExists(storeId)

		val categories = storeMenuCategoryRepository.findAllWithMenusByStoreId(storeId)

		return categories
			.sortedBy { it.sequence }
			.map { category ->
				StoreMenuCategoryWithMenusResult(
					id = category.id,
					name = category.name,
					sequence = category.sequence,
					menus = category.menus
						.filterNot { it.isDeleted }
						.sortedBy { it.sequence }
						.map { menu ->
							StoreMenuResult(
								id = menu.id!!,
								categoryId = category.id,
								name = menu.name,
								salePrice = menu.salePrice,
								imageUrl = menu.imageUrl,
								isSoldOut = menu.status == StoreMenuStatus.NOT_AVAILABLE
							)
						}
				)
			}
	}

	@Transactional(readOnly = true)
	fun getKitchenMenus(storeId: UUID, user: User): KitchenMenuPageResponse {
		validateStoreExists(storeId)

		val categories = storeMenuCategoryRepository.findAllWithMenusByStoreId(storeId)

		return KitchenMenuPageResponse(
			categories = categories
				.sortedBy { it.sequence }
				.map { category ->
					KitchenMenuCategoryResponse(
						categoryId = category.id,
						categoryName = category.name,
						sequence = category.sequence
					)
				},
			menus = categories
				.flatMap { category ->
					category.menus.filterNot { it.isDeleted }.map { menu ->
						menu.toKitchenMenuSummaryResponse()
					}
				}
				.sortedWith(
					compareBy<KitchenMenuSummaryResponse> { it.categoryId.toString() }
						.thenBy { it.name }
				)
		)
	}

	@Transactional
	fun updateKitchenMenu(
		storeId: UUID,
		menuId: UUID,
		user: User,
		request: UpdateKitchenMenuRequest,
	): KitchenMenuDetailResponse {
		validateStoreExists(storeId)

		val menu = storeMenuRepository.findByStoreIdAndIdForUpdate(storeId, menuId)
			?: throw BusinessException(ErrorCode.MENU_NOT_FOUND)

		request.status?.let { newStatus ->
			menu.status = newStatus
		}

		request.inventory?.enabled?.let { enabled ->
			menu.isUnlimited = !enabled
		}

		val saved = storeMenuRepository.save(menu)
		eventPublisher.publishEvent(
			KitchenMenuChangedEvent(
				storeId = storeId,
				menuId = saved.id!!,
				categoryId = saved.category.id,
				name = saved.name,
				salePrice = saved.salePrice,
				imageUrl = saved.imageUrl,
				status = saved.status,
				inventoryEnabled = !saved.isUnlimited,
				inventoryQuantity = null,
				updatedAt = saved.updatedAt,
			)
		)

		return saved.toKitchenMenuDetailResponse()
	}

	private fun validateStoreExists(storeId: UUID) {
		if (!storeRepository.existsById(storeId)) {
			throw BusinessException(ErrorCode.STORE_NOT_FOUND)
		}
	}

	private fun validateSalePrice(salePrice: BigDecimal) {
		if (salePrice <= BigDecimal.ZERO) {
			throw BusinessException(ErrorCode.BAD_REQUEST, "메뉴 가격은 1원 이상이어야 합니다.")
		}
	}

}
