package com.dustvalve.next.android.data.remote.spotify

import android.util.Log

/**
 * JNI bridge to Rust librespot native library.
 * Native methods are loaded lazily — the app builds and runs even without .so files present.
 * Operations throw [SpotifyNotAvailableException] if the native library isn't loaded.
 */
object SpotifyBridge {

    private var nativeLoaded = false

    fun ensureLoaded() {
        if (!nativeLoaded) {
            try {
                System.loadLibrary("librespot_ffi")
                nativeLoaded = true
            } catch (e: UnsatisfiedLinkError) {
                Log.e("SpotifyBridge", "Native library not available", e)
                throw SpotifyNotAvailableException("Spotify native library not found. Please rebuild with Rust support.", e)
            }
        }
    }

    fun isAvailable(): Boolean {
        if (nativeLoaded) return true
        return try {
            ensureLoaded()
            true
        } catch (_: SpotifyNotAvailableException) {
            false
        }
    }

    // --- Session ---
    external fun nativeInitSession(cacheDir: String, credentialsPath: String)
    external fun nativeShutdownSession()
    external fun nativeHasCachedCredentials(credentialsPath: String): Boolean

    // --- Auth (OAuth PKCE) ---
    external fun nativeGetAuthorizationURL(): String
    external fun nativeHandleOAuthCode(code: String, state: String)

    // --- Search ---
    external fun nativeSearch(query: String, filter: String?): String // JSON

    // --- Metadata ---
    external fun nativeGetTrackMetadata(uri: String): String // JSON
    external fun nativeGetAlbumMetadata(uri: String): String // JSON
    external fun nativeGetArtistMetadata(uri: String): String // JSON
    external fun nativeGetPlaylistMetadata(uri: String): String // JSON

    // --- Audio ---
    external fun nativeDownloadTrack(uri: String, outputPath: String, quality: String): Boolean
}

class SpotifyNotAvailableException(message: String, cause: Throwable? = null) : Exception(message, cause)
