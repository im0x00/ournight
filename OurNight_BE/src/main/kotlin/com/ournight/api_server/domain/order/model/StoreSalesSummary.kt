package com.ournight.api_server.domain.order.model

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID


@Entity
@Table(
    name = "store_sales_summaries",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_store_sales_slot_quantum",
            columnNames = ["store_id", "time_slot_id", "time_quantum"]
        )
    ]
)
class StoreSalesSummary(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(name = "store_id", nullable = false, columnDefinition = "UUID")
    val storeId: UUID,

    @Column(name = "time_slot_id", nullable = false, columnDefinition = "UUID")
    val timeSlotId: UUID,

    @Column(name = "base_date", nullable = false)
    val baseDate: LocalDate,

    @Column(name = "time_quantum", nullable = false)
    val timeQuantum: Int,

    @Column(name = "total_sales_amount", nullable = false)
    var totalSalesAmount: BigDecimal = BigDecimal.ZERO,

    @Column(name = "refund_amount", nullable = false)
    var refundAmount: BigDecimal = BigDecimal.ZERO,

    @Column(name = "order_count", nullable = false)
    var orderCount: Long = 0,

    @Version
    var version: Long = 0L
) {
    val netSalesAmount: BigDecimal
        get() = totalSalesAmount.subtract(refundAmount)

    fun addOrder(amount: BigDecimal) {
        totalSalesAmount = totalSalesAmount.add(amount)
        orderCount += 1
    }

    fun addRefund(amount: BigDecimal) {
        refundAmount = refundAmount.add(amount)
    }
}