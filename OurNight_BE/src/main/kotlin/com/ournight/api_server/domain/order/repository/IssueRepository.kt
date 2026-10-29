package com.ournight.api_server.domain.order.repository

import com.ournight.api_server.domain.order.model.Issue
import com.ournight.api_server.domain.order.model.IssueStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface IssueRepository : JpaRepository<Issue, UUID> {

    @Query(
        """
        select i from Issue i
        where i.orderItemId in :orderItemIds
        """
    )
    fun findAllByOrderItemIds(
        @Param("orderItemIds") orderItemIds: List<UUID>
    ): List<Issue>

    @Query(
        """
        select i from Issue i
        where i.status = :status
          and i.orderId in :orderIds
        """
    )
    fun findAllByStatusAndOrderIds(
        @Param("status") status: IssueStatus,
        @Param("orderIds") orderIds: List<UUID>
    ): List<Issue>

    fun findAllByStoreIdAndTimeSlotIdAndStatusOrderByOpenedAtDesc(
        storeId: UUID,
        timeSlotId: UUID,
        status: IssueStatus,
    ): List<Issue>

    fun findAllByStoreIdAndTimeSlotIdAndStatusInOrderByOpenedAtDesc(
        storeId: UUID,
        timeSlotId: UUID,
        statuses: List<IssueStatus>,
    ): List<Issue>

    fun findByIdAndStoreId(issueId: UUID, storeId: UUID): Issue?

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    fun deleteAllByStoreId(storeId: UUID): Int
}
