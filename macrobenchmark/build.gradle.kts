plugins {
    id("com.android.test")
}

android {
    namespace = "com.dustvalve.next.android.macrobenchmark"
    compileSdk = 37
    // Compile against the 37.1 minor SDK release (API additions only; minor
    // SDKs carry no behavior changes and cannot be targeted - targetSdk
    // stays at the 37 major).
    compileSdkMinor = 1

    targetProjectPath = ":app"

    // THE load-bearing setting (same as :shippedsmoke). Without it AGP builds
    // this module's manifest with android:targetPackage pointing at the APP
    // package, i.e. the instrumentation runs INSIDE the process being
    // measured. benchmark-macro detects that and aborts every test with
    // NOT-SELF-INSTRUMENTING ("Benchmark manifest is instrumenting separate
    // process") before a single iteration runs - the benchmarks compile and
    // "pass" a build but never do any real work. Self-instrumenting makes
    // this APK standalone: it runs in its OWN process and drives the app
    // externally through UiAutomator, which is what macrobenchmarking
    // requires.
    experimentalProperties["android.experimental.self-instrumenting"] = true

    defaultConfig {
        minSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // A com.android.test module only gets a `debug` variant by default, and
    // variants are matched to the target project BY NAME. Benchmarks must
    // measure the NON-DEBUGGABLE release variant (debuggable builds disable
    // the optimizations being measured; benchmark-macro raises DEBUGGABLE
    // against them), so declare a release build type here, signed with the
    // debug key exactly like :shippedsmoke - :app's release signingConfig
    // resolves to the same materialized debug keystore, and instrumenting
    // another package requires both APKs to carry the same certificate.
    buildTypes {
        create("release") {
            signingConfig = signingConfigs.getByName("debug")
            // Left unminified on purpose: a self-instrumenting module never
            // links the tested app's classes, so there is no mapping to
            // apply and no reason to run R8 over a benchmark harness.
        }
    }

    testOptions.managedDevices.localDevices {
        // apiLevel must be >= :app's minSdk (37); API 33 cannot install the
        // APK (INSTALL_FAILED_OLDER_SDK). Only 16 KB page-size Google APIs
        // images are published for API 37.
        register("pixel7aApi37") {
            device = "Pixel 7a"
            apiLevel = 37
            systemImageSource = "google"
            pageAlignment = com.android.build.api.dsl.ManagedVirtualDevice.PageAlignment.FORCE_16KB_PAGES
            // Test the arm64 APK - the ABI every real device runs - via the
            // image's built-in translation layer. Matches the AGP 10 default.
            testedAbi = "arm64-v8a"
        }
    }
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui.test.junit4)
}
