package com.ournight.api_server.domain.order.repository

import com.ournight.api_server.domain.order.domain.OrderItem
import com.ournight.api_server.domain.order.model.OrderItemStatus
import com.ournight.api_server.domain.order.model.OrderStatus
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface OrderItemRepository : JpaRepository<OrderItem, UUID> {

    @Query("""
        select oi
        from OrderItem oi
        join fetch oi.menu m
        where oi.order.id = :id
    """)
    fun findAllByOrderIdWithMenu(@Param("id") id: UUID): List<OrderItem>

    @Query("""
        select oi
        from OrderItem oi
        join fetch oi.order o
        join fetch oi.menu m
        where oi.id = :id
    """)
    fun findByIdWithOrderAndMenu(@Param("id") id: UUID): OrderItem?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select oi
        from OrderItem oi
        join fetch oi.order o
        join fetch oi.menu m
        where oi.order.id = :id
    """)
    fun findAllByOrderIdForUpdate(@Param("id") id: UUID): List<OrderItem>

    @Query("""
        select oi
        from OrderItem oi
        join fetch oi.order o
        join fetch o.session s
        join fetch s.table t
        join fetch oi.menu m
        where t.store.id = :storeId
          and oi.status in :statuses
    """)
    fun findAllByStoreIdAndStatusIn(
        @Param("storeId") storeId: UUID,
        @Param("statuses") statuses: List<OrderItemStatus>
    ): List<OrderItem>

    @Query("""
        select oi
        from OrderItem oi
        join fetch oi.order o
        join fetch oi.menu m
        where o.id in :orderIds
        order by o.id asc, oi.id asc
    """)
    fun findAllByOrderIdIn(
        @Param("orderIds") orderIds: List<UUID>
    ): List<OrderItem>

    @Query("""
        select oi
        from OrderItem oi
        join fetch oi.menu m
        join fetch oi.order o 
        where o.id in :orderIds
            and m.requiresCooking = true
    """)
    fun findKitchenItemsByOrderIds(
        @Param("orderIds") orderIds: List<UUID>,
    ): List<OrderItem>

    @Query("""
        select oi
        from OrderItem oi
        join fetch oi.order o
        join fetch o.session s
        join fetch s.table t
        join fetch oi.menu m
        where t.store.id = :storeId
          and oi.status = :status
    """)
    fun findAllByStoreIdAndStatus(
        @Param("storeId") storeId: UUID,
        @Param("status") status: OrderItemStatus
    ): List<OrderItem>

    @Query("""
        select oi
        from OrderItem oi
        join fetch oi.order o
        join fetch o.session s
        join fetch s.table t
        join fetch oi.menu m
        where t.store.id = :storeId
          and o.status = :orderStatus
          and o.timeSlot.id in (:timeSlotIds)
          and oi.status in :itemStatuses
    """)
    fun findAllServingPending(
        @Param("storeId") storeId: UUID,
        @Param("timeSlotIds") timeSlotIds: Collection<UUID>,
        @Param("orderStatus") orderStatus: OrderStatus,
        @Param("itemStatuses") itemStatuses: Collection<OrderItemStatus>,
    ): List<OrderItem>

    @Query("""
        select oi
        from OrderItem oi
        join fetch oi.order o
        join fetch o.session s
        join fetch s.table t
        join fetch oi.menu m
        where t.store.id = :storeId
          and o.status = :orderStatus
          and s.id in (:sessionIds)
          and oi.status in :itemStatuses
    """)
    fun findAllServingPendingBySessionIds(
        @Param("storeId") storeId: UUID,
        @Param("sessionIds") sessionIds: Collection<UUID>,
        @Param("orderStatus") orderStatus: OrderStatus,
        @Param("itemStatuses") itemStatuses: Collection<OrderItemStatus>,
    ): List<OrderItem>

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        delete from OrderItem oi
        where oi.id in (
            select oi2.id
            from OrderItem oi2
            where oi2.order.session.table.store.id = :storeId
        )
        """
    )
    fun deleteAllByStoreId(@Param("storeId") storeId: UUID): Int
}
