plugins {
    id("com.android.test")
}

/*
 * Shipped-config smoke: drives the release APK EXACTLY as users receive it.
 *
 * The :app androidTest release lane (-PtestReleaseBuild) has to apply
 * proguard-test-support.pro, which keeps every non-app class so the
 * instrumentation APK can link against them. That is unavoidable - AGP
 * deduplicates app-provided classes out of the test APK - but it means the
 * APK under test is NOT the shipped one: its libraries are unminified, so
 * library-level R8 breakage (Hilt, Room, kotlinx-serialization: all
 * reflection- or codegen-heavy) goes uncaught.
 *
 * A com.android.test module has no such constraint: it ships its own APK and
 * addresses the app through UiAutomator by resource id, never by linking app
 * classes. So :app's release variant is built here with proguard-rules.pro
 * ALONE - no test-support keeps - and driven as-is. Do NOT pass
 * -PtestReleaseBuild to this module; that would defeat its entire purpose.
 */
android {
    namespace = "com.dustvalve.next.android.shippedsmoke"
    compileSdk = 37
    // Matches :app - compile against the 37.1 minor SDK release.
    compileSdkMinor = 1

    targetProjectPath = ":app"

    defaultConfig {
        minSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // A com.android.test module only gets a `debug` variant by default, and
    // variants are matched to the target project BY NAME - so testing :app's
    // release build requires declaring a release build type here. Signed with
    // the debug key because :app's release signingConfig resolves to the same
    // materialized debug keystore: instrumenting another package requires
    // both APKs to carry the same certificate.
    buildTypes {
        create("release") {
            signingConfig = signingConfigs.getByName("debug")
            // Required, not desired: AGP refuses to instrument a minified app
            // from a non-minified test module. proguard-rules.pro turns every
            // actual transformation back off - see the file for why.
            isMinifyEnabled = true
            proguardFiles("proguard-rules.pro")
        }
    }

    testOptions.managedDevices.localDevices {
        // Mirrors :app and :macrobenchmark - apiLevel must be >= :app's
        // minSdk (37), and only 16 KB page-size Google APIs images exist.
        register("pixel7aApi37") {
            device = "Pixel 7a"
            apiLevel = 37
            systemImageSource = "google"
            pageAlignment = com.android.build.api.dsl.ManagedVirtualDevice.PageAlignment.FORCE_16KB_PAGES
            testedAbi = "arm64-v8a"
        }
    }
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.uiautomator)

    // androidx.test annotates Checks/Tracer with @CanIgnoreReturnValue and
    // @MustBeClosed but does not declare errorprone-annotations as a runtime
    // dep, so R8 reports them as missing classes. :app carries this for the
    // same reason (there, via Hilt's generated components).
    implementation(libs.errorprone.annotations)
}
