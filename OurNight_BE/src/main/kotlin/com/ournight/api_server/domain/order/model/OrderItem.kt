package com.ournight.api_server.domain.order.domain

import com.ournight.api_server.domain.order.model.AdjustmentReasonType
import com.ournight.api_server.domain.order.model.OrderItemStatus
import com.ournight.api_server.domain.store.domain.StoreMenu
import com.ournight.api_server.global.error.ErrorCode
import com.ournight.api_server.global.error.exception.BusinessException
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

enum class OrderItemChangeType {
	DECREASED,   // 일부 감소
	CANCELED     // 전량 취소
}


@Entity
@Table(name = "order_item_changes")
class OrderItemChange(
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", columnDefinition = "UUID")
	val id: UUID? = null,

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "order_item_id", nullable = false, columnDefinition = "UUID")
	val orderItem: OrderItem,

	@Enumerated(EnumType.STRING)
	@Column(name = "type", nullable = false, length = 30)
	val type: OrderItemChangeType,

	@Column(name = "delta", nullable = false)
	val delta: Int, // 항상 양수

	@Column(name = "before_qty", nullable = false)
	val beforeQty: Int,

	@Column(name = "after_qty", nullable = false)
	val afterQty: Int,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime,

	@Enumerated(EnumType.STRING)
	@Column(name = "reason_type", length = 50)
	val reasonType: AdjustmentReasonType? = null,
)

@Entity
@Table(name = "order_items")
class OrderItem(
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", columnDefinition = "UUID")
	val id: UUID? = null,

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "order_id", nullable = false, columnDefinition = "UUID")
	val order: Order,

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "store_menu_id", nullable = false, columnDefinition = "UUID")
	val menu: StoreMenu,

	@Column(nullable = false)
	var quantity: Int,

	@Column(nullable = false)
	var price: BigDecimal,

	@Column(nullable = false, length = 100)
	var name: String,

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	var status: OrderItemStatus = OrderItemStatus.WAITING,

	@Version
	@Column(nullable = false)
	var version: Long = 0L,
) {

	fun paymentConfirmed(nextStatus: OrderItemStatus) {
		if (this.status == OrderItemStatus.CANCELED) return
		if (this.status != OrderItemStatus.WAITING) {
			throw BusinessException(ErrorCode.INVALID_ORDER_STATUS, "WAITING 상태의 item만 결제 확정 처리할 수 있습니다.")
		}
		this.status = nextStatus
	}

	fun ensureCancelable() {
		if (this.status == OrderItemStatus.SERVED) {
			throw BusinessException(ErrorCode.INVALID_ORDER_STATUS, "이미 served 된 item은 cancel 못함. refund flow 써야 함.")
		}
		if (this.status == OrderItemStatus.CANCELED) {
			throw BusinessException(ErrorCode.INVALID_ORDER_STATUS, "이미 canceled 된 item입니다.")
		}
	}

	fun ensureDecreasable() {
		if (this.status == OrderItemStatus.SERVED) {
			throw BusinessException(ErrorCode.INVALID_ORDER_STATUS, "served item은 qty change 못함. refund flow.")
		}
		if (this.status == OrderItemStatus.CANCELED) {
			throw BusinessException(ErrorCode.INVALID_ORDER_STATUS, "canceled item은 qty change 못함.")
		}
	}

	fun applyDecrease(delta: Int) {
		if (delta <= 0) {
			throw BusinessException(ErrorCode.INVALID_ORDER_STATUS, "감소 수량은 1 이상이어야 합니다.")
		}
		if (this.status == OrderItemStatus.SERVED) {
			throw BusinessException(ErrorCode.INVALID_ORDER_STATUS, "served item은 수량 감소 불가")
		}
		if (this.status == OrderItemStatus.CANCELED) {
			throw BusinessException(ErrorCode.INVALID_ORDER_STATUS, "canceled item은 수량 감소 불가")
		}
		if (delta > this.quantity) {
			throw BusinessException(ErrorCode.ORDER_NEGATIVE_QTY)
		}

		val remainQty = this.quantity - delta
		this.quantity = remainQty

		if (remainQty == 0) {
			this.status = OrderItemStatus.CANCELED
		}
	}

	fun cancelAll() {
		ensureCancelable()
		this.status = OrderItemStatus.CANCELED
		this.quantity = 0
	}

	fun completeCooking() {
		if (this.status != OrderItemStatus.COOKING) {
			throw BusinessException(ErrorCode.INVALID_ORDER_STATUS, "조리 중인 메뉴만 완료 처리할 수 있습니다.")
		}
		this.status = OrderItemStatus.COOKED
	}

	fun completeServing() {
		this.status = OrderItemStatus.SERVED
	}

	fun updateStatus(newStatus: OrderItemStatus) {
		this.status = newStatus
	}
}
