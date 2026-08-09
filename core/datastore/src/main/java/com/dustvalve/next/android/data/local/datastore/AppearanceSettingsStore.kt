package com.dustvalve.next.android.data.local.datastore

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Theme / OLED / album-art / progress-bar preference surface. */
interface AppearanceSettingsStore {
    val themeMode: Flow<String>
    val dynamicColor: Flow<Boolean>
    val oledBlack: Flow<Boolean>
    val albumArtTheme: Flow<Boolean>

    /** "wavy" or "linear". Replaces the legacy boolean wavyProgressBar. */
    val progressBarStyle: Flow<String>

    /** Stroke height of the player progress bar in dp; default 24. */
    val progressBarSizeDp: Flow<Int>

    suspend fun setThemeMode(mode: String)
    suspend fun setDynamicColor(enabled: Boolean)
    suspend fun setOledBlack(enabled: Boolean)
    suspend fun setAlbumArtTheme(enabled: Boolean)
    suspend fun setProgressBarStyle(style: String)
    suspend fun setProgressBarSizeDp(sizeDp: Int)
}
