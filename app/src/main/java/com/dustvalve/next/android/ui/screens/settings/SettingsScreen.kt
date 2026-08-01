package com.dustvalve.next.android.ui.screens.settings

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dustvalve.next.android.R
import com.dustvalve.next.android.domain.model.AudioFormat
import com.dustvalve.next.android.ui.adaptive.LocalAdaptiveLayoutInfo
import com.dustvalve.next.android.ui.adaptive.adaptiveContentWidth
import com.dustvalve.next.android.ui.components.update.AppUpdateDialog
import com.dustvalve.next.android.ui.theme.AppShapes
import com.dustvalve.next.android.ui.util.displayNameRes

// Shared left-padding for every child toggle that appears under a parent
// switch in the settings UI. Toggle rows go through SettingsToggleRow
// (subRow = true for indented children); dependent single toggles use
// SettingsSubToggle, which adds the canonical AnimatedVisibility reveal.
internal val SUB_TOGGLE_INDENT = 16.dp

// Fixed gap between a toggle row's label column and its switch so
// descriptions wrap at the same width on every row.
internal val TOGGLE_LABEL_END_GAP = 16.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    onBandcampLoginClick: () -> Unit,
    onYouTubeMusicLoginClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showRemoveDownloadsDialog by rememberSaveable { mutableStateOf(false) }
    var showFormatSheet by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    SettingsScreenSnackbars(
        state = state,
        snackbarHostState = snackbarHostState,
        onClearSignOutSuccess = viewModel::clearSignOutSuccess,
        onClearYtmSignOutSuccess = viewModel::clearYtmSignOutSuccess,
        onClearFolderMigrationMessage = viewModel::clearFolderMigrationMessage,
        onClearFolderMigrationError = viewModel::clearFolderMigrationError,
        onClearSearchHistoryClearedMessage = viewModel::clearSearchHistoryClearedMessage,
        onClearUpdateMessage = viewModel::clearUpdateMessage,
    )

    if (showRemoveDownloadsDialog) {
        SettingsRemoveDownloadsDialog(
            onConfirm = {
                viewModel.removeAllDownloads()
                showRemoveDownloadsDialog = false
            },
            onDismiss = { showRemoveDownloadsDialog = false },
        )
    }

    if (showFormatSheet) {
        SettingsDownloadFormatSheet(
            downloadFormat = state.downloadFormat,
            onSelect = { key ->
                viewModel.setDownloadFormat(key)
                showFormatSheet = false
            },
            onDismiss = { showFormatSheet = false },
        )
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0),
    ) { scaffoldPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            SettingsScreenList(
                state = state,
                snackbarHostState = snackbarHostState,
                onBandcampLoginClick = onBandcampLoginClick,
                onYouTubeMusicLoginClick = onYouTubeMusicLoginClick,
                onShowRemoveDownloads = { showRemoveDownloadsDialog = true },
                onShowFormatSheet = { showFormatSheet = true },
                onSourcesAction = { handleSettingsSourcesAction(viewModel, it) },
                onStorageAction = { handleSettingsStorageAction(viewModel, it) },
                onAppearanceAction = { handleSettingsAppearanceAction(viewModel, it) },
                onSignOutBandcamp = viewModel::signOutBandcamp,
                onSignOutYouTubeMusic = viewModel::signOutYouTubeMusic,
                onSetSaveDataOnMetered = viewModel::setSaveDataOnMetered,
                onSetProgressiveDownload = viewModel::setProgressiveDownload,
                onSetSeamlessQualityUpgrade = viewModel::setSeamlessQualityUpgrade,
                onSetShowInlineVolumeSlider = viewModel::setShowInlineVolumeSlider,
                onSetShowVolumeButton = viewModel::setShowVolumeButton,
                onSetKeepScreenOnInApp = viewModel::setKeepScreenOnInApp,
                onSetKeepScreenOnWhilePlaying = viewModel::setKeepScreenOnWhilePlaying,
                onSetSearchHistoryEnabled = viewModel::setSearchHistoryEnabled,
                onSetSearchHistorySource = viewModel::setSearchHistorySource,
                onClearAllSearchHistory = viewModel::clearAllSearchHistory,
                onCheckForAppUpdate = viewModel::checkForAppUpdate,
                onSetAutoUpdateCheckEnabled = viewModel::setAutoUpdateCheckEnabled,
                onSetAlbumCoverLongPressCarousel = viewModel::setAlbumCoverLongPressCarousel,
            )
        }

        // Update flow: Available -> confirm dialog; Downloading -> progress dialog
        AppUpdateDialog(
            state = state.updateState,
            onConfirmDownload = viewModel::confirmAppUpdate,
            onDismiss = viewModel::dismissAppUpdate,
        )

        if (state.folderMigrationInProgress) {
            com.dustvalve.next.android.ui.components.LoadingOverlay(
                title = stringResource(R.string.settings_dedicated_folder_migrating),
                progress = state.folderMigrationProgress,
                message = state.folderMigrationMessage?.asString(),
            )
        }
    }
}

@Composable
private fun SettingsScreenSnackbars(
    state: SettingsUiState,
    snackbarHostState: SnackbarHostState,
    onClearSignOutSuccess: () -> Unit,
    onClearYtmSignOutSuccess: () -> Unit,
    onClearFolderMigrationMessage: () -> Unit,
    onClearFolderMigrationError: () -> Unit,
    onClearSearchHistoryClearedMessage: () -> Unit,
    onClearUpdateMessage: () -> Unit,
) {
    val clearSignOutSuccess by rememberUpdatedState(onClearSignOutSuccess)
    val clearYtmSignOutSuccess by rememberUpdatedState(onClearYtmSignOutSuccess)
    val clearFolderMigrationMessage by rememberUpdatedState(onClearFolderMigrationMessage)
    val clearFolderMigrationError by rememberUpdatedState(onClearFolderMigrationError)
    val clearSearchHistoryClearedMessage by rememberUpdatedState(onClearSearchHistoryClearedMessage)
    val clearUpdateMessage by rememberUpdatedState(onClearUpdateMessage)

    val bandcampDisconnectedMsg = stringResource(R.string.settings_bandcamp_disconnected)
    LaunchedEffect(state.bandcampSignOutSuccess) {
        if (state.bandcampSignOutSuccess) {
            try {
                snackbarHostState.showSnackbar(bandcampDisconnectedMsg)
            } finally {
                clearSignOutSuccess()
            }
        }
    }

    val ytmDisconnectedMsg = stringResource(R.string.settings_youtube_disconnected)
    LaunchedEffect(state.ytmSignOutSuccess) {
        if (state.ytmSignOutSuccess) {
            try {
                snackbarHostState.showSnackbar(ytmDisconnectedMsg)
            } finally {
                clearYtmSignOutSuccess()
            }
        }
    }

    val folderMigrationText = state.folderMigrationMessage?.asString()
    LaunchedEffect(folderMigrationText) {
        folderMigrationText?.let { message ->
            try {
                snackbarHostState.showSnackbar(message)
            } finally {
                clearFolderMigrationMessage()
            }
        }
    }

    val folderMigrationErrorText = state.folderMigrationError?.asString()
    LaunchedEffect(folderMigrationErrorText) {
        folderMigrationErrorText?.let { message ->
            try {
                snackbarHostState.showSnackbar(message)
            } finally {
                clearFolderMigrationError()
            }
        }
    }

    val historyClearedText = state.searchHistoryClearedMessage?.asString()
    LaunchedEffect(historyClearedText) {
        historyClearedText?.let { message ->
            try {
                snackbarHostState.showSnackbar(message)
            } finally {
                clearSearchHistoryClearedMessage()
            }
        }
    }

    val updateText = state.updateMessage?.asString()
    LaunchedEffect(updateText) {
        updateText?.let { message ->
            try {
                snackbarHostState.showSnackbar(message)
            } finally {
                clearUpdateMessage()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsRemoveDownloadsDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_remove_downloads_title)) },
        text = { Text(stringResource(R.string.settings_remove_downloads_text)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.common_action_remove))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.common_action_cancel))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsDownloadFormatSheet(downloadFormat: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val formatHaptic = LocalHapticFeedback.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden),
        sheetMaxWidth = LocalAdaptiveLayoutInfo.current.sheetMaxWidth,
    ) {
        Text(
            text = stringResource(R.string.settings_download_format),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        AudioFormat.DOWNLOADABLE.forEach { format ->
            val isSelected = format.key == downloadFormat
            ListItem(
                trailingContent = {
                    if (isSelected) {
                        Icon(
                            painter = painterResource(R.drawable.ic_check),
                            contentDescription = stringResource(R.string.common_cd_selected),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        formatHaptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSelect(format.key)
                    },
            ) {
                Text(stringResource(format.displayNameRes))
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SettingsScreenList(
    state: SettingsUiState,
    snackbarHostState: SnackbarHostState,
    onBandcampLoginClick: () -> Unit,
    onYouTubeMusicLoginClick: () -> Unit,
    onShowRemoveDownloads: () -> Unit,
    onShowFormatSheet: () -> Unit,
    onSourcesAction: (SettingsSourcesAction) -> Unit,
    onStorageAction: (SettingsStorageAction) -> Unit,
    onAppearanceAction: (SettingsAppearanceAction) -> Unit,
    onSignOutBandcamp: () -> Unit,
    onSignOutYouTubeMusic: () -> Unit,
    onSetSaveDataOnMetered: (Boolean) -> Unit,
    onSetProgressiveDownload: (Boolean) -> Unit,
    onSetSeamlessQualityUpgrade: (Boolean) -> Unit,
    onSetShowInlineVolumeSlider: (Boolean) -> Unit,
    onSetShowVolumeButton: (Boolean) -> Unit,
    onSetKeepScreenOnInApp: (Boolean) -> Unit,
    onSetKeepScreenOnWhilePlaying: (Boolean) -> Unit,
    onSetSearchHistoryEnabled: (Boolean) -> Unit,
    onSetSearchHistorySource: (String, Boolean) -> Unit,
    onClearAllSearchHistory: () -> Unit,
    onCheckForAppUpdate: () -> Unit,
    onSetAutoUpdateCheckEnabled: (Boolean) -> Unit,
    onSetAlbumCoverLongPressCarousel: (Boolean) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .adaptiveContentWidth()
            .testTag(com.dustvalve.next.android.ui.TestTags.SETTINGS_LIST),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 10.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineMediumEmphasized,
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)
                    .animateItem(),
            )
        }
        item {
            SettingsSourcesSection(
                state = state,
                snackbarHostState = snackbarHostState,
                onAction = onSourcesAction,
            )
        }
        item {
            SettingsConnectionsSection(
                state = state,
                onBandcampLoginClick = onBandcampLoginClick,
                onYouTubeMusicLoginClick = onYouTubeMusicLoginClick,
                onSignOutBandcamp = onSignOutBandcamp,
                onSignOutYouTubeMusic = onSignOutYouTubeMusic,
            )
        }
        item {
            SettingsStorageSection(
                state = state,
                onRemoveAllDownloadsClick = onShowRemoveDownloads,
                onAction = onStorageAction,
            )
        }
        item {
            SettingsAudioQualitySection(
                state = state,
                onShowFormatSheet = onShowFormatSheet,
                onSetSaveDataOnMetered = onSetSaveDataOnMetered,
                onSetProgressiveDownload = onSetProgressiveDownload,
                onSetSeamlessQualityUpgrade = onSetSeamlessQualityUpgrade,
            )
        }
        item {
            SettingsAppearanceSection(
                state = state,
                onAction = onAppearanceAction,
            )
        }
        item {
            SettingsPlayerSection(
                state = state,
                onSetShowInlineVolumeSlider = onSetShowInlineVolumeSlider,
                onSetShowVolumeButton = onSetShowVolumeButton,
                onSetKeepScreenOnInApp = onSetKeepScreenOnInApp,
                onSetKeepScreenOnWhilePlaying = onSetKeepScreenOnWhilePlaying,
            )
        }
        item {
            SettingsSearchSection(
                state = state,
                onSetSearchHistoryEnabled = onSetSearchHistoryEnabled,
                onSetSearchHistorySource = onSetSearchHistorySource,
                onClearAllSearchHistory = onClearAllSearchHistory,
            )
        }
        item {
            SettingsAboutSection(
                state = state,
                onCheckForAppUpdate = onCheckForAppUpdate,
                onSetAutoUpdateCheckEnabled = onSetAutoUpdateCheckEnabled,
            )
        }
        item {
            SettingsDebugSection(
                albumCoverLongPressCarousel = state.albumCoverLongPressCarousel,
                onSetAlbumCoverLongPressCarousel = onSetAlbumCoverLongPressCarousel,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsSection(title: String, icon: Int, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(AppShapes.SettingsIcon)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        content()
    }
}

/**
 * Standard settings toggle row: optional leading icon, label column
 * (title + optional description), Switch pinned to the card edge.
 * [subRow] switches to the indented dependent-setting variant with the
 * smaller type scale. When [enabled] is false the texts dim along with
 * the switch.
 */
@Composable
internal fun SettingsToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
    icon: Int? = null,
    subRow: Boolean = false,
    switchTag: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (subRow) Modifier.padding(start = SUB_TOGGLE_INDENT) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        val disabledAlpha = 0.38f
        Column(modifier = Modifier.weight(1f).padding(end = TOGGLE_LABEL_END_GAP)) {
            Text(
                text = title,
                style = if (subRow) {
                    MaterialTheme.typography.bodyMedium
                } else {
                    MaterialTheme.typography.titleSmall
                },
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = disabledAlpha)
                },
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = disabledAlpha)
                    },
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = if (switchTag != null) Modifier.testTag(switchTag) else Modifier,
        )
    }
}

/**
 * Canonical dependent setting: an indented toggle row that animates in
 * below its parent when [visible] flips on.
 */
@Composable
internal fun SettingsSubToggle(
    visible: Boolean,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String? = null,
    enabled: Boolean = true,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        SettingsToggleRow(
            title = title,
            checked = checked,
            onCheckedChange = onCheckedChange,
            description = description,
            enabled = enabled,
            subRow = true,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
internal fun SearchHistorySourceRow(labelRes: Int, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * Sub-row under the download-notifications toggle that appears only when the
 * per-app "Live Updates" permission is off (so the status-bar download chip
 * cannot show). Tapping the button deep-links to the system toggle. Re-checks
 * on every ON_RESUME so it disappears as soon as the user grants it.
 */
@Composable
internal fun LiveUpdatesPromptRow() {
    val context = LocalContext.current
    var canPost by remember { mutableStateOf(canPostPromotedNotifications(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canPost = canPostPromotedNotifications(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    AnimatedVisibility(
        visible = !canPost,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, start = SUB_TOGGLE_INDENT),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = TOGGLE_LABEL_END_GAP)) {
                Text(
                    text = stringResource(R.string.settings_live_updates_title),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.settings_live_updates_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(
                onClick = { openLiveUpdatesSettings(context) },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.settings_live_updates_action))
            }
        }
    }
}

@Suppress("TooGenericExceptionCaught", "SwallowedException")
private fun canPostPromotedNotifications(context: Context): Boolean = try {
    context.getSystemService(NotificationManager::class.java).canPostPromotedNotifications()
} catch (e: Throwable) {
    // API absent (pre-QPR1) - don't nag with a prompt that leads nowhere.
    true
}

@Suppress("TooGenericExceptionCaught", "SwallowedException")
private fun openLiveUpdatesSettings(context: Context) {
    // There is no dedicated promoted-notifications settings action in API 37;
    // the per-app "Live Updates" toggle lives in the app's notification
    // settings. Fall back to the app details page if that screen is unavailable.
    try {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (e: Exception) {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                "package:${context.packageName}".toUri(),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
