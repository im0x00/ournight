package com.ournight.api_server.domain.university.service

import com.ournight.api_server.api.host.dto.UniversityCreateRequest
import com.ournight.api_server.api.host.dto.UniversityUpdateRequest
import com.ournight.api_server.domain.university.model.University
import com.ournight.api_server.domain.university.repository.UniversityRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class UniversityService(
    private val universityRepository: UniversityRepository
) {
    fun getUniversity(universityId: UUID): University {
        return universityRepository.findById(universityId)
            .orElseThrow { IllegalArgumentException("존재하지 않는 대학입니다. ID: $universityId") }
    }

    fun getAllUniversities(): List<University> {
        return universityRepository.findAll()
    }

    @Transactional
    fun createUniversity(request: UniversityCreateRequest): University {
        val university = University(
            name = request.name,
            latitude = request.latitude,
            longitude = request.longitude
        )
        return universityRepository.save(university)
    }

    @Transactional
    fun updateUniversity(universityId: UUID, request: UniversityUpdateRequest): University {
        val university = universityRepository.findById(universityId)
            .orElseThrow { IllegalArgumentException("수정하려는 대학이 존재하지 않습니다. ID: $universityId") }

        request.name?.let { university.name = it }
        request.latitude?.let { university.latitude = it }
        request.longitude?.let { university.longitude = it }

        return university
    }

    @Transactional
    fun deleteUniversity(universityId: UUID) {
        if (!universityRepository.existsById(universityId)) {
            throw IllegalArgumentException("삭제하려는 대학이 존재하지 않습니다.")
        }
        universityRepository.deleteById(universityId)
    }
}
