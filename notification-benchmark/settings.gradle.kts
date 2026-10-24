pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "notification-benchmark"

// Module directories stay where they are (repo root) so Dockerfile and compose paths are unchanged.
// A · C: Spring MVC + virtual threads
include("notification-mvc-vt")
project(":notification-mvc-vt").projectDir = file("../notification-mvc-vt")

// old WebFlux + Reactor (reference row only)
include("notification-webflux-reactor")
project(":notification-webflux-reactor").projectDir = file("../notification-webflux-reactor")

// F: WebFlux + coroutine, owner hub
include("notification-webflux-coroutine")
project(":notification-webflux-coroutine").projectDir = file("../notification-webflux-coroutine")
