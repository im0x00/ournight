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

rootProject.name = "OurNight"

include("OurNight_BE")

// Benchmark modules (notification-mvc-vt, notification-webflux-reactor, notification-webflux-coroutine) are a separate Gradle build
// in notification-benchmark/. Run Gradle for them from that directory.
