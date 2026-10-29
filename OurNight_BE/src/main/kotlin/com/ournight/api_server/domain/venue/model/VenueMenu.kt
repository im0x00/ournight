package com.ournight.api_server.domain.venue.model

import com.ournight.api_server.domain.store.domain.Store
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal
import java.util.UUID

@Entity
@Table(name = "venue_menus")
class VenueMenu (
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "UUID")
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id", nullable = false, columnDefinition = "UUID")
    val venue: Venue,

    @Column(name = "original_name", nullable = false, columnDefinition = "varchar(128) NOT NULL")
    val name: String,

    @Column(name = "price", nullable = false)
    val price: BigDecimal,

    @Column(name = "image_url", nullable = true)
    val imageUrl: String?,

)