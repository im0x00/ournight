package com.ournight.api_server.global.dev

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@Profile("local")
class PosDevSeedRunnerConfig {

    private val log = LoggerFactory.getLogger(PosDevSeedRunnerConfig::class.java)

    @Bean
    fun posDevSeedRunner(
        baseDevSeedService: BaseDevSeedService,
        @Value("\${app.dev.seed.enabled:true}") enabled: Boolean
    ): CommandLineRunner {
        return CommandLineRunner {
            if (!enabled) {
                log.info("[PosDevSeedRunner] seed disabled")
                return@CommandLineRunner
            }

            val summary = baseDevSeedService.seed()
            log.info(
                "[PosDevSeedRunner] base seed completed. created={}, reused={}",
                summary.createdCount,
                summary.reusedCount,
            )
        }
    }
}
