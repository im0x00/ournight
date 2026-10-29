package com.ournight.api_server.domain.venue.repository

import com.ournight.api_server.domain.venue.model.Venue
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface VenueRepository : JpaRepository<Venue, UUID> {
    @EntityGraph(attributePaths = ["university"])
    override fun findAll(): List<Venue>

    @EntityGraph(attributePaths = ["university"])
    override fun findById(id: UUID): Optional<Venue>

    // 제휴된 가게들만 조회 (일일호프 장소 목록용)
    @EntityGraph(attributePaths = ["university"])
    fun findAllByIsPartnerTrue(): List<Venue>

    // 이름으로 검색
    fun findByNameContaining(name: String): List<Venue>

    fun findByNameAndAddress(name: String, address: String): Venue?
}
