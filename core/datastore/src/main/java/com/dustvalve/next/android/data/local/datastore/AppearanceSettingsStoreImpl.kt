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
        prefs.edit {
            it[keys.DYNAMIC_COLOR] = enabled
            if (enabled) it[keys.ALBUM_ART_THEME] = false
        }
    }

    override suspend fun setOledBlack(enabled: Boolean) {
        prefs.edit { it[keys.OLED_BLACK] = enabled }
    }

    override suspend fun setAlbumArtTheme(enabled: Boolean) {
        prefs.edit {
            it[keys.ALBUM_ART_THEME] = enabled
            if (enabled) it[keys.DYNAMIC_COLOR] = false
        }
    }

    override suspend fun setColorSource(source: String) {
        prefs.edit {
            when (source) {
                ColorSource.ALBUM_ART -> {
                    it[keys.DYNAMIC_COLOR] = false
                    it[keys.ALBUM_ART_THEME] = true
                }

                ColorSource.APP -> {
                    it[keys.DYNAMIC_COLOR] = false
                    it[keys.ALBUM_ART_THEME] = false
                }

                else -> {
                    it[keys.DYNAMIC_COLOR] = true
                    it[keys.ALBUM_ART_THEME] = false
                }
            }
        }
    }

    override suspend fun setProgressBarStyle(style: String) {
        prefs.edit { it[keys.PROGRESS_BAR_STYLE] = style }
    }

    override suspend fun setProgressBarSizeDp(sizeDp: Int) {
        prefs.edit { it[keys.PROGRESS_BAR_SIZE_DP] = sizeDp }
    }
}
