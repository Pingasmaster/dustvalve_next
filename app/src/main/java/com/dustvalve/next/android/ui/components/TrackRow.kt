package com.dustvalve.next.android.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.dustvalve.next.android.R
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.util.TimeUtils

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TrackRow(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    onFavoriteClick: (() -> Unit)? = null,
    onDownloadClick: (() -> Unit)? = null,
    isDownloading: Boolean = false,
    isDownloaded: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "pressScale",
    )

    val containerColor by animateColorAsState(
        targetValue = if (isPlaying) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else {
            Color.Transparent
        },
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "containerColor",
    )
    val headlineColor by animateColorAsState(
        targetValue = if (isPlaying) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "headlineColor",
    )

    ListItem(
        modifier = modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        colors = ListItemDefaults.colors(
            containerColor = containerColor,
        ),
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.small),
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
                style = if (isPlaying) MaterialTheme.typography.bodyLargeEmphasized
                    else MaterialTheme.typography.bodyLarge,
                color = headlineColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = TimeUtils.formatDuration(track.duration),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = if (onFavoriteClick != null || onDownloadClick != null) {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onFavoriteClick != null) {
                        IconButton(onClick = onFavoriteClick) {
                            Icon(
                                painter = painterResource(if (track.isFavorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border),
                                contentDescription = stringResource(if (track.isFavorite) R.string.player_cd_remove_from_favorites else R.string.player_cd_add_to_favorites),
                                tint = if (track.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    if (onDownloadClick != null) {
                        FilledTonalIconButton(
                            onClick = onDownloadClick,
                            enabled = !isDownloading,
                            modifier = Modifier.size(40.dp),
                        ) {
                            if (isDownloading) {
                                CircularWavyProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                )
                            } else {
                                Icon(
                                    painter = painterResource(if (isDownloaded) R.drawable.ic_download_done else R.drawable.ic_download),
                                    contentDescription = stringResource(if (isDownloaded) R.string.player_cd_delete_download else R.string.player_cd_download_track),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        } else {
            null
        },
    )
}
