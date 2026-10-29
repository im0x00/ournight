package com.ournight.api_server.domain.order.repository

import com.ournight.api_server.domain.order.domain.Order
import com.ournight.api_server.domain.order.domain.OrderItem
import com.ournight.api_server.domain.order.view.AdminOrderSummaryView
import com.ournight.api_server.domain.order.domain.QOrder.order
import com.ournight.api_server.domain.order.domain.QOrderItem.orderItem
import com.ournight.api_server.domain.order.model.OrderCursor
import com.ournight.api_server.domain.order.model.OrderStatus
import com.ournight.api_server.domain.session.model.QTableSession.tableSession
import com.ournight.api_server.domain.session.model.SessionStatus
import com.ournight.api_server.domain.store.domain.QStoreMenu.storeMenu
import com.ournight.api_server.domain.store.domain.QStore.store
import com.ournight.api_server.domain.store.domain.QStoreTable.storeTable
import com.querydsl.core.types.dsl.BooleanExpression

import com.querydsl.jpa.impl.JPAQueryFactory
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

interface OrderRepository : JpaRepository<Order, UUID>, OrderRepositoryCustom {
    // 홀 입금 확인 후보 조회용
    @Query("""
        SELECT o FROM Order o 
        JOIN FETCH o.session s
        JOIN FETCH s.table t
        WHERE t.store.id = :storeId
          AND o.status IN :statuses
          AND o.timeSlot.id in (:timeSlotIds)
        ORDER BY o.orderedAt ASC
    """)
    fun findAllByStoreIdAndTimeSlotIdAndStatuses(
        @Param("storeId") storeId: UUID,
        @Param("timeSlotIds") timeSlotIds: Collection<UUID>,
        @Param("statuses") statuses: Collection<OrderStatus>,
    ): List<Order>

    @Query("""
        SELECT o FROM Order o 
        JOIN FETCH o.session s
        JOIN FETCH s.table t
        WHERE t.store.id = :storeId
          AND o.status IN :statuses
          AND s.id in (:sessionIds)
        ORDER BY o.orderedAt ASC
    """)
    fun findAllByStoreIdAndSessionIdAndStatuses(
        @Param("storeId") storeId: UUID,
        @Param("sessionIds") sessionIds: Collection<UUID>,
        @Param("statuses") statuses: Collection<OrderStatus>,
    ): List<Order>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT o FROM Order o
        WHERE o.id = :id
    """)
    fun findByIdForUpdate(@Param("id") id: UUID): Order?


    @Query("""
        select distinct o from Order o
        join fetch o.session s
        join fetch s.table t
        left join fetch o.orderItems oi
        left join fetch oi.menu m
        where t.store.id = :storeId
          and o.timeSlot.id = :timeSlotId
          and s.status in :sessionStatuses
        order by o.orderedAt asc, t.tableNumber asc 
        """
    )
    fun findAllPosOrdersByTimeSlotIdAndSessionStatus(
        @Param("storeId") storeId: UUID,
        @Param("timeSlotId") timeSlotId: UUID,
        @Param("sessionStatuses") sessionStatuses: List<SessionStatus>
    ): List<Order>

    @Query("""
        select distinct o from Order o
        join fetch o.session s
        join fetch s.table t
        left join fetch o.orderItems oi
        left join fetch oi.menu m
        where t.store.id = :storeId
          and t.id = :tableId
          and o.timeSlot.id = :timeSlotId
          and s.status in :sessionStatuses
        order by o.orderedAt asc
        """
    )
    fun findAllPosOrdersByTimeSlotIdAndTableAndSessionStatus(
        @Param("storeId") storeId: UUID,
        @Param("tableId") tableId: UUID,
        @Param("timeSlotId") timeSlotId: UUID,
        @Param("sessionStatuses") sessionStatuses: List<SessionStatus>
    ): List<Order>

    fun findByCommitId(commitId: UUID): Order?

    @Query("""
        select distinct o
        from Order o
        join o.session s
        join s.participants sp
        join sp.pass p
        where p.user.id = :userId
          and o.session.table.store.id = :storeId
        order by o.orderedAt desc, o.sessionOrderSequence desc
    """)
    fun findUserOrders(
        @Param("userId") userId: UUID,
        @Param("storeId") storeId: UUID
    ): List<Order>

    @Query("""
        select distinct o
        from Order o
        join o.session s
        join s.participants sp
        join sp.pass p
        where p.user.id = :userId
          and s.table.store.id = :storeId
          and s.table.id = :tableId
          and s.status = :sessionStatus
        order by o.orderedAt desc, o.sessionOrderSequence desc
    """)
    fun findUserOrdersInSession(
        @Param("userId") userId: UUID,
        @Param("storeId") storeId: UUID,
        @Param("tableId") tableId: UUID,
        @Param("sessionStatus") sessionStatus: SessionStatus,
    ): List<Order>

    @Query("""
        select distinct o
        from Order o
        left join fetch o.orderItems oi
        left join fetch oi.menu m
        where o.session.id = :sessionId
          and o.timeSlot.id = :timeSlotId
        order by o.sessionOrderSequence desc 
    """)
    fun findWithItemsBySessionIdAndTimeSlotId(
        @Param("sessionId") sessionId: UUID,
        @Param("timeSlotId") timeSlotId: UUID,
    ): List<Order>

    fun existsOpenOrderBySessionId(sessionId: UUID): Boolean

    fun existsByTimeSlot_Id(timeSlotId: UUID): Boolean

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        delete from Order o
        where o.id in (
            select o2.id
            from Order o2
            where o2.session.table.store.id = :storeId
        )
        """
    )
    fun deleteAllByStoreId(@Param("storeId") storeId: UUID): Int

    @Query(
        """
        SELECT new com.ournight.api_server.domain.order.view.AdminOrderSummaryView(
            o.id, o.status, o.totalAmount, o.orderedAt, t.tableNumber, st.id, st.name
        )
        FROM Order o
        JOIN o.session s
        JOIN s.table t
        JOIN t.store st
        """
    )
    fun findAllSummariesForAdmin(pageable: Pageable): Page<AdminOrderSummaryView>

    @Query("""
        select distinct o
        from Order o
        join fetch o.session s
        left join fetch o.orderItems oi
        where s.id in :sessionIds
    """)
    fun findBySessionIds(@Param("sessionIds") sessionIds: List<UUID>): List<Order>

    @Query("""
        select count(o) > 0
        from Order o
        where o.session.id = :sessionId
    """)
    fun existsBySessionId(@Param("sessionId") sessionId: UUID): Boolean

    @Query("""
        select case when count(o) > 0 then true else false end
        from Order o
        where o.session.id = :sessionId
          and o.status = com.ournight.api_server.domain.order.model.OrderStatus.CONFIRMED
    """)
    fun existsConfirmedOrderBySessionId(@Param("sessionId") sessionId: UUID): Boolean


    @Query("""
        select o
        from Order o
        where o.session.id = :sessionId
          and o.status in :statuses
    """)
    fun findBySessionIdAndStatuses(
        sessionId: UUID,
        statuses: Collection<OrderStatus>
    ): List<Order>

    @Query("""
        select count(o) > 0
        from Order o
        where o.session.id = :sessionId
          and o.status in :statuses
    """)
    fun existsBySessionIdAndStatuses(
        sessionId: UUID,
        statuses: Collection<OrderStatus>
    ): Boolean
}

interface OrderRepositoryCustom {
    fun findOrderWithDetails(orderId: UUID): Order?
    fun findByTableIdAndCommitId(tableId: UUID, commitId: UUID): Order?
    fun findLatestActiveSessionOrderByTableIdAndStatuses(
        tableId: UUID,
        statuses: Collection<OrderStatus>
    ): Order?
    fun findHallOrdersPage(
        storeId: UUID,
        status: OrderStatus,
        activeTimeSlotIds: Collection<UUID>,
        activeSessionIds: Collection<UUID>,
        cursor: OrderCursor?,
        limitPlusOne: Int,
    ): List<Order>

    fun findItemsByOrderIds(orderIds: List<UUID>): List<OrderItem>

    fun findKitchenOrdersPage(
        storeId: UUID,
        status: OrderStatus,
        activeTimeSlotIds: Collection<UUID>,
        activeSessionIds: Collection<UUID>,
        cursor: OrderCursor?,
        limitPlusOne: Int
    ): List<Order>

    fun findByStoreIdAndIds(
        storeId: UUID,
        orderIds: Collection<UUID>,
        activeTimeSlotIds: Collection<UUID>,
        activeSessionIds: Collection<UUID>,
    ): List<Order>

}

@Repository
class OrderRepositoryImpl(
    private val queryFactory: JPAQueryFactory
) : OrderRepositoryCustom {

    override fun findOrderWithDetails(orderId: UUID): Order? {
        return queryFactory
            .selectFrom(order)
            .join(order.session, tableSession).fetchJoin()
            .join(tableSession.table, storeTable).fetchJoin()
            .leftJoin(order.orderItems, orderItem).fetchJoin()
            .leftJoin(orderItem.menu, storeMenu).fetchJoin()
            .where(order.id.eq(orderId))
            .distinct()
            .fetchOne()
    }

    override fun findByTableIdAndCommitId(tableId: UUID, commitId: UUID): Order? {
        return queryFactory
            .selectFrom(order)
            .join(order.session, tableSession)
            .where(
                tableSession.table.id.eq(tableId),
                order.commitId.eq(commitId)
            )
            .fetchOne()
    }

    override fun findLatestActiveSessionOrderByTableIdAndStatuses(
        tableId: UUID,
        statuses: Collection<OrderStatus>
    ): Order? {
        if (statuses.isEmpty()) return null
        return queryFactory
            .selectFrom(order)
            .join(order.session, tableSession).fetchJoin()
            .where(
                tableSession.table.id.eq(tableId),
                tableSession.status.eq(SessionStatus.ACTIVE),
                order.status.`in`(statuses)
            )
            .orderBy(order.orderedAt.desc(), order.id.desc())
            .fetchFirst()
    }

    override fun findHallOrdersPage(
        storeId: UUID,
        status: OrderStatus,
        activeTimeSlotIds: Collection<UUID>,
        activeSessionIds: Collection<UUID>,
        cursor: OrderCursor?,
        limitPlusOne: Int,
    ): List<Order> {
        val operationalPredicate = operationalOrderPredicate(activeTimeSlotIds, activeSessionIds)
            ?: return emptyList()

        return queryFactory
            .selectFrom(order)
            .join(order.session, tableSession).fetchJoin()
            .join(tableSession.table, storeTable).fetchJoin()
            .where(
                storeTable.store.id.eq(storeId),
                operationalPredicate,
                order.status.eq(status),
                hallCursorPredicate(cursor)
            )
            .orderBy(order.orderedAt.desc(), order.id.desc())
            .limit(limitPlusOne.toLong())
            .fetch()
    }

    private fun hallCursorPredicate(cursor: OrderCursor?): BooleanExpression? {
        if (cursor == null) return null
        return order.orderedAt.lt(cursor.orderedAt)
            .or(order.orderedAt.eq(cursor.orderedAt).and(order.id.lt(cursor.orderId)))
    }

    override fun findItemsByOrderIds(orderIds: List<UUID>): List<OrderItem> {
        if (orderIds.isEmpty()) return emptyList()
        return queryFactory
            .selectFrom(orderItem)
            .join(orderItem.order, order).fetchJoin()
            .where(order.id.`in`(orderIds))
            .fetch()
    }


    override fun findKitchenOrdersPage(
        storeId: UUID,
        status: OrderStatus,
        activeTimeSlotIds: Collection<UUID>,
        activeSessionIds: Collection<UUID>,
        cursor: OrderCursor?,
        limitPlusOne: Int
    ): List<Order> {
        val operationalPredicate = operationalOrderPredicate(activeTimeSlotIds, activeSessionIds)
            ?: return emptyList()

        return queryFactory
            .selectFrom(order)
            .join(order.session, tableSession).fetchJoin()
            .join(tableSession.table, storeTable).fetchJoin()
            .where(
                storeTable.store.id.eq(storeId),
                operationalPredicate,
                order.status.eq(status),
                kitchenCursorPredicate(cursor)
            )
            .orderBy(order.orderedAt.asc(), order.id.asc())
            .limit(limitPlusOne.toLong())
            .fetch()
    }

    private fun kitchenCursorPredicate(cursor: OrderCursor?): BooleanExpression? {
        if (cursor == null) return null
        return order.orderedAt.gt(cursor.orderedAt)
            .or(order.orderedAt.eq(cursor.orderedAt).and(order.id.gt(cursor.orderId)))
    }

    override fun findByStoreIdAndIds(
        storeId: UUID,
        orderIds: Collection<UUID>,
        activeTimeSlotIds: Collection<UUID>,
        activeSessionIds: Collection<UUID>,
    ): List<Order> {
        if (orderIds.isEmpty()) return emptyList()

        val operationalPredicate = operationalOrderPredicate(activeTimeSlotIds, activeSessionIds)
            ?: return emptyList()

        return queryFactory
            .selectFrom(order)
            .join(order.session, tableSession).fetchJoin()
            .join(tableSession.table, storeTable).fetchJoin()
            .where(
                storeTable.store.id.eq(storeId),
                operationalPredicate,
                order.id.`in`(orderIds),
            )
            .orderBy(order.orderedAt.desc(), order.id.desc())
            .fetch()
    }

    private fun operationalOrderPredicate(
        activeTimeSlotIds: Collection<UUID>,
        activeSessionIds: Collection<UUID>,
    ): BooleanExpression? {
        val activeSlotPredicate = activeTimeSlotIds
            .takeIf { it.isNotEmpty() }
            ?.let { order.timeSlot.id.`in`(it) }
        val activeSessionPredicate = activeSessionIds
            .takeIf { it.isNotEmpty() }
            ?.let { tableSession.id.`in`(it) }

        return when {
            activeSlotPredicate != null && activeSessionPredicate != null ->
                activeSlotPredicate.or(activeSessionPredicate)
            activeSlotPredicate != null -> activeSlotPredicate
            else -> activeSessionPredicate
        }
    }
}
