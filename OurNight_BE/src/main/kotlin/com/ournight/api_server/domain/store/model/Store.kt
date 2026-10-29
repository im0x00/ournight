package com.ournight.api_server.domain.store.domain

import com.fasterxml.jackson.annotation.JsonCreator
import com.ournight.api_server.domain.store.model.StoreTimeSlot
import com.ournight.api_server.domain.user.model.User
import com.ournight.api_server.domain.venue.model.Venue
import jakarta.persistence.*
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import org.hibernate.annotations.CreationTimestamp
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

enum class StoreType(val value: String) {
	FESTIVAL("축제주점"),
	RENTAL("일일호프");

	companion object {
		@JvmStatic
		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		fun from(value: String): StoreType {
			return values().find { it.value == value || it.name == value }
				?: throw IllegalArgumentException("Invalid StoreType: $value")
		}
	}
}

@Converter(autoApply = true)
class StoreCategoryConverter : AttributeConverter<StoreType, String> {
	override fun convertToDatabaseColumn(attribute: StoreType?): String? {
		return attribute?.value
	}

	override fun convertToEntityAttribute(dbData: String?): StoreType? {
		return dbData?.let {
			StoreType.values().find { category -> category.value == it }
		}
	}
}


@Entity
@Table(name = "stores")
class Store(
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", columnDefinition = "UUID")
	val id: UUID? = null,

	@Column(nullable = false, length = 100)
	var name: String,

	@Column(nullable = false, length = 1000)
	var description: String = "",

	@Column(name = "host_contact", nullable = false, length = 50)
	var hostContact: String = "",

	@Convert(converter = StoreCategoryConverter::class)
	@Column(name = "type", nullable = false, length = 20)
	var type: StoreType,

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false, columnDefinition = "UUID")
	var host: User,

	@ManyToOne(fetch = FetchType.LAZY, cascade = [CascadeType.MERGE])
	@JoinColumn(name = "venue_id", nullable = true)
	var venue: Venue? = null,

	@Column(name = "bank_name", length = 50)
	var bankName: String = "",

	@Column(name = "account_number", length = 50)
	var accountNumber: String = "",

	/** 입장료(원). 미설정 시 null */
	@Column(name = "entrance_fee", precision = 12, scale = 0)
	var entranceFee: BigDecimal? = null,

	@Column(name = "is_active", nullable = false)
	var isActive: Boolean = true,

	@Column(name = "time_limit", nullable = false)
	var timeLimit: Int = 120,

	@OneToMany(mappedBy = "store", cascade = [CascadeType.ALL], orphanRemoval = true)
	var timeSlots: MutableList<StoreTimeSlot> = mutableListOf(),

	@ElementCollection
	@CollectionTable(name = "store_images", joinColumns = [JoinColumn(name = "store_id")])
	@Column(name = "image_url", columnDefinition = "TEXT")
	var mainImageUrls: MutableList<String> = mutableListOf(),

	@ElementCollection
	@CollectionTable(name = "store_notices", joinColumns = [JoinColumn(name= "store_id")])
	@Column(name= "content")
	var notices: MutableList<String> = mutableListOf(),

	@Column(name = "created_at", nullable = true, updatable = false)
	@CreationTimestamp
	var createdAt: LocalDateTime? = null,

//	@Column(name = "use_pos", nullable = false)
//	var usePos: Boolean = false,

)
