package com.ournight.api_server.api.pos.dto

import java.math.BigDecimal
import java.util.UUID

data class DailyReportResponse(
    val graph: List<SalesGraphPointDto>,
    val summary: SalesSummaryDto,
    val menuRankings: List<MenuRankingDto>
)

data class SalesGraphPointDto(
    val timeQuantum: Int,
    val salesAmount: BigDecimal,
    val netSalesAmount: BigDecimal
)

data class SalesSummaryDto(
    val totalSalesAmount: BigDecimal,
    val netSalesAmount: BigDecimal,
    val totalRefundAmount: BigDecimal,
    val totalOrderCount: Long
)

data class MenuRankingDto(
    val menuId: UUID,
    val menuName: String,
    val totalQuantity: Int,
    val totalAmount: BigDecimal
)