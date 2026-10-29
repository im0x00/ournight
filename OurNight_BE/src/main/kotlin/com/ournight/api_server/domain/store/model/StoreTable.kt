package com.ournight.api_server.domain.store.domain

import com.ournight.api_server.domain.store.model.StoreTableStatus
import com.ournight.api_server.domain.store.model.StoreTableZone
import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(
	name = "store_tables",
	uniqueConstraints = [
		UniqueConstraint(
			name = "uk_store_table_number",
			columnNames = ["store_id", "table_number"]
		)
	]
)
class StoreTable(
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", columnDefinition = "UUID")
	val id: UUID? = null,

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "store_id", nullable = false, columnDefinition = "UUID")
	val store: Store,

	@Column(name = "table_number", nullable = false)
	var tableNumber: Int,

	@Column(name = "x_position", nullable = false)
	var xPosition: Double = 0.0,

	@Column(name = "y_position", nullable = false)
	var yPosition: Double = 0.0,

	@Column(nullable = false)
	var capacity: Int = 4,

	@Column(name = "qr_link", length = 500)
	var qrLink: String? = null,

	@Column(name = "qr_image_url", length = 500)
	var qrImageUrl: String? = null,

	@Enumerated(EnumType.STRING)
	var status: StoreTableStatus = StoreTableStatus.VACANT,

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "zone_id", columnDefinition = "UUID")
	var zone: StoreTableZone? = null,

	) {

	fun occupy() {
		this.status = StoreTableStatus.OCCUPIED
	}

	fun clear() {
		this.status = StoreTableStatus.VACANT
	}

}

