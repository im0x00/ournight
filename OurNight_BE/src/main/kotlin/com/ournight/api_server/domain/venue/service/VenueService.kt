package com.ournight.api_server.domain.venue.service

import com.ournight.api_server.api.host.dto.VenueCreateRequest
import com.ournight.api_server.api.host.dto.VenueUpdateRequest
import com.ournight.api_server.domain.university.service.UniversityService
import com.ournight.api_server.domain.venue.model.Venue
import com.ournight.api_server.domain.venue.repository.VenueRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class VenueService(
    private val venueRepository: VenueRepository,
    private val universityService: UniversityService,
) {
    fun getVenue(venueId: UUID): Venue {
        return venueRepository.findById(venueId)
            .orElseThrow { IllegalArgumentException("존재하지 않는 장소입니다. ID: $venueId") }
    }

    fun getAllPartnerVenues(): List<Venue> {
        return venueRepository.findAllByIsPartnerTrue()
    }

    fun getAllVenues(): List<Venue> {
        return venueRepository.findAll()
    }

    // [FESTIVAL용] 임시 장소 생성: 축제 주점 생성 시, 지도 좌표를 찍기 위해 생성
    @Transactional
    fun createTemporaryVenue(request: VenueCreateRequest): Venue {
        val temporaryVenue = Venue(
            name = request.name,
            address = request.address,
            latitude = request.latitude,
            longitude = request.longitude,
            detailLocation = request.detailLocation, // "부스 1~3번" 등 상세 설명
            isPartner = false, // 중요: 임시 장소임을 명시
            university = request.universityId?.let { universityService.getUniversity(it) },
        )
        return venueRepository.save(temporaryVenue)
    }

    @Transactional
    fun updateTemporaryVenue(venue: Venue, request: VenueCreateRequest): Venue {
        venue.latitude = request.latitude
        venue.longitude = request.longitude
        venue.address = request.address
        venue.detailLocation = request.detailLocation
        venue.isPartner = false
        if (request.universityId != null) {
            venue.university = universityService.getUniversity(request.universityId)
        }
        return venue
    }

    // 관리자가 DB에 미리 등록해두는 실제 술집 데이터
    @Transactional
    fun createPartnerVenue(request: VenueCreateRequest): Venue {
        val partnerVenue = Venue(
            name = request.name,
            address = request.address,
            latitude = request.latitude,
            longitude = request.longitude,
            detailLocation = request.detailLocation,
            isPartner = true, // 중요: 실제 제휴 가게
            university = request.universityId?.let { universityService.getUniversity(it) },
        )
        return venueRepository.save(partnerVenue)
    }

    @Transactional
    fun createVenue(request: VenueCreateRequest): Venue {
        val venue = Venue(
            name = request.name,
            address = request.address,
            latitude = request.latitude,
            longitude = request.longitude,
            detailLocation = request.detailLocation,
            isPartner = request.isPartner,
            university = request.universityId?.let { universityService.getUniversity(it) },
        )
        return venueRepository.save(venue)
    }

    @Transactional
    fun updateVenue(venueId: UUID, request: VenueUpdateRequest): Venue {
        val venue = venueRepository.findById(venueId)
            .orElseThrow { IllegalArgumentException("수정하려는 장소가 존재하지 않습니다. ID: $venueId") }

        request.name?.let { venue.name = it }
        request.address?.let { venue.address = it }
        request.latitude?.let { venue.latitude = it }
        request.longitude?.let { venue.longitude = it }
        request.detailLocation?.let { venue.detailLocation = it }
        request.isPartner?.let { venue.isPartner = it }
        if (request.isUniversityIdProvided) {
            venue.university = request.universityId?.let { universityService.getUniversity(it) }
        }

        return venue
    }

    @Transactional
    fun deleteVenue(venueId: UUID) {
        if (!venueRepository.existsById(venueId)) {
            throw IllegalArgumentException("삭제하려는 장소가 존재하지 않습니다.")
        }
        // TODO: 실제 운영 시에는 해당 Venue를 참조하는 Store가 있는지 확인하는 로직이 필요
        venueRepository.deleteById(venueId)
    }
}
