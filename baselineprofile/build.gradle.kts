plugins {
    id("com.android.test")
}

android {
    namespace = "com.dustvalve.next.android.baselineprofile"
    compileSdk = 37

    targetProjectPath = ":app"

    testOptions.managedDevices.localDevices {
        register("pixel6Api33") {
            device = "Pixel 6"
            apiLevel = 33
            systemImageSource = "aosp"
        }
    }
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.ext.junit)
}
