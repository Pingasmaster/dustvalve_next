package com.dustvalve.next.android.ui.components.sheet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dustvalve.next.android.R
import com.dustvalve.next.android.domain.model.SearchResult
import com.dustvalve.next.android.domain.model.SearchResultType

enum class RemoteItemKind { TRACK, ALBUM, PLAYLIST, ARTIST }

fun SearchResultType.toRemoteKind(): RemoteItemKind? = when (this) {
    SearchResultType.TRACK,
    SearchResultType.YOUTUBE_TRACK -> RemoteItemKind.TRACK

    SearchResultType.ALBUM,
    SearchResultType.YOUTUBE_ALBUM -> RemoteItemKind.ALBUM

    SearchResultType.YOUTUBE_PLAYLIST -> RemoteItemKind.PLAYLIST

    SearchResultType.ARTIST,
    SearchResultType.YOUTUBE_ARTIST -> RemoteItemKind.ARTIST

    SearchResultType.LOCAL_TRACK -> null
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RemoteResultActionSheet(
    result: SearchResult,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onPlayAll: () -> Unit,
    onEnqueueAll: () -> Unit,
    onShare: () -> Unit,
    onOpenInBrowser: () -> Unit,
) {
    val kind = result.type.toRemoteKind() ?: return

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = result.name,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        result.artist?.takeIf { it.isNotBlank() }?.let { artist ->
            Text(
                text = artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        when (kind) {
            RemoteItemKind.TRACK -> {
                ActionRow(
                    labelRes = R.string.common_play_next,
                    iconRes = R.drawable.ic_skip_next,
                    onClick = onPlayNext,
                )
                ActionRow(
                    labelRes = R.string.common_add_to_queue,
                    iconRes = R.drawable.ic_queue_music,
                    onClick = onAddToQueue,
                )
                ActionRow(
                    labelRes = R.string.common_add_to_playlist,
                    iconRes = R.drawable.ic_playlist_add,
                    onClick = onAddToPlaylist,
                )
                ActionRow(
                    labelRes = R.string.common_share,
                    iconRes = R.drawable.ic_share,
                    onClick = onShare,
                )
                ActionRow(
                    labelRes = R.string.common_open_in_browser,
                    iconRes = R.drawable.ic_open_in_new,
                    onClick = onOpenInBrowser,
                )
            }
            RemoteItemKind.ALBUM, RemoteItemKind.PLAYLIST -> {
                ActionRow(
                    labelRes = R.string.common_play_all,
                    iconRes = R.drawable.ic_playlist_play,
                    onClick = onPlayAll,
                )
                ActionRow(
                    labelRes = R.string.common_enqueue_all,
                    iconRes = R.drawable.ic_queue_music,
                    onClick = onEnqueueAll,
                )
                ActionRow(
                    labelRes = R.string.common_share,
                    iconRes = R.drawable.ic_share,
                    onClick = onShare,
                )
                ActionRow(
                    labelRes = R.string.common_open_in_browser,
                    iconRes = R.drawable.ic_open_in_new,
                    onClick = onOpenInBrowser,
                )
            }
            RemoteItemKind.ARTIST -> {
                ActionRow(
                    labelRes = R.string.common_share,
                    iconRes = R.drawable.ic_share,
                    onClick = onShare,
                )
                ActionRow(
                    labelRes = R.string.common_open_in_browser,
                    iconRes = R.drawable.ic_open_in_new,
                    onClick = onOpenInBrowser,
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
private fun ActionRow(
    labelRes: Int,
    iconRes: Int,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(stringResource(labelRes)) },
        leadingContent = {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
