package com.ournight.api_server.domain.venue.model

import com.ournight.api_server.domain.university.model.University
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "venues")
class Venue (
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "UUID")
    val id: UUID? = null,

    @Column(name = "latitude", nullable = false)
    var latitude: Double = 0.0,

    @Column(name = "longitude", nullable = false)
    var longitude: Double = 0.0,

    @Column(nullable = false, length = 200)
    var address: String = "",

    @Column(nullable = false, length = 100)
    var name: String,

    // 일일호프용 제휴 가게면 true, 축제용 임시 장소면 false
    @Column(name = "is_partner", nullable = false)
    var isPartner: Boolean = false,

    @Column(name = "detail_location", length = 200)
    var detailLocation: String? = null, // 예: "부스 번호 10~12번"

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "university_id")
    var university: University? = null,
)
