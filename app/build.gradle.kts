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
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.dustvalve.next.android"
    compileSdk = 37
    // Compile against the 37.1 minor SDK release (API additions only; minor
    // SDKs carry no behavior changes and cannot be targeted - targetSdk
    // stays at the 37 major).
    compileSdkMinor = 1

    defaultConfig {
        applicationId = "com.dustvalve.next.android"
        minSdk = 26
        targetSdk = 37
        versionCode = 279
        versionName = "0.5.1"
        versionNameSuffix = "-legacy"
        // Instrumentation (smoke + E2E) runs against the REAL app object
        // graph - no HiltTestApplication on device by design.
        testInstrumentationRunner = "com.dustvalve.next.android.testing.DustvalveTestRunner"
    }

    // Instrumentation normally tests the debug APK. -PtestReleaseBuild flips
    // it to the MINIFIED release APK (R8 full mode + resource shrinking) so
    // CI catches release-only breakage - the v0.5.x 'playback dead in prod
    // while every debug test is green' class of bug. Release falls back to
    // debug signing when the keystore is absent (see signingConfigs), so the
    // test APK (always debug-signed) can install alongside it in CI.
    testBuildType = if (project.hasProperty("testReleaseBuild")) "release" else "debug"

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
                // Only warn when this build actually produces a release
                // artifact; on test/lint-only invocations the fallback is
                // irrelevant and the message is just configuration noise.
                val wantsReleaseArtifact = gradle.startParameter.taskNames.any { name ->
                    val task = name.substringAfterLast(':')
                    task.contains("Release") &&
                        (task.startsWith("assemble") || task.startsWith("bundle") || task.startsWith("install"))
                }
                val fallbackMessage = "release-keystore.jks or .password-signing-keys missing - " +
                    "falling back to AGP debug signing for the release variant."
                if (wantsReleaseArtifact) {
                    rootProject.logger.warn(fallbackMessage)
                } else {
                    rootProject.logger.info(fallbackMessage)
                }
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
        debug {
            // en-XA (accented/expanded) and ar-XB (RTL) pseudolocales for
            // localizability testing; never enabled on release.
            isPseudoLocalesEnabled = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (project.hasProperty("testReleaseBuild")) {
                // Instrumentation compiles against unobfuscated names, so the
                // few app classes tests construct directly must keep theirs.
                // Applied ONLY for the release-test CI lane; the shipped APK
                // never carries these keeps.
                proguardFiles("proguard-test-support.pro")
            }
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
        isCoreLibraryDesugaringEnabled = true
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        // Ship exactly the locales the app is translated into (res/values-*);
        // this still strips the ~75 other locales of androidx/Material3/Media3
        // library translations from the universal APK. Keep in sync with the
        // values-* directories when adding a language.
        localeFilters += listOf("en", "de", "es", "fr", "it", "pt-rBR", "ja", "zh-rCN", "ru")
        // Auto-generate the Android 13+ LocaleConfig from the values-* dirs +
        // res/resources.properties so the app appears in
        // Settings > System > App languages (per-app language preference).
        generateLocaleConfig = true
    }

    // The dependency-metadata block is an encrypted blob only Google Play can
    // read; releases ship as plain APKs on GitHub, so it's dead weight (and
    // F-Droid rejects APKs that carry it).
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
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
        // Gradle Managed Device for the smoke + E2E instrumentation suites.
        // Legacy branch (minSdk 26): API 33 AOSP, same image the
        // baselineprofile module already uses.
        managedDevices {
            localDevices.register("pixel7aApi33") {
                device = "Pixel 7a"
                apiLevel = 33
                systemImageSource = "aosp"
                // Test the arm64 APK - the ABI every real device runs - via the
                // image's built-in translation layer. Matches the AGP 10 default.
                testedAbi = "arm64-v8a"
            }
        }
        // Robolectric 4.17 pokes JDK internals (FileDescriptor, reflection)
        // that modern JDKs seal by default; open them for the test JVM only.
        unitTests.all { test ->
            // Roborazzi: render through the real hardware-accelerated PixelCopy
            // path so every test in a class captures actual pixels (the default
            // software path yields blank frames after the first capture).
            test.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
            test.jvmArgs(
                "--add-opens=java.base/java.lang=ALL-UNNAMED",
                "--add-opens=java.base/java.util=ALL-UNNAMED",
                "--add-opens=java.base/java.io=ALL-UNNAMED",
                "--add-opens=java.base/java.nio=ALL-UNNAMED",
                "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
                // SDK-37 ApplicationSharedMemory shadow reaches SharedSecrets.
                "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
                // Robolectric loads its native runtime via System::load; JDK 25
                // warns (and will later block) restricted methods without this.
                "--enable-native-access=ALL-UNNAMED",
                // datastore's bundled protobuf still calls sun.misc.Unsafe
                // memory accessors (JEP 498 deprecation warning otherwise).
                "--sun-misc-unsafe-memory-access=allow",
                // The --add-opens set appends to the bootstrap classpath, which
                // disables CDS anyway; turn it off explicitly to silence the
                // "Sharing is only supported for boot loader classes" notice.
                "-Xshare:off",
            )
        }
    }

    // AGP can't run NDK strip on these AAR-supplied .so files (no NDK installed
    // in this build env). Opt them out explicitly so AGP doesn't print a noisy
    // "Unable to strip" notice on every build.
    packaging {
        // kotlinx-coroutines debug-probe stub; only read by the IDE debugger
        // agent, never at runtime on device.
        resources.excludes += "DebugProbesKt.bin"
        jniLibs {
            keepDebugSymbols += setOf(
                "**/libandroidx.graphics.path.so",
                "**/libdatastore_shared_counter.so",
            )
        }
    }

    lint {
        // Off-by-default check; enabled in every module so the shared
        // lint.xml can hold it at error severity.
        enable += "TypographyQuotes"
        abortOnError = true
        warningsAsErrors = true
        checkDependencies = true
        checkReleaseBuilds = true
        explainIssues = true
        showAll = true
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

roborazzi {
    // Checked-in screenshot baselines; verifyRoborazziDebug diffs against these.
    outputDir.set(layout.projectDirectory.dir("src/test/snapshots/roborazzi"))
}

dependencies {
    // Core library desugaring (required on legacy branch: minSdk=26 + JVM 25 bytecode)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // Project modules
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":domain"))
    implementation(project(":data"))

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
    // transitive runtime dep - without this the hiltJavaCompile* step fails
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

    // Profile installer - consumed by androidx.baselineprofile to install
    // baseline-prof.txt + startup-prof.txt shipped in the release APK.
    // (Profile files are produced by the :baselineprofile module's
    // `pixel7aApi33` managed-device run and copied to
    // app/src/release/baseline-prof.txt + startup-prof.txt.)
    implementation(libs.androidx.profileinstaller)

    // --- Unit test dependencies ---
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.truth)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.androidx.test.espresso.core)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.room.testing)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.okhttp.tls)
    testImplementation(libs.turbine)
    testImplementation(libs.compose.ui.test.junit4)
    // Real ExoPlayer decoding under Robolectric (TestPlayerRunHelper etc.)
    // for the workflow tests in com.dustvalve.next.android.workflow.
    testImplementation(libs.media3.test.utils)
    testImplementation(libs.media3.test.utils.robolectric)
    testImplementation(libs.hilt.android)
    // Processes @EntryPoint/@HiltAndroidTest defined in unit-test sources
    // (workflow tests reach into the real app graph under Robolectric).
    kspTest(libs.hilt.android.compiler)
    debugImplementation(libs.compose.ui.test.manifest)

    // --- Instrumentation (smoke + E2E) dependencies ---
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    // Pin espresso-core explicitly (same reason as the unit-test pin above):
    // compose ui-test pulls espresso 3.5.0 transitively, whose input
    // injection reflects on InputManager.getInstance - removed in SDK 37 -
    // and crashes every on-device test in Espresso.onIdle.
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.coroutines.test)
    androidTestImplementation(libs.media3.session)

    // Static analysis plugins / lint checks
    detektPlugins(libs.detekt.compose)
    lintChecks(libs.lint.slack.checks)
    lintChecks(libs.lint.slack.compose)
}

// No lint baseline: the old app/lint-baseline.xml ended up suppressing
// ZERO issues, so all it did was emit "The baseline was created using a
// different target/variant than it was checked against" whenever the AGP
// version or checked variant differed from the one that wrote the file
// (e.g. lintVitalRelease during assembleRelease). With warningsAsErrors =
// true every lint finding is a build failure outright - there is nothing
// to baseline away. If a truly unfixable upstream finding ever appears,
// prefer a scoped lint.xml severity override over reintroducing a baseline.
