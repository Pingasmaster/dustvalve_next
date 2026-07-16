package com.dustvalve.next.android.ui.screens.folder

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dustvalve.next.android.R
import com.dustvalve.next.android.ui.components.EmptyState

/**
 * Blocks the app on cold start when the user's dedicated folder can't be
 * reached (e.g. SD card unmounted, permission revoked, user deleted the
 * folder). Two actions: re-pick the folder, or disable the feature.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DedicatedFolderErrorScreen(onLocateFolder: (Uri) -> Unit, onTurnOff: () -> Unit, modifier: Modifier = Modifier) {
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri != null) onLocateFolder(uri)
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EmptyState(
                icon = R.drawable.ic_folder_open,
                title = stringResource(R.string.settings_dedicated_folder_unreachable_title),
                subtitle = stringResource(R.string.settings_dedicated_folder_unreachable_text),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { folderPicker.launch(null) },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.settings_dedicated_folder_locate))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onTurnOff,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.settings_dedicated_folder_turn_off))
                    }
                }
            }
        }
    }
}
