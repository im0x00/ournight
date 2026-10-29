package com.ournight.api_server.domain.order.model

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(
    name = "menu_sales_summaries",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_menu_sales_slot",
            columnNames = ["store_id", "time_slot_id", "store_menu_id"]
        )
    ]
)
class MenuSalesSummary(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(name = "store_id", nullable = false, columnDefinition = "UUID")
    val storeId: UUID,

    @Column(name = "time_slot_id", nullable = false, columnDefinition = "UUID")
    val timeSlotId: UUID,

    @Column(name = "store_menu_id", nullable = false, columnDefinition = "UUID")
    val storeMenuId: UUID,

    @Column(name = "menu_name", nullable = false)
    val menuName: String,

    @Column(name = "base_date", nullable = false)
    val baseDate: LocalDate,

    @Column(name = "total_quantity", nullable = false)
    var totalQuantity: Int = 0,

    @Column(name = "total_amount", nullable = false)
    var totalAmount: BigDecimal = BigDecimal.ZERO,

    @Version
    var version: Long = 0L
) {
    fun addSale(quantity: Int, amount: BigDecimal) {
        totalQuantity += quantity
        totalAmount = totalAmount.add(amount)
    }

    fun addRefund(quantity: Int, amount: BigDecimal) {
        totalQuantity -= quantity
        totalAmount = totalAmount.subtract(amount)
    }
}