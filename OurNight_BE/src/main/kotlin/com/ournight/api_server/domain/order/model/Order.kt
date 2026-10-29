package com.ournight.api_server.domain.order.domain

import com.ournight.api_server.domain.order.model.OrderItemStatus
import com.ournight.api_server.domain.order.model.OrderStatus
import com.ournight.api_server.domain.session.model.TableSession
import com.ournight.api_server.domain.store.domain.Store
import com.ournight.api_server.domain.store.domain.StoreTable
import com.ournight.api_server.domain.store.model.StoreTimeSlot
import com.ournight.api_server.global.error.ErrorCode
import com.ournight.api_server.global.error.exception.BusinessException
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(
	name = "orders",
	uniqueConstraints = [
		UniqueConstraint(name = "uk_order_commit", columnNames = ["session_id", "commit_id"])
	]
)
class Order(
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", columnDefinition = "UUID")
	val id: UUID? = null,

	@Version
	@Column(nullable = false)
	var version: Long = 0L,

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "session_id", nullable = false, columnDefinition = "UUID")
    var session: TableSession,

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "time_slot_id", nullable = false, columnDefinition = "UUID")
	val timeSlot: StoreTimeSlot,

    @Column(name = "ordered_at", nullable = false)
    var orderedAt: LocalDateTime,

	@Column(name = "completed_at")
	var completedAt: LocalDateTime? = null,

	@Column(name="total_amount")
	var totalAmount: BigDecimal = BigDecimal.ZERO,

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	var status: OrderStatus = OrderStatus.PENDING_PAYMENT,

	@OneToMany(mappedBy = "order", cascade = [CascadeType.ALL], fetch = FetchType.LAZY, orphanRemoval = true)
	val orderItems: MutableList<OrderItem> = mutableListOf(),

	@Column(name = "commit_id", nullable = false, columnDefinition = "UUID")
	val commitId: UUID,

	@Column(name = "cart_version", nullable = false)
	val cartVersion: Int,

	val sessionOrderSequence: Int
) {

	val table: StoreTable
		get() = session.table

	val store: Store
		get() = session.table.store

	// 손님: cart
	fun addOrderItem(item: OrderItem) {
		this.orderItems.add(item)
		this.totalAmount = this.totalAmount.add(item.price.multiply(BigDecimal(item.quantity)))
	}


	//손님: 입금 확인 요청
	fun markPaymentVerifying() {
		if (this.status != OrderStatus.PENDING_PAYMENT) {
			throw IllegalStateException("입금 대기 상태에서만 입금 확인 요청이 가능합니다.")
		}
		this.status = OrderStatus.PAYMENT_REQUESTED
	}

	fun confirm() {
		this.status = OrderStatus.CONFIRMED
	}

	// 시스템: 전체 서빙 완료 체크
    fun checkAndCompleteOrder(now: LocalDateTime) {
        val activeItems = this.orderItems.filter { it.status != OrderItemStatus.CANCELED }

		if (activeItems.isEmpty()) {
			this.status = OrderStatus.CANCELED
			return
		}

		// 취소되지 않은 모든 아이템이 서빙 완료되었는지 확인
		val allServed = activeItems.all { it.status == OrderItemStatus.SERVED }

		if (allServed) {
			this.status = OrderStatus.COMPLETED
			this.completedAt = now
		}
	}

	// 주문 전체 취소
	fun cancel() {
		if (this.status == OrderStatus.COMPLETED) {
			throw BusinessException(ErrorCode.INVALID_ORDER_STATUS, "이미 완료된 주문입니다.")
		}
		this.status = OrderStatus.CANCELED
	}

	internal fun attachItem(item: OrderItem) {
		this.orderItems.add(item)
	}

	fun recomputeTotalAmount() {
		this.totalAmount = this.orderItems
			.filter { it.status != OrderItemStatus.CANCELED }
			.fold(BigDecimal.ZERO) { acc, it ->
				acc.add(it.price.multiply(BigDecimal(it.quantity)))
			}
	}

	fun reassignTo(targetSession: TableSession) {
		require(this.status in REASSIGNABLE_STATUSES) {
			"진행 중인 주문만 다른 session으로 이동할 수 있습니다."
		}
		this.session = targetSession
	}

	companion object {
		private val REASSIGNABLE_STATUSES = setOf(
			OrderStatus.PENDING_PAYMENT,
			OrderStatus.PAYMENT_REQUESTED,
			OrderStatus.CONFIRMED,
		)
	}

}
