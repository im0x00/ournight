package com.ournight.api_server.domain.university.repository

import com.ournight.api_server.domain.university.model.University
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UniversityRepository : JpaRepository<University, UUID>
{
    fun findByName(name: String): University?
}
