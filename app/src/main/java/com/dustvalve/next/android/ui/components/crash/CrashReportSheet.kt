package com.dustvalve.next.android.ui.components.crash

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dustvalve.next.android.R
import com.dustvalve.next.android.crash.CrashReportManager

/**
 * Post-crash prompt, hosted by MainActivity when [CrashReportManager]
 * detects that the previous process died from a crash / ANR (never from the
 * user force-closing the app).
 *
 * Everything here is opt-in by design: the sheet explains that the log
 * lives only on this device and both share paths are explicit gestures.
 * Dismissing deletes the stored log.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CrashReportSheet(
    state: CrashReportManager.PromptState,
    onShareLog: () -> Unit,
    onOpenIssue: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state !is CrashReportManager.PromptState.Pending) return

    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.crash_report_title),
                // titleMedium + 4dp - the app-wide sheet header convention.
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            Spacer(Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.crash_report_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.crash_report_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))

            Button(
                onClick = onShareLog,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_share),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.crash_report_share_action),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Spacer(Modifier.height(8.dp))

            FilledTonalButton(
                onClick = onOpenIssue,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_bug_report),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.crash_report_github_action),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Spacer(Modifier.height(8.dp))

            TextButton(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.crash_report_dismiss_action))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
