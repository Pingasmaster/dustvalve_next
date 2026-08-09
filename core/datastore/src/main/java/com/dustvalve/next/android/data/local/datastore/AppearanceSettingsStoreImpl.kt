package com.dustvalve.next.android.data.local.datastore

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class AppearanceSettingsStoreImpl(private val prefs: SettingsPreferences) : AppearanceSettingsStore {
    private val keys = SettingsPreferences.Keys

    override val themeMode: Flow<String> = prefs.guardedPreferences.map { it[keys.THEME_MODE] ?: "system" }
    override val dynamicColor: Flow<Boolean> = prefs.guardedPreferences.map { it[keys.DYNAMIC_COLOR] ?: true }
    override val oledBlack: Flow<Boolean> = prefs.guardedPreferences.map { it[keys.OLED_BLACK] ?: false }
    override val albumArtTheme: Flow<Boolean> = prefs.guardedPreferences.map { it[keys.ALBUM_ART_THEME] ?: false }
    override val progressBarStyle: Flow<String> = prefs.guardedPreferences.map { it[keys.PROGRESS_BAR_STYLE] ?: "wavy" }
    override val progressBarSizeDp: Flow<Int> = prefs.guardedPreferences.map { it[keys.PROGRESS_BAR_SIZE_DP] ?: 24 }

    override suspend fun setThemeMode(mode: String) {
        prefs.edit { it[keys.THEME_MODE] = mode }
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        prefs.edit { it[keys.DYNAMIC_COLOR] = enabled }
    }

    override suspend fun setOledBlack(enabled: Boolean) {
        prefs.edit { it[keys.OLED_BLACK] = enabled }
    }

    override suspend fun setAlbumArtTheme(enabled: Boolean) {
        prefs.edit { it[keys.ALBUM_ART_THEME] = enabled }
    }

    override suspend fun setProgressBarStyle(style: String) {
        prefs.edit { it[keys.PROGRESS_BAR_STYLE] = style }
    }

    override suspend fun setProgressBarSizeDp(sizeDp: Int) {
        prefs.edit { it[keys.PROGRESS_BAR_SIZE_DP] = sizeDp }
    }
}
