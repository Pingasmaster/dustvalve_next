package com.dustvalve.next.android.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dustvalve.next.android.R
import com.dustvalve.next.android.player.BluetoothStabilityMode
import com.dustvalve.next.android.ui.components.AppButtonGroup
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsBluetoothStabilitySection(state: SettingsUiState, onAction: (SettingsBluetoothStabilityAction) -> Unit) {
    val mode = remember(state.bluetoothStabilityMode) {
        BluetoothStabilityMode.fromStorage(state.bluetoothStabilityMode)
    }
    var showEnableDialog by remember { mutableStateOf(false) }
    var showFloatWarning by remember { mutableStateOf(false) }

    BluetoothStabilityDialogs(
        showEnableDialog = showEnableDialog,
        showFloatWarning = showFloatWarning,
        onAction = onAction,
        onDismissEnable = { showEnableDialog = false },
        onDismissFloatWarning = { showFloatWarning = false },
    )

    SettingsSection(
        title = stringResource(R.string.settings_section_bluetooth_stability),
        icon = R.drawable.ic_bluetooth,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingsToggleRow(
                    title = stringResource(R.string.settings_bt_stability),
                    checked = mode.isEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            showEnableDialog = true
                        } else {
                            onAction(SettingsBluetoothStabilityAction.Disable)
                        }
                    },
                    extras = SettingsToggleExtras(
                        description = stringResource(R.string.settings_bt_stability_desc),
                    ),
                )

                AnimatedVisibility(
                    visible = mode.isEnabled,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    BluetoothStabilityEnabledControls(
                        state = state,
                        mode = mode,
                        onAction = onAction,
                        onRequestFloatWarning = { showFloatWarning = true },
                    )
                }
            }
        }
    }
}

@Composable
private fun BluetoothStabilityDialogs(
    showEnableDialog: Boolean,
    showFloatWarning: Boolean,
    onAction: (SettingsBluetoothStabilityAction) -> Unit,
    onDismissEnable: () -> Unit,
    onDismissFloatWarning: () -> Unit,
) {
    // Single root so MultipleEmitters does not fire when both dialogs are up.
    Column {
        if (showEnableDialog) {
            BluetoothStabilityEnableDialog(
                onConfirm = {
                    onAction(SettingsBluetoothStabilityAction.Enable(BluetoothStabilityMode.NORMAL))
                    onDismissEnable()
                },
                onDismiss = onDismissEnable,
            )
        }
        if (showFloatWarning) {
            BluetoothStabilityQualityWarningDialog(
                title = stringResource(R.string.settings_bt_stability_float_warning_title),
                text = stringResource(R.string.settings_bt_stability_float_warning_text),
                onConfirm = {
                    onAction(SettingsBluetoothStabilityAction.SetDisableFloatOutput(true))
                    onDismissFloatWarning()
                },
                onDismiss = onDismissFloatWarning,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BluetoothStabilityEnabledControls(
    state: SettingsUiState,
    mode: BluetoothStabilityMode,
    onAction: (SettingsBluetoothStabilityAction) -> Unit,
    onRequestFloatWarning: () -> Unit,
) {
    Column {
        Spacer(modifier = Modifier.height(16.dp))
        BluetoothStabilityProfilePicker(mode = mode, onAction = onAction)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.settings_bt_stability_applies_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        BluetoothStabilityPcmSlider(pcmBufferMs = state.bluetoothPcmBufferMs, onAction = onAction)
        Spacer(modifier = Modifier.height(16.dp))
        SettingsToggleRow(
            title = stringResource(R.string.settings_bt_stability_exo_boost),
            checked = state.bluetoothExoBufferBoost,
            onCheckedChange = { onAction(SettingsBluetoothStabilityAction.SetExoBufferBoost(it)) },
            extras = SettingsToggleExtras(
                description = stringResource(R.string.settings_bt_stability_exo_boost_desc),
                subRow = true,
            ),
        )
        Spacer(modifier = Modifier.height(16.dp))
        SettingsToggleRow(
            title = stringResource(R.string.settings_bt_stability_pause_downloads),
            checked = state.bluetoothPauseDownloadsWhilePlaying,
            onCheckedChange = {
                onAction(SettingsBluetoothStabilityAction.SetPauseDownloadsWhilePlaying(it))
            },
            extras = SettingsToggleExtras(
                description = stringResource(R.string.settings_bt_stability_pause_downloads_desc),
                subRow = true,
            ),
        )
        AnimatedVisibility(
            visible = mode.isExtreme,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            BluetoothStabilityExtremeOptions(
                disableFloatOutput = state.bluetoothDisableFloatOutput,
                onAction = onAction,
                onRequestFloatWarning = onRequestFloatWarning,
            )
        }
    }
}

@Composable
private fun BluetoothStabilityExtremeOptions(
    disableFloatOutput: Boolean,
    onAction: (SettingsBluetoothStabilityAction) -> Unit,
    onRequestFloatWarning: () -> Unit,
) {
    Column {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.settings_bt_stability_extreme_options),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(modifier = Modifier.height(8.dp))
        SettingsToggleRow(
            title = stringResource(R.string.settings_bt_stability_disable_float),
            checked = disableFloatOutput,
            onCheckedChange = { enabled ->
                if (enabled) {
                    onRequestFloatWarning()
                } else {
                    onAction(SettingsBluetoothStabilityAction.SetDisableFloatOutput(false))
                }
            },
            extras = SettingsToggleExtras(
                description = stringResource(R.string.settings_bt_stability_disable_float_desc),
                subRow = true,
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BluetoothStabilityProfilePicker(mode: BluetoothStabilityMode, onAction: (SettingsBluetoothStabilityAction) -> Unit) {
    Column {
        Text(
            text = stringResource(R.string.settings_bt_stability_profile),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(modifier = Modifier.height(8.dp))
        val profiles = listOf(BluetoothStabilityMode.NORMAL, BluetoothStabilityMode.EXTREME)
        val labels = listOf(
            stringResource(R.string.settings_bt_stability_profile_normal),
            stringResource(R.string.settings_bt_stability_profile_extreme),
        )
        AppButtonGroup(
            overflowIndicator = { _ -> },
            modifier = Modifier.fillMaxWidth(),
        ) {
            profiles.forEachIndexed { i, profile ->
                customItem(
                    buttonGroupContent = {
                        ToggleButton(
                            checked = mode == profile,
                            onCheckedChange = {
                                if (it) {
                                    onAction(SettingsBluetoothStabilityAction.SetProfile(profile))
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shapes = when (i) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                else -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            },
                        ) {
                            Text(labels[i])
                        }
                    },
                    menuContent = {},
                )
            }
        }
    }
}

@Composable
private fun BluetoothStabilityPcmSlider(pcmBufferMs: Int, onAction: (SettingsBluetoothStabilityAction) -> Unit) {
    val steps = BluetoothStabilityMode.PCM_BUFFER_STEPS_MS
    var sizeIndex by remember(pcmBufferMs) {
        mutableIntStateOf(steps.indexOf(pcmBufferMs).let { if (it >= 0) it else 2 })
    }
    Column {
        Text(
            text = stringResource(R.string.settings_bt_stability_pcm, steps[sizeIndex]),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Slider(
            value = sizeIndex.toFloat(),
            onValueChange = {
                sizeIndex = it.roundToInt().coerceIn(0, steps.lastIndex)
            },
            onValueChangeFinished = {
                onAction(SettingsBluetoothStabilityAction.SetPcmBufferMs(steps[sizeIndex]))
            },
            valueRange = 0f..(steps.lastIndex).toFloat(),
            steps = steps.size - 2,
        )
        Text(
            text = stringResource(R.string.settings_bt_stability_pcm_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BluetoothStabilityEnableDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_bt_stability_enable_title)) },
        text = { Text(stringResource(R.string.settings_bt_stability_enable_text)) },
        confirmButton = {
            Button(onClick = onConfirm, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.common_action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.common_action_cancel))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BluetoothStabilityQualityWarningDialog(title: String, text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.settings_bt_stability_enable_anyway))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.common_action_cancel))
            }
        },
    )
}
