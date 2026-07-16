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
