package com.ournight.api_server.api.pos.controller

import com.ournight.api_server.api.pos.dto.DailyReportResponse
import com.ournight.api_server.api.pos.dto.MenuRankingDto
import com.ournight.api_server.api.pos.dto.SalesGraphPointDto
import com.ournight.api_server.api.pos.dto.SalesSummaryDto
import com.ournight.api_server.domain.order.repository.MenuSalesSummaryRepository
import com.ournight.api_server.domain.order.repository.StoreSalesSummaryRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.util.UUID

@RestController
@RequestMapping("/api/pos/{storeId}/reports")
class PosSalesReportController(
    private val salesRepository: StoreSalesSummaryRepository,
    private val menuRepository: MenuSalesSummaryRepository
) {
    @GetMapping("/daily")
    fun getDailyReport(
        @PathVariable storeId: UUID,
        @RequestParam slotId: UUID,
    ): DailyReportResponse {

        val summaries =
            salesRepository.findAllByStoreIdAndTimeSlotIdOrderByTimeQuantumAsc(storeId, slotId)


        var totalSales = BigDecimal.ZERO
        var totalRefund = BigDecimal.ZERO
        var totalOrders = 0L

        val graphData = summaries.map { summary ->
            totalSales += summary.totalSalesAmount
            totalRefund += summary.refundAmount
            totalOrders += summary.orderCount

            SalesGraphPointDto(
                timeQuantum = summary.timeQuantum,
                salesAmount = summary.totalSalesAmount,
                netSalesAmount = summary.netSalesAmount
            )
        }

        // 2. 메뉴 매출 순위 (금액 높은 순 Top 10)
        val menuRankings = menuRepository.findTop10ByStoreIdAndTimeSlotIdOrderByTotalAmountDesc(storeId, slotId)
            .map {
                MenuRankingDto(
                    menuId = it.storeMenuId,
                    menuName = it.menuName,
                    totalQuantity = it.totalQuantity,
                    totalAmount = it.totalAmount
                )
            }

        return DailyReportResponse(
            graph = graphData,
            summary = SalesSummaryDto(
                totalSalesAmount = totalSales,
                netSalesAmount = totalSales.subtract(totalRefund),
                totalRefundAmount = totalRefund,
                totalOrderCount = totalOrders
            ),
            menuRankings = menuRankings
        )
    }
}
