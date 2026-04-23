plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.dustvalve.next.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.dustvalve.next.android"
        minSdk = 33
        targetSdk = 37
        versionCode = 167
        versionName = "0.3.17"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
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

    packaging {
        resources {
            // Rhino (pulled transitively by NewPipe Extractor) ships a service file
            // referencing javax.script, which is not available on Android.
            excludes += "META-INF/services/javax.script.ScriptEngineFactory"
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2026.04.01")
    implementation(composeBom)

    // Material 3 Expressive + compatible compose libs (all pinned to 1.11.0)
    implementation("androidx.compose.runtime:runtime:1.11.0")
    implementation("androidx.compose.ui:ui:1.11.0")
    implementation("androidx.compose.ui:ui-graphics:1.11.0")
    implementation("androidx.compose.ui:ui-tooling-preview:1.11.0")
    debugImplementation("androidx.compose.ui:ui-tooling:1.11.0")
    implementation("androidx.compose.material3:material3:1.5.0-alpha18")
    implementation("androidx.compose.foundation:foundation:1.11.0")
    implementation("androidx.compose.animation:animation:1.11.0")

    // Graphics Shapes (for MaterialShapes)
    implementation("androidx.graphics:graphics-shapes:1.1.0")

    // Navigation 3
    implementation("androidx.navigation3:navigation3-runtime:1.1.1")
    implementation("androidx.navigation3:navigation3-ui:1.1.1")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0-beta01")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0-beta01")

    // Activity
    implementation("androidx.activity:activity-compose:1.13.0")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.59.2")
    ksp("com.google.dagger:hilt-android-compiler:2.59.2")
    implementation("androidx.hilt:hilt-lifecycle-viewmodel-compose:1.3.0")

    // WorkManager + Hilt Work
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation("androidx.hilt:hilt-work:1.3.0")
    ksp("androidx.hilt:hilt-compiler:1.3.0")

    // Room
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.3.0-alpha08")

    // DocumentFile (SAF directory/file creation for export)
    implementation("androidx.documentfile:documentfile:1.1.0")

    // OkHttp
    implementation("com.squareup.okhttp3:okhttp:5.3.2")

    // Jsoup
    implementation("org.jsoup:jsoup:1.22.2")

    // Coil 3
    implementation("io.coil-kt.coil3:coil-compose:3.4.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.4.0")

    // Palette (for extracting dominant colors from album art)
    implementation("androidx.palette:palette-ktx:1.0.0")

    // MaterialKolor (generate M3 ColorScheme from seed color)
    implementation("com.materialkolor:material-kolor:4.1.1")

    // Media3
    implementation("androidx.media3:media3-exoplayer:1.10.0")
    implementation("androidx.media3:media3-session:1.10.0")
    implementation("androidx.media3:media3-datasource-okhttp:1.10.0")

    // Kotlinx Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // WebKit
    implementation("androidx.webkit:webkit:1.16.0-rc01")

    // Core KTX
    implementation("androidx.core:core-ktx:1.18.0")

    // NewPipe Extractor (YouTube scraping)
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.1")

    // --- Unit test dependencies ---
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("com.google.truth:truth:1.4.5")
    testImplementation("io.mockk:mockk:1.14.9")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("androidx.test.ext:junit:1.3.0")
    testImplementation("androidx.room:room-testing:2.8.4")
    testImplementation("com.squareup.okhttp3:mockwebserver:5.3.2")
    testImplementation("com.squareup.okhttp3:okhttp-tls:5.3.2")
    testImplementation("app.cash.turbine:turbine:1.2.1")
}
