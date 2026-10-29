package com.ournight.api_server.domain.order.repository

import com.ournight.api_server.domain.order.domain.OrderItemChange
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface OrderItemChangeRepository : JpaRepository<OrderItemChange, UUID> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        delete from OrderItemChange c
        where c.orderItem.id in (
            select oi.id
            from OrderItem oi
            where oi.order.session.table.store.id = :storeId
        )
        """
    )
    fun deleteAllByStoreId(@Param("storeId") storeId: UUID): Int
}
