package com.dustvalve.next.android.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dustvalve.next.android.R
import com.dustvalve.next.android.ui.components.AppButtonGroup
import com.dustvalve.next.android.update.UpdateUiState
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsAppearanceSection(state: SettingsUiState, onAction: (SettingsAppearanceAction) -> Unit) {
    SettingsSection(
        title = stringResource(R.string.settings_section_appearance),
        icon = R.drawable.ic_palette,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                ThemeModePicker(themeMode = state.themeMode, onAction = onAction)

                Spacer(modifier = Modifier.height(16.dp))

                SettingsToggleRow(
                    title = stringResource(R.string.settings_dynamic_color),
                    checked = state.dynamicColor,
                    onCheckedChange = {
                        onAction(SettingsAppearanceAction.SetDynamicColor(it))
                    },
                    extras = SettingsToggleExtras(
                        description = stringResource(R.string.settings_dynamic_color_desc),
                    ),
                )

                val isDarkEffective = when (state.themeMode) {
                    "dark" -> true
                    "light" -> false
                    else -> isSystemInDarkTheme()
                }
                AnimatedVisibility(
                    visible = isDarkEffective,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        SettingsToggleRow(
                            title = stringResource(R.string.settings_oled_black),
                            checked = state.oledBlack,
                            onCheckedChange = {
                                onAction(SettingsAppearanceAction.SetOledBlack(it))
                            },
                            extras = SettingsToggleExtras(
                                description = stringResource(R.string.settings_oled_black_desc),
                            ),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                SettingsToggleRow(
                    title = stringResource(R.string.settings_album_art_colors),
                    checked = state.albumArtTheme,
                    onCheckedChange = {
                        onAction(SettingsAppearanceAction.SetAlbumArtTheme(it))
                    },
                    extras = SettingsToggleExtras(
                        description = stringResource(R.string.settings_album_art_colors_desc),
                    ),
                )

                Spacer(modifier = Modifier.height(16.dp))

                ProgressBarAppearanceControls(state = state, onAction = onAction)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ThemeModePicker(themeMode: String, onAction: (SettingsAppearanceAction) -> Unit) {
    Column {
        Text(
            text = stringResource(R.string.settings_theme),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(modifier = Modifier.height(8.dp))

        val themeOptions = listOf("system", "light", "dark")
        val themeLabels = listOf(
            stringResource(R.string.settings_theme_system),
            stringResource(R.string.settings_theme_light),
            stringResource(R.string.settings_theme_dark),
        )
        val selectedIndex = themeOptions.indexOf(themeMode).coerceAtLeast(0)

        AppButtonGroup(
            overflowIndicator = { _ -> },
            modifier = Modifier.fillMaxWidth(),
        ) {
            themeOptions.forEachIndexed { index, mode ->
                customItem(
                    buttonGroupContent = {
                        ToggleButton(
                            checked = index == selectedIndex,
                            onCheckedChange = { isChecked ->
                                if (isChecked) {
                                    onAction(SettingsAppearanceAction.SetThemeMode(mode))
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shapes = when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                themeOptions.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                        ) {
                            Text(themeLabels[index])
                        }
                    },
                    menuContent = {},
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProgressBarAppearanceControls(state: SettingsUiState, onAction: (SettingsAppearanceAction) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.settings_progress_bar_style),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Connected ButtonGroup [Wavy | Linear] - uses the same
        // M3E ButtonGroup component as the theme selector above
        // so the inter-button gap matches.
        val styleOptions = listOf("wavy", "linear")
        val styleLabels = listOf(
            stringResource(R.string.settings_progress_bar_style_wavy),
            stringResource(R.string.settings_progress_bar_style_linear),
        )
        AppButtonGroup(
            overflowIndicator = { _ -> },
            modifier = Modifier.fillMaxWidth(),
        ) {
            styleOptions.forEachIndexed { i, opt ->
                customItem(
                    buttonGroupContent = {
                        ToggleButton(
                            checked = state.progressBarStyle == opt,
                            onCheckedChange = {
                                if (it) {
                                    onAction(SettingsAppearanceAction.SetProgressBarStyle(opt))
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shapes = when (i) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                styleOptions.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                        ) {
                            Text(styleLabels[i])
                        }
                    },
                    menuContent = {},
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Size slider - discrete 4..32 dp in 4 dp steps.
        val sizeSteps = listOf(4, 8, 12, 16, 20, 24, 28, 32)
        var sizeIndex by remember(state.progressBarSizeDp) {
            mutableIntStateOf(
                sizeSteps.indexOf(state.progressBarSizeDp).coerceAtLeast(0),
            )
        }
        Text(
            text = stringResource(
                R.string.settings_progress_bar_size,
                sizeSteps[sizeIndex],
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Slider(
            value = sizeIndex.toFloat(),
            onValueChange = {
                sizeIndex = it.roundToInt().coerceIn(0, sizeSteps.lastIndex)
            },
            onValueChangeFinished = {
                onAction(SettingsAppearanceAction.SetProgressBarSizeDp(sizeSteps[sizeIndex]))
            },
            valueRange = 0f..(sizeSteps.lastIndex).toFloat(),
            steps = sizeSteps.size - 2,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsAudioQualitySection(
    state: SettingsUiState,
    onSetProgressiveDownload: (Boolean) -> Unit,
    onSetBackgroundAutoDownload: (Boolean) -> Unit,
    onSetSeamlessQualityUpgrade: (Boolean) -> Unit,
) {
    SettingsSection(
        title = stringResource(R.string.settings_section_audio_quality),
        icon = R.drawable.ic_high_quality,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Download-format picker + "MP3 only on metered" used to select
                // HQ Bandcamp purchase encodes. Free/stream downloads are always
                // the provider's stream format (mp3-128 / YouTube itag), so those
                // toggles were lying UI after login was removed. Progressive
                // download + seamless upgrade remain wired through the player.
                SettingsToggleRow(
                    title = stringResource(R.string.settings_progressive_download),
                    checked = state.progressiveDownload,
                    onCheckedChange = onSetProgressiveDownload,
                    extras = SettingsToggleExtras(
                        description = stringResource(R.string.settings_progressive_download_desc),
                    ),
                )

                Spacer(modifier = Modifier.height(16.dp))

                SettingsToggleRow(
                    title = stringResource(R.string.settings_background_auto_download),
                    checked = state.backgroundAutoDownload,
                    onCheckedChange = onSetBackgroundAutoDownload,
                    extras = SettingsToggleExtras(
                        description = stringResource(R.string.settings_background_auto_download_desc),
                    ),
                )

                // Only meaningful while streaming AND saving a copy in the
                // background: swap the playing URL for the finished file.
                SettingsSubToggle(
                    visible = state.progressiveDownload && state.backgroundAutoDownload,
                    title = stringResource(R.string.settings_seamless_upgrade),
                    description = stringResource(R.string.settings_seamless_upgrade_desc),
                    checked = state.seamlessQualityUpgrade,
                    onCheckedChange = onSetSeamlessQualityUpgrade,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsPlayerSection(
    state: SettingsUiState,
    onSetShowInlineVolumeSlider: (Boolean) -> Unit,
    onSetShowVolumeButton: (Boolean) -> Unit,
    onSetKeepScreenOnInApp: (Boolean) -> Unit,
    onSetKeepScreenOnWhilePlaying: (Boolean) -> Unit,
) {
    SettingsSection(
        title = stringResource(R.string.settings_section_player),
        icon = R.drawable.ic_volume_up,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingsToggleRow(
                    title = stringResource(R.string.settings_volume_slider),
                    checked = state.showInlineVolumeSlider,
                    onCheckedChange = onSetShowInlineVolumeSlider,
                    extras = SettingsToggleExtras(
                        description = stringResource(R.string.settings_volume_slider_desc),
                    ),
                )

                Spacer(modifier = Modifier.height(16.dp))

                SettingsToggleRow(
                    title = stringResource(R.string.settings_volume_button),
                    checked = state.showVolumeButton,
                    onCheckedChange = onSetShowVolumeButton,
                    extras = SettingsToggleExtras(
                        description = stringResource(R.string.settings_volume_button_desc),
                    ),
                )

                Spacer(modifier = Modifier.height(16.dp))

                SettingsToggleRow(
                    title = stringResource(R.string.settings_keep_screen_open),
                    checked = state.keepScreenOnInApp,
                    onCheckedChange = onSetKeepScreenOnInApp,
                    extras = SettingsToggleExtras(
                        description = stringResource(R.string.settings_keep_screen_open_desc),
                    ),
                )

                // Sub-toggle: only shown when the parent is on. When
                // checked, restricts wake-lock to "app open AND
                // playing". Defaults to true so the parent's
                // first-flip yields the lower-impact behaviour.
                SettingsSubToggle(
                    visible = state.keepScreenOnInApp,
                    title = stringResource(R.string.settings_keep_screen_only_playing),
                    description = stringResource(R.string.settings_keep_screen_only_playing_desc),
                    checked = state.keepScreenOnWhilePlaying,
                    onCheckedChange = onSetKeepScreenOnWhilePlaying,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsSearchSection(
    state: SettingsUiState,
    onSetSearchHistoryEnabled: (Boolean) -> Unit,
    onSetSearchHistorySource: (String, Boolean) -> Unit,
    onClearAllSearchHistory: () -> Unit,
) {
    SettingsSection(
        title = stringResource(R.string.settings_section_search),
        icon = R.drawable.ic_search,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingsToggleRow(
                    title = stringResource(R.string.settings_search_history),
                    checked = state.searchHistoryEnabled,
                    onCheckedChange = onSetSearchHistoryEnabled,
                    extras = SettingsToggleExtras(
                        description = stringResource(R.string.settings_search_history_desc),
                    ),
                )

                // Per-source sub-toggles, only when the global toggle is on
                // and only for sources the user has enabled.
                AnimatedVisibility(
                    visible = state.searchHistoryEnabled,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Column(modifier = Modifier.padding(top = 12.dp, start = SUB_TOGGLE_INDENT)) {
                        Text(
                            text = stringResource(R.string.settings_search_history_per_source),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        if (state.bandcampEnabled) {
                            SearchHistorySourceRow(
                                labelRes = R.string.settings_search_history_source_bandcamp,
                                checked = state.searchHistoryBandcamp,
                                onCheckedChange = { onSetSearchHistorySource("bandcamp", it) },
                            )
                        }
                        if (state.youtubeEnabled) {
                            SearchHistorySourceRow(
                                labelRes = R.string.settings_search_history_source_youtube,
                                checked = state.searchHistoryYoutube,
                                onCheckedChange = { onSetSearchHistorySource("youtube", it) },
                            )
                        }
                        if (state.soundcloudEnabled) {
                            SearchHistorySourceRow(
                                labelRes = R.string.settings_search_history_source_soundcloud,
                                checked = state.searchHistorySoundcloud,
                                onCheckedChange = { onSetSearchHistorySource("soundcloud", it) },
                            )
                        }
                        if (state.localMusicEnabled) {
                            SearchHistorySourceRow(
                                labelRes = R.string.settings_search_history_source_local,
                                checked = state.searchHistoryLocal,
                                onCheckedChange = { onSetSearchHistorySource("local", it) },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = onClearAllSearchHistory,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete_sweep),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_search_history_clear_all))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsAboutSection(
    state: SettingsUiState,
    onCheckForAppUpdate: () -> Unit,
    onSetAutoUpdateCheckEnabled: (Boolean) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    SettingsSection(
        title = stringResource(R.string.settings_section_about),
        icon = R.drawable.ic_info,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.settings_version),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = com.dustvalve.next.android.BuildConfig.VERSION_NAME,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_license),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
                Spacer(modifier = Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = {
                        uriHandler.openUri(
                            com.dustvalve.next.android.update.AppUpdateService.REPO_URL,
                        )
                    },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_info),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_open_repository))
                }
                Spacer(modifier = Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = onCheckForAppUpdate,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.updateState !is UpdateUiState.Checking &&
                        state.updateState !is UpdateUiState.Downloading,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_cloud_download),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_check_for_updates))
                }
                Spacer(modifier = Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = {
                        uriHandler.openUri(
                            com.dustvalve.next.android.update.AppUpdateService.REPO_URL + "/issues",
                        )
                    },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_bug_report),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_report_issue))
                }
                Spacer(modifier = Modifier.height(16.dp))
                SettingsToggleRow(
                    title = stringResource(R.string.settings_auto_update_title),
                    checked = state.autoUpdateCheckEnabled,
                    onCheckedChange = onSetAutoUpdateCheckEnabled,
                    extras = SettingsToggleExtras(
                        description = stringResource(R.string.settings_auto_update_desc),
                    ),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsDebugSection(playerDebugOverlay: Boolean, onSetPlayerDebugOverlay: (Boolean) -> Unit) {
    SettingsSection(
        title = stringResource(R.string.settings_section_debug),
        icon = R.drawable.ic_bug_report,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingsToggleRow(
                    title = stringResource(R.string.settings_show_debug_info),
                    checked = playerDebugOverlay,
                    onCheckedChange = onSetPlayerDebugOverlay,
                    extras = SettingsToggleExtras(
                        description = stringResource(R.string.settings_show_debug_info_desc),
                    ),
                )
            }
        }
    }
}
