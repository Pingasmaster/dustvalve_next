import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.dustvalve.next.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.dustvalve.next.android"
        minSdk = 26
        targetSdk = 36
        versionCode = 29
        versionName = "0.0.29"
    }

    signingConfigs {
        create("release") {
            val passwordFile = rootProject.file(".password-signing-keys")
            val signingPassword = if (passwordFile.exists()) {
                passwordFile.readText().trim()
            } else ""
            storeFile = file("../release-keystore.jks")
            storePassword = signingPassword
            keyAlias = "dustvalve"
            keyPassword = signingPassword
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
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
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2026.02.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Material 3 Expressive (pinned outside BOM)
    implementation("androidx.compose.material3:material3:1.5.0-alpha15")
    implementation("androidx.compose.material:material-icons-extended")

    // Graphics Shapes (for MaterialShapes)
    implementation("androidx.graphics:graphics-shapes:1.1.0")

    // Navigation 3
    implementation("androidx.navigation3:navigation3-runtime:1.1.0-alpha05")
    implementation("androidx.navigation3:navigation3-ui:1.1.0-alpha05")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0-alpha01")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0-alpha01")

    // Activity
    implementation("androidx.activity:activity-compose:1.13.0-rc01")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.59.2")
    ksp("com.google.dagger:hilt-android-compiler:2.59.2")
    implementation("androidx.hilt:hilt-lifecycle-viewmodel-compose:1.3.0")

    // Room
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.3.0-alpha06")

    // OkHttp
    implementation("com.squareup.okhttp3:okhttp:5.3.2")

    // Jsoup
    implementation("org.jsoup:jsoup:1.22.1")

    // Coil 3
    implementation("io.coil-kt.coil3:coil-compose:3.4.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.4.0")

    // Palette (for extracting dominant colors from album art)
    implementation("androidx.palette:palette-ktx:1.0.0")

    // Media3
    implementation("androidx.media3:media3-exoplayer:1.10.0-beta01")
    implementation("androidx.media3:media3-session:1.10.0-beta01")
    implementation("androidx.media3:media3-datasource-okhttp:1.10.0-beta01")

    // Kotlinx Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // WebKit
    implementation("androidx.webkit:webkit:1.16.0-alpha02")

    // Core KTX
    implementation("androidx.core:core-ktx:1.18.0-rc01")
}
