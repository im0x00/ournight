package com.ournight.api_server.domain.store.domain

import com.ournight.api_server.domain.store.model.StoreMenuCategory
import com.ournight.api_server.domain.venue.model.VenueMenu
import jakarta.persistence.*
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class StoreMenuStatus {
	ON_SALE,      // 판매 중
	NOT_AVAILABLE // 판매 불가
}

enum class MenuBadge {
	BEST,  // 대표
	NEW,          // 신규
	POPULAR,      // 인기
}

@Entity
@Table(name = "store_menus")
@EntityListeners(AuditingEntityListener::class)
class StoreMenu(
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", columnDefinition = "UUID")
	val id: UUID? = null,

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "category_id", nullable = false) // 카테고리 필수 지정
	var category: StoreMenuCategory,

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "store_id", nullable = false, columnDefinition = "UUID")
	val store: Store,

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "venue_menu_id", nullable = true)
	val venueMenu: VenueMenu? = null,

	@Column(nullable = false)
	var name: String,

	@Column(nullable = false)
	var salePrice: BigDecimal,

	@Column(length = 500)
	var description: String? = null,

	@Column(name = "image_url")
	var imageUrl: String? = null,

	@Column(nullable = false)
	var sequence: Int = 0,

	@Column(name = "is_recommended", nullable = false)
	var isRecommended: Boolean = false,

	@Column(name = "is_unlimited", nullable = false)
	var isUnlimited: Boolean = false,

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	var status: StoreMenuStatus = StoreMenuStatus.ON_SALE,

	@Column(name = "is_deleted", nullable = false)
	var isDeleted: Boolean = false,

	@Column(name = "requires_cooking", nullable = false)
	var requiresCooking: Boolean = true,

	@Enumerated(EnumType.STRING)
	@ElementCollection
	@CollectionTable(name = "menu_badges", joinColumns = [JoinColumn(name = "menu_id", columnDefinition = "UUID")])
	@Column(name = "badge")
	var badges: MutableList<MenuBadge> = mutableListOf(),

	@LastModifiedDate
	@Column(name = "updated_at", nullable = false)
	var updatedAt: Instant = Instant.EPOCH
) {
	fun validateOrderable() {
		if (this.isDeleted || this.status == StoreMenuStatus.NOT_AVAILABLE) {
			throw IllegalStateException("메뉴 '${this.name}'은(는) 현재 주문 불가능합니다.")
		}
	}
}
