plugins {
    id("com.android.test")
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "com.dustvalve.next.android.baselineprofile"
    compileSdk = 37
    // Compile against the 37.1 minor SDK release (API additions only; minor
    // SDKs carry no behavior changes and cannot be targeted - targetSdk
    // stays at the 37 major).
    compileSdkMinor = 1

    targetProjectPath = ":app"

    // THE load-bearing setting (same as :shippedsmoke and :macrobenchmark).
    // Without it AGP points android:targetPackage at the APP package, so the
    // instrumentation runs INSIDE the process being profiled and
    // benchmark-macro aborts with NOT-SELF-INSTRUMENTING before collecting
    // anything. Self-instrumenting runs this APK in its OWN process, driving
    // the app externally through UiAutomator - required for profile
    // collection.
    experimentalProperties["android.experimental.self-instrumenting"] = true

    defaultConfig {
        minSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // :app has an api flavor dimension; always exercise the future APK
        // (Android 17 / offload / Live Updates) from this harness.
        missingDimensionStrategy("api", "future")
    }

    flavorDimensions += "api"
    productFlavors {
        create("future") {
            dimension = "api"
        }
    }

    // Profiles must be collected from a NON-DEBUGGABLE release-shaped
    // variant (what ships). nonMinifiedRelease is created by
    // androidx.baselineprofile on :app; we only declare a release build
    // type here so variant matching works, and sign with debug so the
    // instrumentation APK (always debug-signed) can install alongside it.
    buildTypes {
        create("release") {
            signingConfig = signingConfigs.getByName("debug")
        }
        // nonMinifiedRelease is created by androidx.baselineprofile; do not
        // create{} it here (stacks into NonMinifiedNonMinified* with the plugin).
    }

    testOptions {
        animationsDisabled = true
        managedDevices.localDevices {
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

baselineProfile {
    managedDevices += "pixel7aApi37"
    useConnectedDevices = false
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
}
