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
import androidx.compose.material3.Button
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.dustvalve.next.android.R
import com.dustvalve.next.android.domain.model.AudioFormat
import com.dustvalve.next.android.ui.components.AppButtonGroup
import com.dustvalve.next.android.ui.theme.AppShapes
import com.dustvalve.next.android.ui.util.displayNameRes
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
                    description = stringResource(R.string.settings_dynamic_color_desc),
                    checked = state.dynamicColor,
                    onCheckedChange = {
                        onAction(SettingsAppearanceAction.SetDynamicColor(it))
                    },
                )

                Spacer(modifier = Modifier.height(16.dp))

                SettingsToggleRow(
                    title = stringResource(R.string.settings_album_art_colors),
                    description = stringResource(R.string.settings_album_art_colors_desc),
                    checked = state.albumArtTheme,
                    onCheckedChange = {
                        onAction(SettingsAppearanceAction.SetAlbumArtTheme(it))
                    },
                )

                val isDarkEffective = when (state.themeMode) {
                    "dark" -> true
                    "light" -> false
                    else -> isSystemInDarkTheme()
                }

                SettingsSubToggle(
                    visible = isDarkEffective,
                    title = stringResource(R.string.settings_oled_black),
                    description = stringResource(R.string.settings_oled_black_desc),
                    checked = state.oledBlack,
                    onCheckedChange = {
                        onAction(SettingsAppearanceAction.SetOledBlack(it))
                    },
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

        val themeOptions = listOf("light", "dark", "system")
        val themeLabels = listOf(
            stringResource(R.string.settings_theme_light),
            stringResource(R.string.settings_theme_dark),
            stringResource(R.string.settings_theme_system),
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
internal fun SettingsConnectionsSection(
    state: SettingsUiState,
    onBandcampLoginClick: () -> Unit,
    onYouTubeMusicLoginClick: () -> Unit,
    onSignOutBandcamp: () -> Unit,
    onSignOutYouTubeMusic: () -> Unit,
) {
    SettingsSection(
        title = stringResource(R.string.settings_section_connections),
        icon = R.drawable.ic_account_circle,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                BandcampConnectionBlock(
                    state = state,
                    onLoginClick = onBandcampLoginClick,
                    onSignOut = onSignOutBandcamp,
                )
                Spacer(modifier = Modifier.height(20.dp))
                YoutubeMusicConnectionBlock(
                    isLoggedIn = state.ytmAccountState.isLoggedIn,
                    onLoginClick = onYouTubeMusicLoginClick,
                    onSignOut = onSignOutYouTubeMusic,
                )
            }
        }
    }
}

@Composable
private fun BandcampConnectionBlock(state: SettingsUiState, onLoginClick: () -> Unit, onSignOut: () -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_cloud),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.settings_source_bandcamp),
                style = MaterialTheme.typography.titleSmall,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (state.accountState.isLoggedIn) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.accountState.avatarUrl != null) {
                    AsyncImage(
                        model = state.accountState.avatarUrl,
                        contentDescription = stringResource(R.string.settings_cd_avatar),
                        modifier = Modifier
                            .size(40.dp)
                            .clip(AppShapes.Avatar),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.accountState.username ?: stringResource(R.string.common_connected),
                        style = MaterialTheme.typography.bodyMedium,
                        // Primary accent - matches the YouTube
                        // Music connected state below.
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onSignOut,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.common_action_disconnect))
            }
        } else {
            Text(
                text = stringResource(R.string.settings_connect_bandcamp_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onLoginClick,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_connect_bandcamp))
            }
        }
    }
}

@Composable
private fun YoutubeMusicConnectionBlock(isLoggedIn: Boolean, onLoginClick: () -> Unit, onSignOut: () -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_play_circle),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.settings_youtube_music),
                style = MaterialTheme.typography.titleSmall,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (isLoggedIn) {
            Text(
                text = stringResource(R.string.common_connected),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onSignOut,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.common_action_disconnect))
            }
        } else {
            Text(
                text = stringResource(R.string.settings_connect_youtube_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onLoginClick,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_connect_youtube))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsAudioQualitySection(
    state: SettingsUiState,
    onShowFormatSheet: () -> Unit,
    onSetSaveDataOnMetered: (Boolean) -> Unit,
    onSetProgressiveDownload: (Boolean) -> Unit,
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
                Text(
                    text = stringResource(R.string.settings_download_format),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.settings_download_format_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                val currentFormat = AudioFormat.fromKey(state.downloadFormat)
                FilledTonalButton(
                    onClick = onShowFormatSheet,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(currentFormat?.displayNameRes ?: R.string.audio_format_flac))
                }

                Spacer(modifier = Modifier.height(16.dp))

                SettingsToggleRow(
                    title = stringResource(R.string.settings_mp3_on_metered),
                    description = stringResource(R.string.settings_mp3_on_metered_desc),
                    checked = state.saveDataOnMetered,
                    onCheckedChange = onSetSaveDataOnMetered,
                )

                Spacer(modifier = Modifier.height(16.dp))

                SettingsToggleRow(
                    title = stringResource(R.string.settings_progressive_download),
                    description = stringResource(R.string.settings_progressive_download_desc),
                    checked = state.progressiveDownload,
                    onCheckedChange = onSetProgressiveDownload,
                )

                // Dependent on progressive download - indented sub-toggle
                // like every other dependent setting.
                SettingsSubToggle(
                    visible = state.progressiveDownload,
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
                    description = stringResource(R.string.settings_volume_slider_desc),
                    checked = state.showInlineVolumeSlider,
                    onCheckedChange = onSetShowInlineVolumeSlider,
                )

                Spacer(modifier = Modifier.height(16.dp))

                SettingsToggleRow(
                    title = stringResource(R.string.settings_volume_button),
                    description = stringResource(R.string.settings_volume_button_desc),
                    checked = state.showVolumeButton,
                    onCheckedChange = onSetShowVolumeButton,
                )

                Spacer(modifier = Modifier.height(16.dp))

                SettingsToggleRow(
                    title = stringResource(R.string.settings_keep_screen_open),
                    description = stringResource(R.string.settings_keep_screen_open_desc),
                    checked = state.keepScreenOnInApp,
                    onCheckedChange = onSetKeepScreenOnInApp,
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
                    description = stringResource(R.string.settings_search_history_desc),
                    checked = state.searchHistoryEnabled,
                    onCheckedChange = onSetSearchHistoryEnabled,
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
                    description = stringResource(R.string.settings_auto_update_desc),
                    checked = state.autoUpdateCheckEnabled,
                    onCheckedChange = onSetAutoUpdateCheckEnabled,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsDebugSection(albumCoverLongPressCarousel: Boolean, onSetAlbumCoverLongPressCarousel: (Boolean) -> Unit) {
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
                    description = stringResource(R.string.settings_show_debug_info_desc),
                    checked = albumCoverLongPressCarousel,
                    onCheckedChange = onSetAlbumCoverLongPressCarousel,
                )
            }
        }
    }
}
