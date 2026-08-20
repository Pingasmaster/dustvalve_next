package com.dustvalve.next.android.ui.screens.settings

/** Actions emitted by [SettingsAppearanceSection]. */
internal sealed interface SettingsAppearanceAction {
    data class SetThemeMode(val mode: String) : SettingsAppearanceAction
    data class SetColorSource(val source: String) : SettingsAppearanceAction
    data class SetOledBlack(val enabled: Boolean) : SettingsAppearanceAction
    data class SetProgressBarStyle(val style: String) : SettingsAppearanceAction
    data class SetProgressBarSizeDp(val sizeDp: Int) : SettingsAppearanceAction
}

internal fun handleSettingsAppearanceAction(viewModel: SettingsViewModel, action: SettingsAppearanceAction) {
    val appearance = viewModel.appearance
    when (action) {
        is SettingsAppearanceAction.SetThemeMode ->
            appearance.setThemeMode(action.mode)

        is SettingsAppearanceAction.SetColorSource ->
            appearance.setColorSource(action.source)

        is SettingsAppearanceAction.SetOledBlack ->
            appearance.setOledBlack(action.enabled)

        is SettingsAppearanceAction.SetProgressBarStyle ->
            appearance.setProgressBarStyle(action.style)

        is SettingsAppearanceAction.SetProgressBarSizeDp ->
            appearance.setProgressBarSizeDp(action.sizeDp)
    }
}
