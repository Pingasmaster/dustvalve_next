package com.dustvalve.next.android.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.dustvalve.next.android.domain.model.Playlist
import com.dustvalve.next.android.ui.theme.AppShapes
import com.dustvalve.next.android.ui.theme.resolvePlaylistShape

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlaylistListItem(
    playlist: Playlist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isFullyDownloaded: Boolean = false,
    onLongClick: () -> Unit = {},
    onMoreClick: (() -> Unit)? = null,
) {
    val thumbnailShape = when (playlist.systemType) {
        Playlist.SystemPlaylistType.FAVORITES -> AppShapes.PlaylistFavorites
        Playlist.SystemPlaylistType.DOWNLOADS -> AppShapes.PlaylistDownloads
        Playlist.SystemPlaylistType.RECENT -> AppShapes.PlaylistRecent
        Playlist.SystemPlaylistType.COLLECTION -> AppShapes.PlaylistCollection
        else -> resolvePlaylistShape(playlist.shapeKey)
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "pressScale",
    )

    ListItem(
        modifier = modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
        ),
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(thumbnailShape)
                    .background(
                        when {
                            playlist.isSystem -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.secondaryContainer
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (playlist.iconUrl != null) {
                    AsyncImage(
                        model = playlist.iconUrl,
                        contentDescription = playlist.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(56.dp),
                    )
                } else {
                    Icon(
                        imageVector = getPlaylistIcon(playlist),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = when {
                            playlist.isSystem -> MaterialTheme.colorScheme.onPrimaryContainer
                            else -> MaterialTheme.colorScheme.onSecondaryContainer
                        },
                    )
                }
            }
        },
        headlineContent = {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            val subtitle = when {
                playlist.isSystem -> "Auto playlist"
                playlist.trackCount == 1 -> "1 song"
                else -> "${playlist.trackCount} songs"
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (playlist.isPinned) {
                    Icon(
                        imageVector = Icons.Rounded.PushPin,
                        contentDescription = "Pinned",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                if (isFullyDownloaded) {
                    Icon(
                        imageVector = Icons.Rounded.DownloadDone,
                        contentDescription = "All tracks downloaded",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        },
        trailingContent = if (onMoreClick != null) {
            {
                IconButton(onClick = onMoreClick) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "More options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            null
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun getPlaylistIcon(playlist: Playlist): ImageVector {
    return when (playlist.systemType) {
        Playlist.SystemPlaylistType.FAVORITES -> Icons.Rounded.Favorite
        Playlist.SystemPlaylistType.DOWNLOADS -> Icons.Rounded.CloudDownload
        Playlist.SystemPlaylistType.RECENT -> Icons.Rounded.History
        Playlist.SystemPlaylistType.COLLECTION -> Icons.Rounded.LibraryMusic
        else -> Icons.Rounded.MusicNote
    }
}
