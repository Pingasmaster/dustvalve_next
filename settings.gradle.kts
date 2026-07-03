pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Auto-provision a matching JDK when the daemon/toolchain needs one.
    // 9.6.0 enforces a toolchain resolver; without this, fresh CI machines
    // and dev laptops fail to locate the right JDK.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// Adopt Gradle 10-style lookup semantics now. Forces explicit references to
// rootProject / parent and removes silent findProperty/property/hasProperty
// coupling that historically tripped our convention plugins.
enableFeaturePreview("NO_IMPLICIT_LOOKUP_IN_PARENT_PROJECTS")

// Stable config cache preview (Gradle 9.x promoted; we still toggle via
// org.gradle.configuration-cache in gradle.properties when safe).
enableFeaturePreview("STABLE_CONFIGURATION_CACHE")

rootProject.name = "DustvalveNext"
include(":app")
include(":baselineprofile")
include(":macrobenchmark")
