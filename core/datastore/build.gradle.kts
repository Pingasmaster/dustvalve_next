plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.dustvalve.next.core.datastore"
    compileSdk = 37
    // Compile against the 37.1 minor SDK release (API additions only; minor
    // SDKs carry no behavior changes and cannot be targeted - targetSdk
    // stays at the 37 major).
    compileSdkMinor = 1
    defaultConfig { minSdk = 26 }

    lint {
        // Off-by-default check; must be enabled here so :app (checkDependencies
        // = true) can enforce it at error severity across module boundaries.
        enable += "StopShip"
        enable += "TypographyQuotes"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_26
        targetCompatibility = JavaVersion.VERSION_26
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_26)
        }
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
    baseline = rootProject.file("config/detekt/detekt-baseline.xml")
    parallel = true
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    api(libs.datastore.preferences)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.errorprone.annotations)

    // Shared config/detekt/detekt.yml carries a Compose ruleset section;
    // detekt needs the ruleset provider on its classpath in every module.
    detektPlugins(libs.detekt.compose)
}
