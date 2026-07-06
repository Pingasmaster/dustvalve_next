package com.dustvalve.next.android.ui.screens.youtube

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.dustvalve.next.android.R
import com.dustvalve.next.android.domain.model.ArtistItem
import com.dustvalve.next.android.domain.model.HeroItem
import com.dustvalve.next.android.domain.model.MoodChip
import com.dustvalve.next.android.domain.model.Shelf
import com.dustvalve.next.android.domain.model.SongItem
import com.dustvalve.next.android.domain.model.TileItem
import com.dustvalve.next.android.domain.model.YouTubeMusicHomeFeed

/**
 * YouTube Music home feed, rebuilt on Material 3 Expressive:
 * an immersive parallax hero, morphing toggle-button mood filters,
 * a paged quick-picks grid, masked-shape tile carousels and an
 * artist spotlight with expressive shape clips.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun YouTubeMusicHome(
    state: YouTubeUiState,
    onChipSelected: (String?) -> Unit,
    onPlaySong: (SongItem) -> Unit,
    onPlayHero: (HeroItem) -> Unit,
    onOpenTile: (TileItem) -> Unit,
    onOpenArtist: (ArtistItem) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.ytmHome == null && state.ytmHomeError != null -> YtmErrorState(
            message = state.ytmHomeError,
            onRetry = onRetry,
            modifier = modifier,
        )

        state.ytmHome != null -> YtmFeed(
            feed = state.ytmHome,
            selectedChipParams = state.ytmSelectedChipParams,
            isRefreshing = state.ytmHomeLoading,
            onChipSelected = onChipSelected,
            onPlaySong = onPlaySong,
            onPlayHero = onPlayHero,
            onOpenTile = onOpenTile,
            onOpenArtist = onOpenArtist,
            modifier = modifier,
        )

        else -> YtmSkeleton(modifier)
    }
}

// ── Feed scaffold ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun YtmFeed(
    feed: YouTubeMusicHomeFeed,
    selectedChipParams: String?,
    isRefreshing: Boolean,
    onChipSelected: (String?) -> Unit,
    onPlaySong: (SongItem) -> Unit,
    onPlayHero: (HeroItem) -> Unit,
    onOpenTile: (TileItem) -> Unit,
    onOpenArtist: (ArtistItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val heroShelf = feed.shelves.firstOrNull { it is Shelf.Hero && it.items.isNotEmpty() } as? Shelf.Hero
    val hero = heroShelf?.items?.firstOrNull()
    // Shelves render in server order; the shelf that donated the hero keeps
    // its remaining items as a regular carousel under its own title.
    val shelves = feed.shelves.mapNotNull { shelf ->
        when {
            shelf !== heroShelf -> shelf
            shelf.items.size > 1 -> Shelf.Hero(shelf.title, shelf.items.drop(1))
            else -> null
        }
    }

    val lazyState = rememberLazyListState()
    val heroScrollOffset by remember {
        derivedStateOf {
            if (lazyState.firstVisibleItemIndex == 0) lazyState.firstVisibleItemScrollOffset else 2000
        }
    }
    var renderedBigTileCarousel = false

    LazyColumn(
        state = lazyState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        if (hero != null) {
            item(key = "ytm_hero") {
                YtmHero(
                    hero = hero,
                    scrollOffset = heroScrollOffset,
                    onPlay = onPlayHero,
                )
            }
        }

        if (feed.chips.isNotEmpty()) {
            item(key = "ytm_chips") {
                MoodToggleRow(
                    chips = feed.chips,
                    selectedParams = selectedChipParams,
                    isRefreshing = isRefreshing,
                    onSelect = onChipSelected,
                )
            }
        }

        if (isRefreshing) {
            item(key = "ytm_refresh") {
                LinearWavyProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
        }

        shelves.forEachIndexed { index, shelf ->
            when (shelf) {
                is Shelf.QuickPicks -> if (shelf.items.isNotEmpty()) {
                    item(key = "ytm_shelf_${index}_header") { ShelfDisplayHeader(shelf.title) }
                    item(key = "ytm_shelf_$index") {
                        QuickPicksPager(items = shelf.items, onPlay = onPlaySong)
                    }
                }

                is Shelf.Tiles -> if (shelf.items.isNotEmpty()) {
                    val big = !renderedBigTileCarousel
                    renderedBigTileCarousel = true
                    item(key = "ytm_shelf_${index}_header") { ShelfHeader(shelf.title) }
                    item(key = "ytm_shelf_$index") {
                        if (big) {
                            ImmersiveTileCarousel(items = shelf.items, onOpen = onOpenTile)
                        } else {
                            TileCardRow(items = shelf.items, onOpen = onOpenTile)
                        }
                    }
                }

                is Shelf.Artists -> if (shelf.items.isNotEmpty()) {
                    item(key = "ytm_shelf_${index}_header") { ShelfHeader(shelf.title) }
                    item(key = "ytm_shelf_${index}_spotlight") {
                        ArtistSpotlightCard(artist = shelf.items.first(), onOpen = onOpenArtist)
                    }
                    if (shelf.items.size > 1) {
                        item(key = "ytm_shelf_$index") {
                            ArtistShapeRow(artists = shelf.items.drop(1), onOpen = onOpenArtist)
                        }
                    }
                }

                is Shelf.Hero -> if (shelf.items.isNotEmpty()) {
                    item(key = "ytm_shelf_${index}_header") { ShelfHeader(shelf.title) }
                    item(key = "ytm_shelf_$index") {
                        HeroTileRow(items = shelf.items, onOpen = onPlayHero)
                    }
                }
            }
        }
    }
}

// ── Hero ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun YtmHero(hero: HeroItem, scrollOffset: Int, onPlay: (HeroItem) -> Unit) {
    val surface = MaterialTheme.colorScheme.surface
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(10f / 11f)
            .graphicsLayer {
                translationY = -scrollOffset * 0.5f
                alpha = 1f - (scrollOffset / 1200f).coerceIn(0f, 0.6f)
            },
    ) {
        if (hero.thumbnailUrl != null) {
            AsyncImage(
                model = hero.thumbnailUrl,
                contentDescription = hero.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to surface.copy(alpha = 0.15f),
                        0.8f to surface.copy(alpha = 0.85f),
                        1f to surface,
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            if (hero.subtitle.isNotBlank()) {
                Text(
                    text = hero.subtitle.uppercase(),
                    style = MaterialTheme.typography.labelLargeEmphasized.copy(letterSpacing = 2.sp),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
            }
            Text(
                text = hero.title,
                style = MaterialTheme.typography.displaySmallEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onPlay(hero) },
                shapes = ButtonDefaults.shapes(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_play_arrow),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.common_play))
            }
        }
    }
}

// ── Mood filter row ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MoodToggleRow(chips: List<MoodChip>, selectedParams: String?, isRefreshing: Boolean, onSelect: (String?) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chips.forEach { chip ->
            ToggleButton(
                checked = chip.params == selectedParams,
                // The view model treats re-selecting the active chip as a
                // toggle-off, so both check states forward the same params.
                onCheckedChange = { onSelect(chip.params) },
                enabled = !isRefreshing,
            ) {
                Text(chip.title, maxLines = 1)
            }
        }
    }
}

// ── Section headers ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShelfDisplayHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineMediumEmphasized,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShelfHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLargeEmphasized,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

// ── Quick picks: horizontally paged 4-row grid ──────────────────────────

@Composable
private fun QuickPicksPager(items: List<SongItem>, onPlay: (SongItem) -> Unit) {
    val pages = items.chunked(4)
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            items = pages,
            key = { page -> page.first().videoId },
            contentType = { "quick_picks_page" },
        ) { page ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.width(316.dp),
            ) {
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    page.forEach { song ->
                        QuickPickRow(song = song, onPlay = { onPlay(song) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun QuickPickRow(song: SongItem, onPlay: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        MediaArt(
            url = song.thumbnailUrl,
            contentDescription = null,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.size(52.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleSmallEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(song.artist, song.album).joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        FilledTonalIconButton(
            onClick = onPlay,
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_play_arrow),
                contentDescription = stringResource(R.string.common_play),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// ── Tile shelves ────────────────────────────────────────────────────────

@Composable
private fun ImmersiveTileCarousel(items: List<TileItem>, onOpen: (TileItem) -> Unit) {
    val carouselState = rememberCarouselState { items.size }
    HorizontalMultiBrowseCarousel(
        state = carouselState,
        preferredItemWidth = 220.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp),
        itemSpacing = 8.dp,
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) { index ->
        val tile = items[index]
        Box(
            modifier = Modifier
                .fillMaxSize()
                .maskClip(MaterialTheme.shapes.extraLarge)
                .clickable { onOpen(tile) },
        ) {
            if (tile.thumbnailUrl != null) {
                AsyncImage(
                    model = tile.thumbnailUrl,
                    contentDescription = tile.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.5f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.75f),
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
            ) {
                Text(
                    text = tile.title,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (tile.subtitle.isNotBlank()) {
                    Text(
                        text = tile.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TileCardRow(items: List<TileItem>, onOpen: (TileItem) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items = items, key = { it.id }, contentType = { "tile_card" }) { tile ->
            Column(
                modifier = Modifier
                    .width(150.dp)
                    .clickable { onOpen(tile) },
            ) {
                MediaArt(
                    url = tile.thumbnailUrl,
                    contentDescription = tile.title,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = tile.title,
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (tile.subtitle.isNotBlank()) {
                    Text(
                        text = tile.subtitle,
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HeroTileRow(items: List<HeroItem>, onOpen: (HeroItem) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            items = items,
            key = { hero -> hero.videoId ?: hero.playlistId ?: hero.title },
            contentType = { "hero_tile" },
        ) { hero ->
            Column(
                modifier = Modifier
                    .width(150.dp)
                    .clickable { onOpen(hero) },
            ) {
                MediaArt(
                    url = hero.thumbnailUrl,
                    contentDescription = hero.title,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = hero.title,
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (hero.subtitle.isNotBlank()) {
                    Text(
                        text = hero.subtitle,
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

// ── Artists ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ArtistSpotlightCard(artist: ArtistItem, onOpen: (ArtistItem) -> Unit) {
    Surface(
        onClick = { onOpen(artist) },
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            MediaArt(
                url = artist.thumbnailUrl,
                contentDescription = artist.name,
                shape = MaterialShapes.Cookie9Sided.toShape(),
                modifier = Modifier.size(96.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.ytm_artist_spotlight).uppercase(),
                    style = MaterialTheme.typography.labelMediumEmphasized.copy(letterSpacing = 2.sp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ArtistShapeRow(artists: List<ArtistItem>, onOpen: (ArtistItem) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        itemsIndexed(
            items = artists,
            key = { _, artist -> artist.browseId },
            contentType = { _, _ -> "artist_tile" },
        ) { index, artist ->
            val shape = when (index % 4) {
                0 -> MaterialShapes.Cookie9Sided
                1 -> MaterialShapes.Clover4Leaf
                2 -> MaterialShapes.Sunny
                else -> MaterialShapes.Arch
            }.toShape()
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(92.dp)
                    .clickable { onOpen(artist) },
            ) {
                MediaArt(
                    url = artist.thumbnailUrl,
                    contentDescription = artist.name,
                    shape = shape,
                    modifier = Modifier.size(80.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.labelMediumEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// ── Shared bits ─────────────────────────────────────────────────────────

@Composable
private fun MediaArt(url: String?, contentDescription: String?, shape: Shape, modifier: Modifier = Modifier) {
    if (url != null) {
        AsyncImage(
            model = url,
            contentDescription = contentDescription,
            modifier = modifier.clip(shape),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_music_note),
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Loading skeleton ────────────────────────────────────────────────────

@Composable
private fun YtmSkeleton(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "ytm_skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeleton_alpha",
    )
    val bone = MaterialTheme.colorScheme.surfaceContainerHigh

    Column(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(10f / 11f)
                .graphicsLayer { this.alpha = alpha }
                .background(bone),
        )
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(4) {
                Box(
                    modifier = Modifier
                        .size(width = 88.dp, height = 40.dp)
                        .graphicsLayer { this.alpha = alpha }
                        .background(bone, MaterialTheme.shapes.extraLarge),
                )
            }
        }
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .graphicsLayer { this.alpha = alpha }
                        .background(bone, MaterialTheme.shapes.large),
                )
            }
        }
    }
}

// ── Error state ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun YtmErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialShapes.Flower.toShape(),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_cloud_outlined),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(onClick = onRetry, shapes = ButtonDefaults.shapes()) {
                Icon(
                    painter = painterResource(R.drawable.ic_refresh),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.common_retry))
            }
        }
    }
}
