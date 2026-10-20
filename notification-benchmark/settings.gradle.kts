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
include("notification-reactive")
project(":notification-reactive").projectDir = file("../notification-reactive")

include("notification-imperative")
project(":notification-imperative").projectDir = file("../notification-imperative")
