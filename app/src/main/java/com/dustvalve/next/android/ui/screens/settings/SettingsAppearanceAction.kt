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
    when (action) {
        is SettingsAppearanceAction.SetThemeMode ->
            viewModel.setThemeMode(action.mode)

        is SettingsAppearanceAction.SetDynamicColor ->
            viewModel.setDynamicColor(action.enabled)

        is SettingsAppearanceAction.SetAlbumArtTheme ->
            viewModel.setAlbumArtTheme(action.enabled)

        is SettingsAppearanceAction.SetOledBlack ->
            viewModel.setOledBlack(action.enabled)

        is SettingsAppearanceAction.SetProgressBarStyle ->
            viewModel.setProgressBarStyle(action.style)

        is SettingsAppearanceAction.SetProgressBarSizeDp ->
            viewModel.setProgressBarSizeDp(action.sizeDp)
    }
}
