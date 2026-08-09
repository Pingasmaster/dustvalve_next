package com.dustvalve.next.android.ui.screens.settings

import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Shared try/catch launch for Settings preference writes. */
internal fun CoroutineScope.launchSettingsPref(block: suspend () -> Unit) {
    launch { runSettingsPrefWrite(block) }
}

/**
 * Appearance + player-chrome preference writes extracted from SettingsViewModel.
 */
internal class SettingsAppearancePrefsCoordinator(private val scope: CoroutineScope, private val settingsDataStore: SettingsDataStore) {
    fun setThemeMode(mode: String) = scope.launchSettingsPref { settingsDataStore.setThemeMode(mode) }

    fun setDynamicColor(enabled: Boolean) = scope.launchSettingsPref {
        settingsDataStore.setDynamicColor(enabled)
    }

    fun setOledBlack(enabled: Boolean) = scope.launchSettingsPref {
        settingsDataStore.setOledBlack(enabled)
    }

    fun setAlbumArtTheme(enabled: Boolean) = scope.launchSettingsPref {
        settingsDataStore.setAlbumArtTheme(enabled)
    }

    fun setProgressBarStyle(style: String) = scope.launchSettingsPref {
        settingsDataStore.setProgressBarStyle(style)
    }

    fun setProgressBarSizeDp(sizeDp: Int) = scope.launchSettingsPref {
        settingsDataStore.setProgressBarSizeDp(sizeDp)
    }

    fun setShowInlineVolumeSlider(enabled: Boolean) = scope.launchSettingsPref {
        settingsDataStore.setShowInlineVolumeSlider(enabled)
    }

    fun setShowVolumeButton(enabled: Boolean) = scope.launchSettingsPref {
        settingsDataStore.setShowVolumeButton(enabled)
    }

    fun setKeepScreenOnInApp(enabled: Boolean) = scope.launchSettingsPref {
        settingsDataStore.setKeepScreenOnInApp(enabled)
    }

    fun setKeepScreenOnWhilePlaying(enabled: Boolean) = scope.launchSettingsPref {
        settingsDataStore.setKeepScreenOnWhilePlaying(enabled)
    }

    fun setPlayerDebugOverlay(enabled: Boolean) = scope.launchSettingsPref {
        settingsDataStore.setPlayerDebugOverlay(enabled)
    }
}
