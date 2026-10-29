package com.ournight.api_server.domain.order.service

import com.ournight.api_server.domain.order.model.IssueExecutedForStatEvent
import com.ournight.api_server.domain.order.model.OrderConfirmedForStatEvent
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class SalesStatEventListener(private val summaryService: SalesSummaryUpdateService) {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleOrderConfirmed(event: OrderConfirmedForStatEvent) {
        summaryService.updateOrderStat(event)
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleIssueExecuted(event: IssueExecutedForStatEvent) {
        summaryService.updateRefundStat(event)
    }
}