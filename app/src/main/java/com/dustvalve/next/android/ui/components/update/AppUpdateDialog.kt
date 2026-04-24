package com.dustvalve.next.android.ui.components.update

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.dustvalve.next.android.R
import com.dustvalve.next.android.update.UpdateUiState

/**
 * Shared dialog for the self-update flow. Rendered both by:
 *
 *  - Settings → About (when the user hits "Search for updates"), and
 *  - MainActivity's cold-start host (once [state] transitions to Available
 *    from the silent check fired by `AppUpdateController.checkSilently()`).
 *
 * The dialog is a no-op while [state] is Idle / Checking, so the same
 * composable is safe to drop in at both surfaces without guards.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppUpdateDialog(
    state: UpdateUiState,
    onConfirmDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        is UpdateUiState.Available -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.settings_update_available_title)) },
                text = {
                    Text(
                        stringResource(
                            R.string.settings_update_available_text,
                            state.versionName,
                        ),
                    )
                },
                confirmButton = {
                    TextButton(onClick = onConfirmDownload, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(R.string.settings_update_download_action))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(R.string.common_action_cancel))
                    }
                },
            )
        }
        is UpdateUiState.Downloading -> {
            AlertDialog(
                onDismissRequest = { /* not dismissable while downloading */ },
                title = {
                    Text(
                        stringResource(
                            R.string.settings_update_downloading,
                            state.versionName,
                        ),
                    )
                },
                text = {
                    if (state.progress != null) {
                        LinearWavyProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                },
                confirmButton = {},
            )
        }
        else -> Unit
    }
}
