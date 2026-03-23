// librespot-ffi: JNI bridge for Android
//
// This crate provides JNI-callable functions that wrap librespot's
// Rust API for use from Kotlin/Java on Android.
//
// Build with: cargo ndk -t arm64-v8a -t armeabi-v7a --platform 21 build --release
//
// TODO: Implement each native function. The Kotlin side (SpotifyBridge.kt)
// declares the corresponding `external fun` methods. When the .so is loaded
// via System.loadLibrary("librespot_ffi"), these functions are resolved by JNI.
//
// See Outify (https://github.com/iTomKo/Outify) for a working reference
// implementation of this pattern.

/// Placeholder — the actual JNI functions will be implemented here.
/// Each function follows the JNI naming convention:
///   Java_com_dustvalve_next_android_data_remote_spotify_SpotifyBridge_nativeMethodName
///
/// For now, this file serves as a scaffold. The Kotlin app builds and runs
/// without the .so present — SpotifyBridge.ensureLoaded() catches the
/// UnsatisfiedLinkError and throws SpotifyNotAvailableException.
