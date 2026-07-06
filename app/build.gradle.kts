@file:Suppress("UnstableApiUsage")

import com.android.build.gradle.internal.lint.AndroidLintTask
import org.gradle.api.GradleException
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
        minSdk = 37
        targetSdk = 37
        versionCode = 275
        versionName = "0.4.69"
    }

    signingConfigs {
        create("release") {
            val keystoreFile = file("../release-keystore.jks")
            val passwordFile = rootProject.file(".password-signing-keys")

            if (keystoreFile.exists() && passwordFile.exists()) {
                // Production signing: real keystore + password from local secrets.
                storeFile = keystoreFile
                storePassword = passwordFile.readText().trim()
                keyAlias = "dustvalve"
                keyPassword = storePassword
            } else {
                // Fallback to AGP's debug signing when the real keystore / password
                // is not present. This keeps :app:assembleRelease unblocked in two
                // scenarios that need a release-variant build without secrets:
                //   - the baseline-profile regeneration workflow on every push/PR
                //     (the macrobenchmark only needs an installable APK; the
                //     deliverable is baseline-prof.txt, not the APK itself)
                //   - local devs who haven't generated a keystore yet
                // The resulting APK is debug-signed and NOT shippable. Production
                // release builds decode release-keystore.jks from KEYSTORE_BASE64
                // via .github/workflows/release.yml.
                rootProject.logger.warn(
                    "release-keystore.jks or .password-signing-keys missing — " +
                        "falling back to AGP debug signing for the release variant.",
                )
                val debug = signingConfigs.getByName("debug")
                val debugStoreFile = debug.storeFile
                // AGP only auto-creates the debug keystore when assembleDebug
                // actually runs (lazy materialization on first debug signing).
                // CI flows that build release without ever touching the debug
                // variant find nothing here and validateSigningRelease aborts
                // with "Keystore file not found". Generate the debug keystore
                // ourselves at the exact path AGP expects. Idempotent: only
                // runs when the file is genuinely missing.
                if (debugStoreFile != null && !debugStoreFile.exists()) {
                    debugStoreFile.parentFile.mkdirs()
                    val process = ProcessBuilder(
                        System.getProperty("java.home") + "/bin/keytool",
                        "-genkey", "-noprompt",
                        "-keystore", debugStoreFile.absolutePath,
                        "-alias", debug.keyAlias!!,
                        "-keyalg", "RSA", "-keysize", "2048",
                        "-validity", "10000",
                        "-dname", "CN=Android Debug,O=Android,C=US",
                        "-storepass", debug.storePassword!!,
                        "-keypass", debug.keyPassword!!,
                    ).redirectErrorStream(true).start()
                    val output = process.inputStream.bufferedReader().readText()
                    val exitCode = process.waitFor()
                    if (exitCode != 0) {
                        throw GradleException(
                            "Failed to materialize debug keystore at " +
                                "${debugStoreFile.absolutePath}: keytool exited $exitCode\n$output",
                        )
                    }
                }
                storeFile = debug.storeFile
                storePassword = debug.storePassword
                keyAlias = debug.keyAlias
                keyPassword = debug.keyPassword
            }
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

        // Strong Skipping Mode is on by default in Kotlin Compose Compiler 2.3.21+
        // (the featureFlags.add(StrongSkipping) form was deprecated in 2.3.21 and
        // removed in subsequent versions). Audit reports produced via
        // `-Pcompose.reports=true` to confirm.
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

    // Hilt 2.60's KSP processor emits @CanIgnoreReturnValue on generated
    // component methods but does NOT declare errorprone-annotations as a
    // transitive runtime dep — without this the hiltJavaCompile* step fails
    // with "cannot find symbol class CanIgnoreReturnValue".
    implementation(libs.errorprone.annotations)

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

    // Profile installer — consumed by androidx.baselineprofile to install
    // baseline-prof.txt + startup-prof.txt shipped in the release APK.
    // (Profile files are produced by the :baselineprofile module's
    // `pixel6Api33` managed-device run and copied to
    // app/src/release/baseline-prof.txt + startup-prof.txt.)
    implementation(libs.androidx.profileinstaller)

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
// reported and the baseline looks 100% stale to this task — printing
// "N entries not found" on every CI build alongside the warnings we
// actually want to suppress. AGP 9.x seals the baseline property via
// disallowChanges() inside the task initialization action, so we can't
// null it out at the task level. The pragmatic fix is to keep
// lint-baseline.xml as an EMPTY baseline (see app/lint-baseline.xml):
// nothing for the fatal-only lint to consider stale, so no "N entries not
// found" diagnostic. lintRelease (run separately via ./build.sh) still
// runs full lint and reports any new warnings, but warnings don't fail
// the build (warningsAsErrors = false).
