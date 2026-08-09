package com.dustvalve.next.android.ui.screens.soundcloud

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.dustvalve.next.android.R
import com.dustvalve.next.android.domain.model.SoundCloudHomeFeed
import com.dustvalve.next.android.domain.model.SoundCloudShelf
import com.dustvalve.next.android.domain.model.SoundCloudShelfItem
import com.dustvalve.next.android.domain.model.SoundCloudShelfKind
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.ui.theme.AppShapes

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SoundCloudHomeContent(
    carouselItemWidth: Dp,
    state: SoundCloudUiState,
    onRetry: () -> Unit,
    onPlayTrack: (Track) -> Unit,
    onShelfItemClick: (SoundCloudShelfItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.feed == null && state.homeError != null -> {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = state.homeError.asString(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(16.dp))
                FilledTonalButton(
                    onClick = onRetry,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.soundcloud_home_retry))
                }
            }
        }

        state.feed == null && state.isHomeLoading -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ContainedLoadingIndicator()
            }
        }

        state.feed != null -> {
            Column(modifier = modifier.fillMaxSize()) {
                val homeError = state.homeError
                if (homeError != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = homeError.asString(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FilledTonalButton(
                            onClick = onRetry,
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Text(stringResource(R.string.soundcloud_home_retry))
                        }
                    }
                }
                SoundCloudFeed(
                    carouselItemWidth = carouselItemWidth,
                    feed = state.feed,
                    isRefreshing = state.isHomeLoading,
                    onPlayTrack = onPlayTrack,
                    onShelfItemClick = onShelfItemClick,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        else -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.soundcloud_home_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SoundCloudFeed(
    carouselItemWidth: Dp,
    feed: SoundCloudHomeFeed,
    isRefreshing: Boolean,
    onPlayTrack: (Track) -> Unit,
    onShelfItemClick: (SoundCloudShelfItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        if (isRefreshing) {
            item(key = "sc_refresh") {
                LinearWavyProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
        }

        if (feed.trending.isNotEmpty()) {
            item(key = "sc_trending_header") {
                Text(
                    text = stringResource(R.string.soundcloud_home_trending),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            item(key = "sc_trending") {
                TrendingCarousel(
                    carouselItemWidth = carouselItemWidth,
                    tracks = feed.trending,
                    onPlayTrack = onPlayTrack,
                )
            }
        }

        if (feed.shelves.isNotEmpty()) {
            item(key = "sc_discover_header") {
                Text(
                    text = stringResource(R.string.soundcloud_home_discover),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }

        feed.shelves.forEachIndexed { index, shelf ->
            if (shelf.items.isNotEmpty()) {
                item(key = "sc_shelf_${index}_title") {
                    Text(
                        text = shelf.title,
                        style = MaterialTheme.typography.titleSmallEmphasized,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                item(key = "sc_shelf_$index") {
                    ShelfRow(shelf = shelf, onItemClick = onShelfItemClick)
                }
            }
        }

        if (feed.trending.isEmpty() && feed.shelves.all { it.items.isEmpty() } && !isRefreshing) {
            item(key = "sc_empty") {
                Text(
                    text = stringResource(R.string.soundcloud_home_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrendingCarousel(carouselItemWidth: Dp, tracks: List<Track>, onPlayTrack: (Track) -> Unit) {
    val carouselState = rememberCarouselState { tracks.size }
    HorizontalMultiBrowseCarousel(
        state = carouselState,
        preferredItemWidth = carouselItemWidth,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        itemSpacing = 8.dp,
    ) { index ->
        val track = tracks[index]
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .maskClip(MaterialTheme.shapes.large)
                .clickable { onPlayTrack(track) },
        ) {
            AsyncImage(
                model = track.artUrl,
                contentDescription = track.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.45f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.75f),
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp),
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ShelfRow(shelf: SoundCloudShelf, onItemClick: (SoundCloudShelfItem) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(shelf.items, key = { it.id + it.url }) { item ->
            Column(
                modifier = Modifier
                    .width(140.dp)
                    .clickable { onItemClick(item) },
            ) {
                val shape = when (item.kind) {
                    SoundCloudShelfKind.USER -> AppShapes.SearchResultArtist
                    else -> MaterialTheme.shapes.medium
                }
                if (item.artUrl != null) {
                    AsyncImage(
                        model = item.artUrl,
                        contentDescription = item.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(shape),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(shape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(
                                when (item.kind) {
                                    SoundCloudShelfKind.USER -> R.drawable.ic_person
                                    SoundCloudShelfKind.TRACK -> R.drawable.ic_music_note
                                    else -> R.drawable.ic_album
                                },
                            ),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.subtitle.isNotBlank()) {
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
