package com.ournight.api_server.domain.order.service

import com.ournight.api_server.domain.order.model.IssueExecutedForStatEvent
import com.ournight.api_server.domain.order.model.MenuSalesSummary
import com.ournight.api_server.domain.order.model.OrderConfirmedForStatEvent
import com.ournight.api_server.domain.order.model.StoreSalesSummary
import com.ournight.api_server.domain.order.repository.MenuSalesSummaryRepository
import com.ournight.api_server.domain.order.repository.StoreSalesSummaryRepository
import com.ournight.api_server.domain.store.dao.StoreMenuRepository
import com.ournight.api_server.domain.store.repository.StoreTimeSlotRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDateTime

@Service
@Transactional
class SalesSummaryUpdateService(
    private val salesRepository: StoreSalesSummaryRepository,
    private val menuSalesSummaryRepository: MenuSalesSummaryRepository,
    private val slotRepository: StoreTimeSlotRepository,
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun updateOrderStat(event: OrderConfirmedForStatEvent) {
        val slot = slotRepository.findById(event.timeSlotId).orElseThrow()
        val quantum = calculateQuantum(slot.startTime, event.orderedAt)
        val baseDate = slot.startTime.toLocalDate()

        val summary =
            salesRepository.findByStoreIdAndTimeSlotIdAndTimeQuantum(
                event.storeId,
                event.timeSlotId,
                quantum
            ) ?: StoreSalesSummary(
                storeId = event.storeId,
                timeSlotId = event.timeSlotId,
                baseDate = baseDate,
                timeQuantum = quantum
            )

        summary.addOrder(event.totalAmount)
        salesRepository.save(summary)

        event.items.forEach { item ->
            val menuSummary =
                menuSalesSummaryRepository.findByStoreIdAndTimeSlotIdAndStoreMenuId(
                    event.storeId,
                    event.timeSlotId,
                    item.menuId
                ) ?: MenuSalesSummary(
                    storeId = event.storeId,
                    timeSlotId = event.timeSlotId,
                    storeMenuId = item.menuId,
                    menuName = item.menuName,
                    baseDate = baseDate
                )

            menuSummary.addSale(
                quantity = item.quantity,
                amount = item.price.multiply(item.quantity.toBigDecimal())
            )
            menuSalesSummaryRepository.save(menuSummary)
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun updateRefundStat(event: IssueExecutedForStatEvent) {
        val summary =
            salesRepository.findByStoreIdAndTimeSlotIdAndTimeQuantum(
                event.storeId,
                event.timeSlotId,
                event.timeQuantum
            ) ?: return

        summary.addRefund(event.refundAmount)
        salesRepository.save(summary)

        if (event.menuId != null && event.refundQuantity > 0) {
            val menuSummary =
                menuSalesSummaryRepository.findByStoreIdAndTimeSlotIdAndStoreMenuId(
                    event.storeId,
                    event.timeSlotId,
                    event.menuId,
                ) ?: return

            menuSummary.addRefund(
                quantity = event.refundQuantity,
                amount = event.refundAmount
            )
            menuSalesSummaryRepository.save(menuSummary)
        }
    }

    private fun calculateQuantum(start: LocalDateTime, target: LocalDateTime): Int {
        return (Duration.between(start, target).toMinutes() / 30).toInt().coerceAtLeast(0)
    }
}