package com.dustvalve.next.android.ui.screens.youtube

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Size
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import coil3.compose.AsyncImage
import com.dustvalve.next.android.R
import com.dustvalve.next.android.domain.model.ArtistItem
import com.dustvalve.next.android.domain.model.HeroItem
import com.dustvalve.next.android.domain.model.MoodChip
import com.dustvalve.next.android.domain.model.Shelf
import com.dustvalve.next.android.domain.model.SongItem
import com.dustvalve.next.android.domain.model.TileItem
import com.dustvalve.next.android.domain.model.TileKind
import com.dustvalve.next.android.domain.model.YouTubeMusicHomeFeed
import kotlinx.coroutines.launch

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
        state.ytmHome == null && state.ytmHomeLoading -> YouTubeMusicLoadingState(modifier)
        state.ytmHome == null && state.ytmHomeError != null -> YouTubeMusicErrorState(
            message = state.ytmHomeError,
            onRetry = onRetry,
            modifier = modifier,
        )
        state.ytmHome != null -> YouTubeMusicContent(
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
        else -> YouTubeMusicLoadingState(modifier)
    }
}

@Composable
private fun YouTubeMusicLoadingState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ContainedLoadingIndicator()
    }
}

@Composable
private fun YouTubeMusicErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.common_retry))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun YouTubeMusicContent(
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
    val shelves = feed.shelves
    val heroShelf = shelves.firstOrNull { it is Shelf.Hero } as? Shelf.Hero
    val remaining = shelves.filter { it !== heroShelf }

    val quickPicksShelf = remaining.firstOrNull { it is Shelf.QuickPicks } as? Shelf.QuickPicks
    val mixedForYouShelf = remaining
        .filterIsInstance<Shelf.Tiles>()
        .firstOrNull { it.title.contains("mix", ignoreCase = true) }
    val artistsShelf = remaining.firstOrNull { it is Shelf.Artists } as? Shelf.Artists
    val firstStaggered = remaining
        .filterIsInstance<Shelf.Tiles>()
        .firstOrNull { it !== mixedForYouShelf }

    val usedShelves = buildSet {
        quickPicksShelf?.let { add(it) }
        mixedForYouShelf?.let { add(it) }
        artistsShelf?.let { add(it) }
        firstStaggered?.let { add(it) }
    }
    val overflowShelves = remaining.filter { it !in usedShelves }

    val lazyState = rememberLazyListState()
    val heroScrollOffset by remember {
        derivedStateOf {
            if (lazyState.firstVisibleItemIndex == 0) lazyState.firstVisibleItemScrollOffset else 2000
        }
    }

    LazyColumn(
        state = lazyState,
        modifier = modifier.fillMaxSize(),
    ) {
        if (heroShelf != null && heroShelf.items.isNotEmpty()) {
            item(key = "ytm_hero") {
                ParallaxHeroSection(
                    hero = heroShelf.items.first(),
                    scrollOffset = heroScrollOffset,
                    onPlay = onPlayHero,
                )
            }
        }

        if (feed.chips.isNotEmpty()) {
            item(key = "ytm_chips") {
                ChipCloudRow(
                    chips = feed.chips,
                    selectedParams = selectedChipParams,
                    onSelect = onChipSelected,
                    isRefreshing = isRefreshing,
                )
            }

            item(key = "ytm_bento") {
                ShapeMorphMoodBento(
                    chips = feed.chips.take(BENTO_TILE_COUNT),
                    selectedParams = selectedChipParams,
                    onSelect = onChipSelected,
                )
            }
        }

        if (quickPicksShelf != null && quickPicksShelf.items.isNotEmpty()) {
            item(key = "ytm_quickpicks_break") {
                TypographicBreak(title = quickPicksShelf.title)
            }
            item(key = "ytm_quickpicks") {
                QuickPicksGrid(
                    items = quickPicksShelf.items,
                    onPlay = onPlaySong,
                )
            }
        }

        if (mixedForYouShelf != null && mixedForYouShelf.items.isNotEmpty()) {
            item(key = "ytm_mix_break") {
                TypographicBreak(title = mixedForYouShelf.title)
            }
            item(key = "ytm_mix_carousel") {
                MixedForYouHeroCarousel(
                    items = mixedForYouShelf.items,
                    onOpen = onOpenTile,
                )
            }
        }

        if (firstStaggered != null && firstStaggered.items.isNotEmpty()) {
            item(key = "ytm_stag_header") {
                SectionHeader(firstStaggered.title)
            }
            item(key = "ytm_staggered") {
                StaggeredTilesSection(
                    items = firstStaggered.items,
                    onOpen = onOpenTile,
                )
            }
        }

        if (artistsShelf != null && artistsShelf.items.isNotEmpty()) {
            item(key = "ytm_artist_spotlight") {
                ArtistSpotlightCard(
                    artist = artistsShelf.items.first(),
                    onOpen = onOpenArtist,
                )
            }
            if (artistsShelf.items.size > 1) {
                item(key = "ytm_artist_row") {
                    ArtistRow(
                        artists = artistsShelf.items.drop(1),
                        onOpen = onOpenArtist,
                    )
                }
            }
        }

        itemsIndexed(
            items = overflowShelves,
            key = { idx, _ -> "ytm_overflow_$idx" },
        ) { _, shelf ->
            when (shelf) {
                is Shelf.Tiles -> {
                    SectionHeader(shelf.title)
                    TilesCarousel(items = shelf.items, onOpen = onOpenTile)
                }
                is Shelf.Hero -> {
                    SectionHeader(shelf.title)
                    TilesFromHeroesCarousel(items = shelf.items, onOpen = onPlayHero)
                }
                is Shelf.QuickPicks -> {
                    TypographicBreak(title = shelf.title)
                    QuickPicksGrid(items = shelf.items, onPlay = onPlaySong)
                }
                is Shelf.Artists -> {
                    SectionHeader(shelf.title)
                    ArtistRow(artists = shelf.items, onOpen = onOpenArtist)
                }
            }
        }
    }
}

// ── Parallax hero ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ParallaxHeroSection(
    hero: HeroItem,
    scrollOffset: Int,
    onPlay: (HeroItem) -> Unit,
) {
    val cookieShape = remember { MaterialShapes.Cookie9Sided.toPolygonShape() }
    val overlayColor = MaterialTheme.colorScheme.surface

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 10f)
            .graphicsLayer { translationY = -scrollOffset * 0.5f },
    ) {
        if (hero.thumbnailUrl != null) {
            AsyncImage(
                model = hero.thumbnailUrl,
                contentDescription = hero.title,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh))
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, overlayColor.copy(alpha = 0.4f), overlayColor.copy(alpha = 0.95f)),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(24.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            if (hero.subtitle.isNotBlank()) {
                Text(
                    text = hero.subtitle.uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
            }
            Text(
                text = hero.title,
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { onPlay(hero) },
                shape = cookieShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_play_circle),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.common_play))
            }
        }
    }
}

// ── Morphing chip cloud row ────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChipCloudRow(
    chips: List<MoodChip>,
    selectedParams: String?,
    onSelect: (String?) -> Unit,
    isRefreshing: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chips.forEach { chip ->
            val isSelected = chip.params == selectedParams
            val targetProgress = if (isSelected) 1f else 0f
            val morphProgress = remember(chip.params) { Animatable(targetProgress) }
            LaunchedEffect(isSelected) { morphProgress.animateTo(targetProgress) }

            FilterChip(
                selected = isSelected,
                onClick = { onSelect(chip.params) },
                label = { Text(chip.title) },
                shape = remember(chip.params) { PillCookieMorphShape(morphProgress) },
                enabled = !isRefreshing,
            )
        }
    }
}

// Wrapper that reads the Animatable each render so shape tracks animation.
private class PillCookieMorphShape(
    private val progress: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
) : Shape {
    private val morph: Morph = Morph(MaterialShapes.Pill, MaterialShapes.Cookie9Sided)

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = buildOutlineFromMorph(morph, progress.value, size)
}

// ── Shape-morph mood bento ─────────────────────────────────────────────

private const val BENTO_TILE_COUNT = 5

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShapeMorphMoodBento(
    chips: List<MoodChip>,
    selectedParams: String?,
    onSelect: (String?) -> Unit,
) {
    if (chips.isEmpty()) return
    val padded = chips.toMutableList().apply {
        while (size < BENTO_TILE_COUNT) add(MoodChip("", ""))
    }
    val hap = LocalHapticFeedback.current
    val palette = listOf(
        MaterialTheme.colorScheme.tertiaryContainer,
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.surfaceContainerHigh,
        MaterialTheme.colorScheme.tertiaryContainer,
    )
    val onPalette = listOf(
        MaterialTheme.colorScheme.onTertiaryContainer,
        MaterialTheme.colorScheme.onPrimaryContainer,
        MaterialTheme.colorScheme.onSecondaryContainer,
        MaterialTheme.colorScheme.onSurface,
        MaterialTheme.colorScheme.onTertiaryContainer,
    )

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth().height(160.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BentoTile(
                chip = padded[0],
                selected = padded[0].params == selectedParams,
                bg = palette[0],
                fg = onPalette[0],
                onClick = {
                    hap.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSelect(padded[0].params)
                },
                modifier = Modifier.weight(2f).fillMaxSize(),
            )
            Column(modifier = Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BentoTile(
                    chip = padded[1],
                    selected = padded[1].params == selectedParams,
                    bg = palette[1],
                    fg = onPalette[1],
                    onClick = {
                        hap.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSelect(padded[1].params)
                    },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                BentoTile(
                    chip = padded[2],
                    selected = padded[2].params == selectedParams,
                    bg = palette[2],
                    fg = onPalette[2],
                    onClick = {
                        hap.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSelect(padded[2].params)
                    },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth().height(100.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BentoTile(
                chip = padded[3],
                selected = padded[3].params == selectedParams,
                bg = palette[3],
                fg = onPalette[3],
                onClick = {
                    hap.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSelect(padded[3].params)
                },
                modifier = Modifier.weight(1f).fillMaxSize(),
            )
            BentoTile(
                chip = padded[4],
                selected = padded[4].params == selectedParams,
                bg = palette[4],
                fg = onPalette[4],
                onClick = {
                    hap.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSelect(padded[4].params)
                },
                modifier = Modifier.weight(1f).fillMaxSize(),
            )
        }
    }
}

@Composable
private fun BentoTile(
    chip: MoodChip,
    selected: Boolean,
    bg: Color,
    fg: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (chip.title.isBlank()) {
        Box(modifier = modifier)
        return
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val targetProgress = when {
        pressed -> 1f
        selected -> 0.6f
        else -> 0f
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(targetProgress) { progress.animateTo(targetProgress) }

    val baseShape = remember { TileMorphShape(progress) }
    val bgAnimated by animateColorAsState(
        if (selected) bg else bg.copy(alpha = 0.85f),
        label = "bento_bg",
    )

    Box(
        modifier = modifier
            .clip(baseShape)
            .background(bgAnimated)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(12.dp),
    ) {
        Text(
            text = chip.title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = fg,
            modifier = Modifier.align(Alignment.BottomStart),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private class TileMorphShape(
    private val progress: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
) : Shape {
    private val morph: Morph = Morph(MaterialShapes.Cookie12Sided, MaterialShapes.Pill)

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = buildOutlineFromMorph(morph, progress.value, size)
}

// ── Quick Picks 2-row horizontal grid ──────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun QuickPicksGrid(
    items: List<SongItem>,
    onPlay: (SongItem) -> Unit,
) {
    val chunked = items.chunked(2)
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items = chunked) { pair ->
            Column(
                modifier = Modifier.width(320.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                pair.forEach { song ->
                    QuickPickRow(song = song, onPlay = { onPlay(song) })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun QuickPickRow(song: SongItem, onPlay: () -> Unit) {
    ListItem(
        headlineContent = {
            Text(
                text = song.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = listOfNotNull(song.artist, song.album).joinToString(" • "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingContent = {
            if (song.thumbnailUrl != null) {
                AsyncImage(
                    model = song.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                )
            }
        },
        trailingContent = {
            IconButton(onClick = onPlay, shapes = IconButtonDefaults.shapes()) {
                Icon(
                    painter = painterResource(R.drawable.ic_play_circle),
                    contentDescription = stringResource(R.string.common_play),
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { onPlay() },
    )
}

// ── Typographic break + section header ─────────────────────────────────

@Composable
private fun TypographicBreak(title: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

// ── Mixed-for-You hero carousel ────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MixedForYouHeroCarousel(
    items: List<TileItem>,
    onOpen: (TileItem) -> Unit,
) {
    if (items.isEmpty()) return
    val carouselState = rememberCarouselState { items.size }
    HorizontalMultiBrowseCarousel(
        state = carouselState,
        preferredItemWidth = 240.dp,
        modifier = Modifier.fillMaxWidth().height(240.dp),
        itemSpacing = 8.dp,
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) { index ->
        val tile = items[index]
        Column(
            modifier = Modifier.clickable { onOpen(tile) },
        ) {
            if (tile.thumbnailUrl != null) {
                AsyncImage(
                    model = tile.thumbnailUrl,
                    contentDescription = tile.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(24.dp)),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = tile.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── Staggered tiles section ────────────────────────────────────────────

@Composable
private fun StaggeredTilesSection(
    items: List<TileItem>,
    onOpen: (TileItem) -> Unit,
) {
    // Render as an asymmetric grid using Row/Column composition for visual variety.
    val pairs = items.take(6).chunked(2)
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        pairs.forEachIndexed { rowIndex, pair ->
            val leftWeight = if (rowIndex % 2 == 0) 1.2f else 1f
            val rightWeight = if (rowIndex % 2 == 0) 1f else 1.2f
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.getOrNull(0)?.let { tile ->
                    StaggeredTile(tile = tile, onOpen = onOpen, modifier = Modifier.weight(leftWeight))
                }
                pair.getOrNull(1)?.let { tile ->
                    StaggeredTile(tile = tile, onOpen = onOpen, modifier = Modifier.weight(rightWeight))
                }
            }
        }
    }
}

@Composable
private fun StaggeredTile(
    tile: TileItem,
    onOpen: (TileItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clickable { onOpen(tile) },
    ) {
        if (tile.thumbnailUrl != null) {
            AsyncImage(
                model = tile.thumbnailUrl,
                contentDescription = tile.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(20.dp)),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = tile.title,
            style = MaterialTheme.typography.titleSmall,
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

// ── Tiles carousel (generic fallback) ──────────────────────────────────

@Composable
private fun TilesCarousel(
    items: List<TileItem>,
    onOpen: (TileItem) -> Unit,
) {
    if (items.isEmpty()) return
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items = items) { tile ->
            Column(modifier = Modifier.width(140.dp).clickable { onOpen(tile) }) {
                if (tile.thumbnailUrl != null) {
                    AsyncImage(
                        model = tile.thumbnailUrl,
                        contentDescription = tile.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp)),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = tile.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TilesFromHeroesCarousel(
    items: List<HeroItem>,
    onOpen: (HeroItem) -> Unit,
) {
    if (items.isEmpty()) return
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items = items) { hero ->
            Column(modifier = Modifier.width(160.dp).clickable { onOpen(hero) }) {
                if (hero.thumbnailUrl != null) {
                    AsyncImage(
                        model = hero.thumbnailUrl,
                        contentDescription = hero.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(18.dp)),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = hero.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ── Artist spotlight + row ─────────────────────────────────────────────

@Composable
private fun ArtistSpotlightCard(
    artist: ArtistItem,
    onOpen: (ArtistItem) -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "artist_ring")
    val morphProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "morph",
    )
    val ringShape = remember {
        object : Shape {
            private val morph = Morph(MaterialShapes.Clover8Leaf, MaterialShapes.Cookie12Sided)

            override fun createOutline(
                size: Size,
                layoutDirection: LayoutDirection,
                density: Density,
            ): Outline = buildOutlineFromMorph(morph, 0f, size)
        }
    }

    Surface(
        onClick = { onOpen(artist) },
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .graphicsLayer { rotationZ = morphProgress * 60f }
                        .background(
                            MaterialTheme.colorScheme.primary,
                            shape = remember(morphProgress) {
                                AnimatedMorphShape(morphProgress)
                            },
                        ),
                )
                if (artist.thumbnailUrl != null) {
                    AsyncImage(
                        model = artist.thumbnailUrl,
                        contentDescription = artist.name,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainer),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.ytm_artist_spotlight).uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private class AnimatedMorphShape(private val progress: Float) : Shape {
    private val morph: Morph = Morph(MaterialShapes.Clover8Leaf, MaterialShapes.Cookie12Sided)

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = buildOutlineFromMorph(morph, progress, size)
}

@Composable
private fun ArtistRow(
    artists: List<ArtistItem>,
    onOpen: (ArtistItem) -> Unit,
) {
    if (artists.isEmpty()) return
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items = artists) { artist ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(84.dp).clickable { onOpen(artist) },
            ) {
                if (artist.thumbnailUrl != null) {
                    AsyncImage(
                        model = artist.thumbnailUrl,
                        contentDescription = artist.name,
                        modifier = Modifier.size(72.dp).clip(CircleShape),
                    )
                } else {
                    Box(modifier = Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh))
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// ── Shape utilities ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun RoundedPolygon.toPolygonShape(): Shape = object : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val polygon = this@toPolygonShape
        val path = polygon.toPath().asComposePath()
        val bounds = polygon.calculateBounds()
        val matrix = android.graphics.Matrix()
        val pathW = bounds[2] - bounds[0]
        val pathH = bounds[3] - bounds[1]
        val cx = (bounds[0] + bounds[2]) / 2f
        val cy = (bounds[1] + bounds[3]) / 2f
        matrix.postTranslate(-cx, -cy)
        val scale = minOf(size.width / pathW, size.height / pathH)
        matrix.postScale(scale, scale)
        matrix.postTranslate(size.width / 2f, size.height / 2f)
        val androidPath = android.graphics.Path()
        polygon.toPath(androidPath)
        androidPath.transform(matrix)
        return Outline.Generic(androidPath.asComposePath())
    }
}

private fun buildOutlineFromMorph(morph: Morph, progress: Float, size: Size): Outline {
    val androidPath = android.graphics.Path()
    morph.toPath(progress = progress, path = androidPath)

    val bounds = morph.calculateBounds()
    val pathW = bounds[2] - bounds[0]
    val pathH = bounds[3] - bounds[1]
    val cx = (bounds[0] + bounds[2]) / 2f
    val cy = (bounds[1] + bounds[3]) / 2f

    val matrix = android.graphics.Matrix()
    if (pathW > 0f && pathH > 0f) {
        matrix.postTranslate(-cx, -cy)
        val scale = minOf(size.width / pathW, size.height / pathH)
        matrix.postScale(scale, scale)
        matrix.postTranslate(size.width / 2f, size.height / 2f)
    }
    androidPath.transform(matrix)
    return Outline.Generic(androidPath.asComposePath())
}
