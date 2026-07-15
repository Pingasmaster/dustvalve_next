plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

android {
    namespace = "com.dustvalve.next.core.database"
    compileSdk = 37
    defaultConfig { minSdk = 37 }

    lint {
        // Off-by-default check; must be enabled here so :app (checkDependencies
        // = true) can enforce it at error severity across module boundaries.
        enable += "StopShip"
        enable += "TypographyQuotes"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }
}

ktlint {
    version.set(libs.versions.ktlint.engine.get())
    android.set(true)
    ignoreFailures.set(false)
    filter { exclude { it.file.path.contains("/build/") } }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
    baseline = rootProject.file("config/detekt/detekt-baseline.xml")
    parallel = true
}

dependencies {
    // RoomDatabase/Dao types appear in this module's public API surface.
    api(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.coroutines.android)

    // Shared config/detekt/detekt.yml carries a Compose ruleset section;
    // detekt needs the ruleset provider on its classpath in every module.
    detektPlugins(libs.detekt.compose)
}
