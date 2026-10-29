package com.ournight.api_server.domain.order.service

import com.ournight.api_server.api.customer.mapper.toResponse
import com.ournight.api_server.domain.order.model.CartLockedException
import com.ournight.api_server.domain.order.model.CartMeta
import com.ournight.api_server.domain.order.model.CartSnapshot
import com.ournight.api_server.domain.order.model.CartVersionMismatchException
import com.ournight.api_server.domain.order.model.CommitStartResult
import com.ournight.api_server.domain.order.model.OrderAlreadyExistsException
import com.ournight.api_server.domain.order.model.OrderStatus
import com.ournight.api_server.domain.order.model.OutOfStockException
import com.ournight.api_server.domain.order.repository.CartRedisRepository
import com.ournight.api_server.domain.order.repository.OrderRepository
import com.ournight.api_server.domain.session.repository.TableSessionRepository
import com.ournight.api_server.domain.store.dao.StoreMenuRepository
import com.ournight.api_server.domain.store.domain.StoreMenuStatus
import com.ournight.api_server.global.error.ErrorCode
import com.ournight.api_server.global.error.exception.BusinessException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class CartServiceImpl(
    private val cartRedisRepository: CartRedisRepository,
    private val orderService: OrderService,
    private val orderRepository: OrderRepository,
    private val tableSessionRepository: TableSessionRepository,
    private val storeMenuRepository: StoreMenuRepository,
): CartService {
    private val reorderBlockingStatuses = setOf(
        OrderStatus.PENDING_PAYMENT,
        OrderStatus.PAYMENT_REQUESTED,
    )

    private fun findBlockingOrder(tableId: UUID) =
        orderRepository.findLatestActiveSessionOrderByTableIdAndStatuses(
            tableId = tableId,
            statuses = reorderBlockingStatuses,
        )

    private fun isReorderBlockingStatus(status: String?): Boolean =
        status != null && reorderBlockingStatuses.any { it.name == status }

    private fun overlayBlockingOrder(tableId: UUID, meta: CartMeta): CartMeta {
        val blockingOrder = findBlockingOrder(tableId)

        if (blockingOrder != null) {
            return meta.copy(
                locked = true,
                orderId = blockingOrder.id,
                orderStatus = blockingOrder.status.name,
            )
        }

        if (isReorderBlockingStatus(meta.orderStatus)) {
            cartRedisRepository.clearOrderPointer(tableId)
            return meta.copy(orderId = null, orderStatus = null)
        }

        return meta
    }

    private fun ensureReorderAllowed(tableId: UUID) {
        val blockingOrder = findBlockingOrder(tableId)

        if (blockingOrder != null) {
            val redisMeta = cartRedisRepository.getMeta(tableId)
            throw OrderAlreadyExistsException(
                CartMeta(
                    v = redisMeta.v,
                    locked = true,
                    orderId = blockingOrder.id,
                    orderStatus = blockingOrder.status.name,
                ).toResponse()
            )
        }

        cartRedisRepository.clearOrderPointer(tableId)
    }

    override fun getMeta(tableId: UUID): CartMeta {
        // 현재 redis에 있는 meta정보 가져오기
        return overlayBlockingOrder(tableId, cartRedisRepository.getMeta(tableId))
    }

    override fun getSnapshot(tableId: UUID): CartSnapshot {
        // 현재 redis에 있는 cart snapshot정보 가져오기
        val snapshot = cartRedisRepository.getSnapshotAtomic(tableId)
        return snapshot.copy(meta = overlayBlockingOrder(tableId, snapshot.meta))
    }

    @Transactional
    override fun addDelta(
        tableId: UUID,
        menuId: UUID,
        delta: Int
    ): CartMeta {
        ensureReorderAllowed(tableId)
        ensureMenuCanBeAdded(tableId, menuId, delta)
        // cart 정보 redis에 쓰기 + meta 업뎃
        cartRedisRepository.checkRateLimit(tableId, "delta")
        return cartRedisRepository.applyDeltaAtomic(tableId, menuId, delta)
    }

    private fun ensureMenuCanBeAdded(tableId: UUID, menuId: UUID, delta: Int) {
        if (delta <= 0) return

        val session = tableSessionRepository.findActiveSessionByTableId(tableId)
            ?: throw BusinessException(ErrorCode.ACTIVE_SESSION_NOT_FOUND)
        val storeId = session.table.store.id!!
        val menu = storeMenuRepository.findByStoreIdAndIdForUpdate(storeId, menuId)
            ?: throw BusinessException(ErrorCode.MENU_NOT_FOUND)

        if (menu.status == StoreMenuStatus.NOT_AVAILABLE) {
            throw OutOfStockException(listOf(menuId))
        }
    }

    override fun commitCart(
        tableId: UUID,
        commitId: UUID,
        seenVersion: Int,
        userId: UUID
    ): UUID {
        ensureReorderAllowed(tableId)
        cartRedisRepository.checkRateLimit(tableId, "commit")
        val result = cartRedisRepository.commitStart(tableId, commitId, seenVersion)

        return when(result) {
            is CommitStartResult.AlreadyCommitted -> { result.orderId } // 이미 처리된 요청이면 저장된 orderId 반환 (Idempotency)
            is CommitStartResult.Locked -> { throw CartLockedException(result.meta.toResponse()) } // 423 Locked: 잠시 후 다시 시도
            is CommitStartResult.OrderConflict -> { throw OrderAlreadyExistsException(result.meta.toResponse()) }
            is CommitStartResult.VersionConflict -> { throw CartVersionMismatchException(result.meta.toResponse()) }
            is CommitStartResult.Success -> {
                try {
                    // 1. 주문 생성 시도
                    val orderId = orderService.createOrderFromCart(
                        tableId = tableId,
                        userId = userId,
                        commitId = commitId,
                        items = result.snapshot.items,
                        cartVersion = seenVersion
                    )
                    // 2. Redis 정리 시도
                    cartRedisRepository.commitFinalize(
                        tableId = tableId,
                        commitId = commitId,
                        seenVersion = seenVersion,
                        orderId = orderId,
                        lockToken = result.lockToken
                    )
                    return orderId
                } catch (e: Exception) {
                    val existingOrder = orderRepository.findByCommitId(commitId)

                    if (existingOrder != null) {
                        // CASE A: 이미 DB에 주문이 들어간 경우 -> Redis 정리 재시도
                        try {
                            cartRedisRepository.commitFinalize(
                                tableId = tableId,
                                commitId = commitId,
                                seenVersion = seenVersion,
                                orderId = existingOrder.id!!,
                                lockToken = result.lockToken
                            )
                            return existingOrder.id!!
                        } catch (finalizeEx: Exception) {
                            // Redis 정리조차 실패하면 어쩔 수 없이 에러 던짐 (Client가 Retry 하게 유도)
                            // 락은 TTL에 의해 자연 만료되도록 둠 (이미 DB 성공이라 Abort 하면 안 됨)
                            throw finalizeEx
                        }
                    } else {
                        // CASE B: DB에도 없음 (진짜 실패) -> 락 해제 후 에러 던짐
                        cartRedisRepository.commitAbort(tableId, result.lockToken)
                        throw e
                    }
                }
            }
        }
    }

    override fun getCommitResult(tableId: UUID, commitId: UUID): UUID? {
        // tableId, commitId 기반 redis: order_ptr 가져오기
        return cartRedisRepository.getCommitResult(tableId, commitId)
    }
}
