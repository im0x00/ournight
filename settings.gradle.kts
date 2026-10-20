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

// Benchmark modules (notification-reactive, notification-imperative) are a separate Gradle build
// in notification-benchmark/. Run Gradle for them from that directory.
