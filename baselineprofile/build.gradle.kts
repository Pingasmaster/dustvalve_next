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
        // apiLevel must be >= :app's minSdk (37); API 33 cannot install the
        // APK (INSTALL_FAILED_OLDER_SDK). Only 16 KB page-size Google APIs
        // images are published for API 37.
        register("pixel6Api37") {
            device = "Pixel 6"
            apiLevel = 37
            systemImageSource = "google"
            pageAlignment = com.android.build.api.dsl.ManagedVirtualDevice.PageAlignment.FORCE_16KB_PAGES
        }
    }
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.ext.junit)
}
