plugins {
    id("com.android.test")
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
    }

    // Profiles must be collected from the NON-DEBUGGABLE release variant
    // (what ships); variants are matched to the target project BY NAME, so
    // declare a release build type. Debug-signed for the same reason as
    // :shippedsmoke: :app's release signingConfig resolves to the
    // materialized debug keystore, and instrumenting another package
    // requires matching certificates.
    buildTypes {
        create("release") {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    testOptions.managedDevices.localDevices {
        register("pixel7aApi33") {
            device = "Pixel 7a"
            apiLevel = 33
            systemImageSource = "aosp"
            // Test the arm64 APK - the ABI every real device runs - via the
            // image's built-in translation layer. Matches the AGP 10 default.
            testedAbi = "arm64-v8a"
        }
    }
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.ext.junit)
}
