package com.dustvalve.next.android.ui.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
            Text("Update available")
        },
        text = {
            Column {
                Text(
                    text = "Version ${update.versionName} is available.",
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
                    LinearProgressIndicator(
                        progress = { state.downloadProgress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${(state.downloadProgress * 100).toInt()}%",
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
                        Text("Install")
                    }
                }
                state.isDownloading -> {}
                state.downloadError != null -> {
                    TextButton(onClick = onDownload) {
                        Text("Retry")
                    }
                }
                else -> {
                    TextButton(onClick = onDownload) {
                        Text("Download")
                    }
                }
            }
        },
        dismissButton = {
            if (!state.isDownloading) {
                TextButton(onClick = onDismiss) {
                    Text(if (state.readyToInstall) "Later" else "Not now")
                }
            }
        },
    )
}
