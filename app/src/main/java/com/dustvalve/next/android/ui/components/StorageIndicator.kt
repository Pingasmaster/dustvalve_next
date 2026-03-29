package com.dustvalve.next.android.ui.components

import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dustvalve.next.android.R
import com.dustvalve.next.android.domain.model.CacheInfo
import com.dustvalve.next.android.util.StorageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StorageIndicator(
    cacheInfo: CacheInfo,
    modifier: Modifier = Modifier,
) {
    val progress = (cacheInfo.usagePercent / 100f).coerceIn(0f, 1f)
    val targetColor = when {
        cacheInfo.usagePercent >= 95f -> MaterialTheme.colorScheme.error
        cacheInfo.usagePercent >= 85f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    val color by animateColorAsState(
        targetValue = targetColor,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "storageColor",
    )
    var freeBytes by remember { mutableLongStateOf(0L) }
    LaunchedEffect(cacheInfo) {
        freeBytes = withContext(Dispatchers.IO) {
            val stat = StatFs(Environment.getDataDirectory().path)
            stat.availableBytes
        }
    }

    val cacheSizeBytes = (cacheInfo.totalSizeBytes - cacheInfo.downloadSizeBytes).coerceAtLeast(0)

    Column(modifier = modifier.fillMaxWidth()) {
        LinearWavyProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.storage_info, StorageUtils.formatFileSize(cacheInfo.downloadSizeBytes), StorageUtils.formatFileSize(cacheSizeBytes), StorageUtils.formatFileSize(freeBytes)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${cacheInfo.usagePercent.toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = color,
            )
        }
    }
}
