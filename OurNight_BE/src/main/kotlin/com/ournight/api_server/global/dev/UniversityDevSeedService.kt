package com.ournight.api_server.global.dev

import com.ournight.api_server.domain.university.model.University
import com.ournight.api_server.domain.university.repository.UniversityRepository
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

data class UniversitySeedStats(
    val universitiesByName: Map<String, University>,
    val createdCount: Int,
    val reusedCount: Int,
)

@Service
class UniversityDevSeedService(
    private val universityRepository: UniversityRepository
) {
    private val log = LoggerFactory.getLogger(UniversityDevSeedService::class.java)

    @Transactional
    fun seed(): UniversitySeedStats {
        val specs = listOf(
            University(name = "한양대학교", latitude = 37.55383, longitude = 127.04000),
            University(name = "이화여자대학교", latitude = 37.55712, longitude = 126.94143),
            University(name = "세종대학교", latitude = 37.55080, longitude = 127.07423),
            University(name = "홍익대학교", latitude = 37.55038, longitude = 126.92577),
        )

        var createdCount = 0
        var reusedCount = 0
        val byName = linkedMapOf<String, University>()

        specs.forEach { spec ->
            val existing = universityRepository.findByName(spec.name)
            if (existing == null) {
                val created = universityRepository.save(spec)
                byName[created.name] = created
                createdCount++
            } else {
                existing.latitude = spec.latitude
                existing.longitude = spec.longitude
                val updated = universityRepository.save(existing)
                byName[updated.name] = updated
                reusedCount++
            }
        }

        log.info(
            "[UniversityDevSeedService] seed completed. created={}, reused={}",
            createdCount,
            reusedCount,
        )

        return UniversitySeedStats(
            universitiesByName = byName,
            createdCount = createdCount,
            reusedCount = reusedCount,
        )
    }
}
