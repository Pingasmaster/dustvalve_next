package com.dustvalve.next.android.ui.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.res.stringResource
import com.dustvalve.next.android.R
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UpdateDialog(
    state: UpdateUiState,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
) {
    val update = state.availableUpdate ?: return

    AlertDialog(
        onDismissRequest = {
            if (!state.isDownloading) onDismiss()
        },
        title = {
            Text(stringResource(R.string.update_title))
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.update_version_available, update.versionName),
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (update.releaseNotes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = update.releaseNotes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.isDownloading) {
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearWavyProgressIndicator(
                        progress = { state.downloadProgress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.update_download_percent, (state.downloadProgress * 100).toInt()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.downloadError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.downloadError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            when {
                state.readyToInstall -> {
                    TextButton(onClick = onInstall) {
                        Text(stringResource(R.string.update_install))
                    }
                }
                state.isDownloading -> {}
                state.downloadError != null -> {
                    TextButton(onClick = onDownload) {
                        Text(stringResource(R.string.common_action_retry))
                    }
                }
                else -> {
                    TextButton(onClick = onDownload) {
                        Text(stringResource(R.string.update_download))
                    }
                }
            }
        },
        dismissButton = {
            if (!state.isDownloading) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(if (state.readyToInstall) R.string.update_later else R.string.update_not_now))
                }
            }
        },
    )
}
