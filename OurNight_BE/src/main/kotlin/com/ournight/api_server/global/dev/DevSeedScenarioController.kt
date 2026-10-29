package com.ournight.api_server.global.dev

import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Profile("local")
@RequestMapping("/__dev/seeds/scenarios")
class DevSeedScenarioController(
    private val devScenarioSeedService: DevScenarioSeedService,
) {
    @PostMapping("/store-discovery")
    fun seedStoreDiscoveryScenario(): DevScenarioSeedSummary {
        return devScenarioSeedService.seedStoreDiscoveryScenario()
    }

    @PostMapping("/pos")
    fun seedPosScenario(): DevScenarioSeedSummary {
        return devScenarioSeedService.seedPosScenario()
    }
}
