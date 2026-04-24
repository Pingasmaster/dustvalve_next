package com.dustvalve.next.android.ui.components.lists

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.dustvalve.next.android.R
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.ui.components.TrackArtPlaceholder
import com.dustvalve.next.android.ui.theme.AppShapes
import com.dustvalve.next.android.util.TimeUtils

/**
 * Unified music row for the whole app.
 *
 * Fixed visuals so every list looks the same: 48 dp square art
 * (`AppShapes.SearchResultTrack`), the now-playing overlay, 0.97× press scale via
 * `motionScheme.fastSpatialSpec`, and `combinedClickable` + `interactionSource` for
 * M3E ripple + long-press haptics. Trailing actions are opt-in and render in this
 * order when present: favorite ; download ; dragHandle.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MusicRow(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    isPlaying: Boolean = false,
    isCurrentTrack: Boolean = false,
    showFavorite: Boolean = false,
    onFavoriteClick: (() -> Unit)? = null,
    showDownload: Boolean = false,
    onDownloadClick: (() -> Unit)? = null,
    isDownloaded: Boolean = false,
    isDownloading: Boolean = false,
    priceSuffix: String? = null,
    supportingOverride: String? = null,
    dragHandle: (@Composable () -> Unit)? = null,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "musicRowPressScale",
    )

    val headlineColor by animateColorAsState(
        targetValue = if (isCurrentTrack) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "musicRowHeadlineColor",
    )

    val clickModifier = if (onLongClick != null) {
        Modifier.combinedClickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            onClick = onClick,
            onLongClick = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                onLongClick()
            },
        )
    } else {
        Modifier.combinedClickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            onClick = onClick,
        )
    }

    ListItem(
        modifier = modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .then(clickModifier),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(AppShapes.SearchResultTrack),
            ) {
                if (track.artUrl.isNotBlank()) {
                    AsyncImage(
                        model = track.artUrl,
                        contentDescription = track.albumTitle,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    TrackArtPlaceholder(modifier = Modifier.fillMaxSize())
                }
                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_graphic_eq),
                            contentDescription = stringResource(R.string.common_cd_now_playing),
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        },
        headlineContent = {
            Text(
                text = track.title,
                style = if (isCurrentTrack) MaterialTheme.typography.titleMediumEmphasized
                    else MaterialTheme.typography.titleMedium,
                color = headlineColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            val supporting = supportingOverride ?: defaultSupportingText(track, priceSuffix)
            if (supporting.isNotEmpty()) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        trailingContent = trailingContentOrNull(
            showFavorite = showFavorite,
            isFavorite = track.isFavorite,
            onFavoriteClick = onFavoriteClick,
            showDownload = showDownload,
            onDownloadClick = onDownloadClick,
            isDownloaded = isDownloaded,
            isDownloading = isDownloading,
            dragHandle = dragHandle,
        ),
    )
}

private fun defaultSupportingText(track: Track, priceSuffix: String?): String {
    val parts = buildList {
        if (track.artist.isNotBlank()) add(track.artist)
        if (track.albumTitle.isNotBlank()) add(track.albumTitle)
    }
    val base = if (parts.isNotEmpty()) parts.joinToString("  ·  ")
        else TimeUtils.formatDuration(track.duration)
    return if (!priceSuffix.isNullOrBlank()) "$base  ·  $priceSuffix" else base
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun trailingContentOrNull(
    showFavorite: Boolean,
    isFavorite: Boolean,
    onFavoriteClick: (() -> Unit)?,
    showDownload: Boolean,
    onDownloadClick: (() -> Unit)?,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    dragHandle: (@Composable () -> Unit)?,
): (@Composable () -> Unit)? {
    val hasFavorite = showFavorite && onFavoriteClick != null
    val hasDownload = showDownload && onDownloadClick != null
    val hasDragHandle = dragHandle != null
    if (!hasFavorite && !hasDownload && !hasDragHandle) return null

    return {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (hasFavorite) {
                IconButton(onClick = onFavoriteClick, shapes = IconButtonDefaults.shapes()) {
                    Icon(
                        painter = painterResource(
                            if (isFavorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border,
                        ),
                        contentDescription = stringResource(
                            if (isFavorite) R.string.player_cd_remove_from_favorites
                            else R.string.player_cd_add_to_favorites,
                        ),
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            if (hasDownload) {
                FilledTonalIconButton(
                    onClick = onDownloadClick,
                    enabled = !isDownloading,
                    modifier = Modifier.size(40.dp),
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    if (isDownloading) {
                        CircularWavyProgressIndicator(modifier = Modifier.size(16.dp))
                    } else {
                        Icon(
                            painter = painterResource(
                                if (isDownloaded) R.drawable.ic_download_done
                                else R.drawable.ic_download,
                            ),
                            contentDescription = stringResource(
                                if (isDownloaded) R.string.player_cd_delete_download
                                else R.string.player_cd_download_track,
                            ),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            if (hasDragHandle) {
                dragHandle()
            }
        }
    }
}
