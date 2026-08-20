package com.dustvalve.next.android.data.local.datastore

/**
 * Wallpaper / album-art / built-in palette. Stored as the existing
 * [AppearanceSettingsStore.dynamicColor] + [AppearanceSettingsStore.albumArtTheme]
 * pair so older installs keep their choice; the two flags are mutually
 * exclusive (album art wins if a legacy file had both on).
 */
object ColorSource {
    const val DYNAMIC = "dynamic"
    const val ALBUM_ART = "album_art"
    const val APP = "app"

    fun fromPrefs(dynamicColor: Boolean, albumArtTheme: Boolean): String = when {
        albumArtTheme -> ALBUM_ART
        dynamicColor -> DYNAMIC
        else -> APP
    }
}
