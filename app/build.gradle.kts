@file:Suppress("UnstableApiUsage")

import com.android.build.gradle.internal.lint.AndroidLintTask
import org.gradle.api.file.RegularFile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

android {
    namespace = "com.dustvalve.next.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.dustvalve.next.android"
        minSdk = 36
        targetSdk = 37
        versionCode = 264
        versionName = "0.4.58"
    }

    signingConfigs {
        create("release") {
            val passwordFile = rootProject.file(".password-signing-keys")
            val signingPassword = if (passwordFile.exists()) {
                passwordFile.readText().trim()
            } else {
                ""
            }
            storeFile = file("../release-keystore.jks")
            storePassword = signingPassword
            keyAlias = "dustvalve"
            keyPassword = signingPassword
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
        }
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

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeCompiler {
        // Treat kotlin.collections.{List,Set,Map,Collection} as stable so
        // composables that take them as params can skip on equal-reference
        // recompositions. The codebase consistently emits these from read-only
        // Flows (Room, JSON parsing) and never mutates them after emission.
        stabilityConfigurationFiles.add(
            rootProject.layout.projectDirectory.file("compose_stability_config.conf"),
        )
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }

    // AGP can't run NDK strip on these AAR-supplied .so files (no NDK installed
    // in this build env). Opt them out explicitly so AGP doesn't print a noisy
    // "Unable to strip" notice on every build.
    packaging {
        jniLibs {
            keepDebugSymbols += setOf(
                "**/libandroidx.graphics.path.so",
                "**/libdatastore_shared_counter.so",
            )
        }
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
        checkDependencies = true
        checkReleaseBuilds = true
        explainIssues = true
        showAll = true
        baseline = file("lint-baseline.xml")
        lintConfig = rootProject.file("config/lint/lint.xml")
    }
}

ktlint {
    version.set(libs.versions.ktlint.engine.get())
    android.set(true)
    ignoreFailures.set(false)
    filter {
        exclude { it.file.path.contains("/build/") }
    }
    reporters {
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.SARIF)
    }
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
    baseline = rootProject.file("config/detekt/detekt-baseline.xml")
    parallel = true
}

dependencies {
    // Compose BOM
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)

    // Material 3 Expressive + compatible compose libs
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.material3)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)

    // Graphics Shapes (for MaterialShapes)
    implementation(libs.graphics.shapes)

    // Lifecycle
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    // Activity
    implementation(libs.activity.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.lifecycle.viewmodel.compose)

    // WorkManager + Hilt Work
    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // DataStore
    implementation(libs.datastore.preferences)

    // DocumentFile (SAF directory/file creation for export)
    implementation(libs.documentfile)

    // OkHttp
    implementation(libs.okhttp)
    implementation(libs.okhttp.brotli)

    // Jsoup
    implementation(libs.jsoup)

    // Coil 3
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.network.cache.control)

    // Palette (for extracting dominant colors from album art)
    implementation(libs.palette.ktx)

    // MaterialKolor (generate M3 ColorScheme from seed color)
    implementation(libs.material.kolor)

    // Media3
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.datasource.okhttp)

    // Kotlinx Serialization
    implementation(libs.kotlinx.serialization.json)

    // Coroutines
    implementation(libs.coroutines.android)

    // Immutable collections (stable Compose params for List/Set types)
    implementation(libs.collections.immutable)

    // Reorderable lists (drag & drop for LazyColumn / LazyRow)
    implementation(libs.reorderable)

    // Core KTX
    implementation(libs.core.ktx)

    // --- Unit test dependencies ---
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.truth)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.room.testing)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.okhttp.tls)
    testImplementation(libs.turbine)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)

    // Static analysis plugins / lint checks
    detektPlugins(libs.detekt.compose)
    lintChecks(libs.lint.slack.checks)
    lintChecks(libs.lint.slack.compose)
}

// lintVital* runs with --fatalOnly during assembleRelease, so the
// RawDispatchersUse error-severity entries in lint-baseline.xml are never
// reported and the baseline looks 100% stale to this task — printing both
// "N entries not found" and "creation variant ... different" warnings on
// every CI build. Clearing the baseline for lintVital* only silences both,
// while lintRelease (used by ./build.sh) keeps the baseline and continues
// to enforce the ratchet documented in config/lint/lint.xml.
tasks.withType<AndroidLintTask>()
    .matching { it.name.startsWith("lintVital") }
    .configureEach {
        projectInputs.lintOptions.baseline.set(null as RegularFile?)
        missingBaselineIsEmptyBaseline.set(true)
    }
