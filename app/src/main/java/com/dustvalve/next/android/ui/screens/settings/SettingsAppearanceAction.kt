package com.dustvalve.next.android.ui.screens.settings

/** Actions emitted by [SettingsAppearanceSection]. */
internal sealed interface SettingsAppearanceAction {
    data class SetThemeMode(val mode: String) : SettingsAppearanceAction
    data class SetDynamicColor(val enabled: Boolean) : SettingsAppearanceAction
    data class SetAlbumArtTheme(val enabled: Boolean) : SettingsAppearanceAction
    data class SetOledBlack(val enabled: Boolean) : SettingsAppearanceAction
    data class SetProgressBarStyle(val style: String) : SettingsAppearanceAction
    data class SetProgressBarSizeDp(val sizeDp: Int) : SettingsAppearanceAction
}

internal fun handleSettingsAppearanceAction(viewModel: SettingsViewModel, action: SettingsAppearanceAction) {
    val appearance = viewModel.appearance
    when (action) {
        is SettingsAppearanceAction.SetThemeMode ->
            appearance.setThemeMode(action.mode)

        is SettingsAppearanceAction.SetDynamicColor ->
            appearance.setDynamicColor(action.enabled)

        is SettingsAppearanceAction.SetAlbumArtTheme ->
            appearance.setAlbumArtTheme(action.enabled)

        is SettingsAppearanceAction.SetOledBlack ->
            appearance.setOledBlack(action.enabled)

        is SettingsAppearanceAction.SetProgressBarStyle ->
            appearance.setProgressBarStyle(action.style)

        is SettingsAppearanceAction.SetProgressBarSizeDp ->
            appearance.setProgressBarSizeDp(action.sizeDp)
    }
}
