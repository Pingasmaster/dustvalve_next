plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

android {
    namespace = "com.dustvalve.next.core.common"
    compileSdk = 37
    defaultConfig { minSdk = 37 }

    lint {
        // Off-by-default check; must be enabled here so :app (checkDependencies
        // = true) can enforce it at error severity across module boundaries.
        enable += "StopShip"
        enable += "TypographyQuotes"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
        }
    }

    buildFeatures { compose = true }
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
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui) // stringResource/pluralStringResource in UiText
    implementation(libs.core.ktx)
    // jakarta.inject.Qualifier for the Dispatcher qualifier annotations.
    api(libs.hilt.android)

    // Shared config/detekt/detekt.yml carries a Compose ruleset section;
    // detekt needs the ruleset provider on its classpath in every module.
    detektPlugins(libs.detekt.compose)
}
