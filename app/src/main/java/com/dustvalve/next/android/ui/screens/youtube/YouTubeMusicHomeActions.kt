package com.dustvalve.next.android.ui.screens.youtube

import androidx.compose.runtime.Immutable
import com.dustvalve.next.android.domain.model.ArtistItem
import com.dustvalve.next.android.domain.model.HeroItem
import com.dustvalve.next.android.domain.model.SongItem
import com.dustvalve.next.android.domain.model.TileItem

@Immutable
data class YouTubeMusicHomeActions(
    val onChipSelected: (String?) -> Unit,
    val onPlaySong: (SongItem) -> Unit,
    val onPlayHero: (HeroItem) -> Unit,
    val onOpenTile: (TileItem) -> Unit,
    val onOpenArtist: (ArtistItem) -> Unit,
    val onRetry: () -> Unit = {},
)
