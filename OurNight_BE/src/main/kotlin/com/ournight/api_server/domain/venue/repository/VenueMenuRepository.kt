package com.ournight.api_server.domain.venue.repository

import com.ournight.api_server.domain.venue.model.VenueMenu
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface VenueMenuRepository : JpaRepository<VenueMenu, UUID> {
    // 특정 Venue의 원본 메뉴판 전체 조회
    fun findAllByVenueId(venueId: UUID): List<VenueMenu>
}