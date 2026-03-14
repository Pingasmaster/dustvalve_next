package com.dustvalve.next.android.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
object AppShapes {
    /** Rounded shape for album art in cards — theme medium shape */
    val AlbumArt: Shape
        @Composable
        get() = MaterialTheme.shapes.medium

    /** Circular shape for user avatars */
    val Avatar: Shape = CircleShape

    /** Large rounded shape for player and album detail hero art — theme extraLarge shape */
    val PlayerArt: Shape
        @Composable
        get() = MaterialTheme.shapes.extraLarge

    /** Small rounded shape for thumbnails and tags — theme small shape */
    val Thumbnail: Shape
        @Composable
        get() = MaterialTheme.shapes.small

    /** Expressive shape for the "now playing" indicator badge — a soft burst */
    val NowPlaying: Shape
        @Composable
        get() = MaterialShapes.SoftBurst.toShape()

    /** Expressive organic pill shape for tags */
    val Tag: Shape
        @Composable
        get() = MaterialShapes.Pill.toShape()

    /** Fun organic shape for mini player album art */
    val MiniPlayerArt: Shape
        @Composable
        get() = MaterialShapes.Cookie9Sided.toShape()

    /** Arch shape for artist search result thumbnails */
    val SearchResultArtist: Shape
        @Composable
        get() = MaterialShapes.Arch.toShape()

    /** Sunny shape for album search result thumbnails */
    val SearchResultAlbum: Shape
        @Composable
        get() = MaterialShapes.Sunny.toShape()

    /** Square shape for track search result thumbnails */
    val SearchResultTrack: Shape
        @Composable
        get() = MaterialShapes.Square.toShape()

    /** Soft petal shape for empty state icons */
    val EmptyStateIcon: Shape
        @Composable
        get() = MaterialShapes.Flower.toShape()

    /** Slanted shape for genre chips */
    val GenreChip: Shape
        @Composable
        get() = MaterialShapes.Slanted.toShape()

    /** Ghostish shape for settings section icons */
    val SettingsIcon: Shape
        @Composable
        get() = MaterialShapes.Ghostish.toShape()

    /** Diamond shape for carousel hero badges */
    val CarouselBadge: Shape
        @Composable
        get() = MaterialShapes.Gem.toShape()

    /** Heart shape for double-tap favorite animation */
    val Heart: Shape
        @Composable
        get() = MaterialShapes.Heart.toShape()

    /** Pill shape for favorite artist items */
    val FavArtist: Shape
        @Composable
        get() = MaterialShapes.Pill.toShape()

    /** Pentagon shape for favorite album items */
    val FavAlbum: Shape
        @Composable
        get() = MaterialShapes.Pentagon.toShape()

    /** Gem shape for favorite track items */
    val FavTrack: Shape
        @Composable
        get() = MaterialShapes.Gem.toShape()

    /** PixelTriangle shape for the play button */
    val PlayButton: Shape
        @Composable
        get() = MaterialShapes.PixelTriangle.toShape()

    /** Heart shape for favorites playlist thumbnail */
    val PlaylistFavorites: Shape
        @Composable
        get() = MaterialShapes.Heart.toShape()

    /** Cookie shape for downloads playlist thumbnail */
    val PlaylistDownloads: Shape
        @Composable
        get() = MaterialShapes.Cookie9Sided.toShape()

    /** SoftBurst shape for recent playlist thumbnail */
    val PlaylistRecent: Shape
        @Composable
        get() = MaterialShapes.SoftBurst.toShape()

    /** Pentagon shape for collection playlist thumbnail */
    val PlaylistCollection: Shape
        @Composable
        get() = MaterialShapes.Pentagon.toShape()

    /** Clover shape for user-created playlist thumbnails */
    val PlaylistUser: Shape
        @Composable
        get() = MaterialShapes.Clover4Leaf.toShape()

    /** Default Sunny shape for albums in Library */
    val LibraryAlbum: Shape
        @Composable
        get() = MaterialShapes.Sunny.toShape()

    /** Default Arch shape for artists in Library */
    val LibraryArtist: Shape
        @Composable
        get() = MaterialShapes.Arch.toShape()
}

data class PlaylistShapeOption(
    val key: String,
    val label: String,
)

val PlaylistShapeOptions: List<PlaylistShapeOption> = listOf(
    PlaylistShapeOption("clover4leaf", "Clover"),
    PlaylistShapeOption("heart", "Heart"),
    PlaylistShapeOption("cookie9sided", "Cookie"),
    PlaylistShapeOption("softburst", "Burst"),
    PlaylistShapeOption("pentagon", "Pentagon"),
    PlaylistShapeOption("gem", "Gem"),
    PlaylistShapeOption("flower", "Flower"),
    PlaylistShapeOption("pill", "Pill"),
    PlaylistShapeOption("ghostish", "Ghost"),
    PlaylistShapeOption("slanted", "Slanted"),
    PlaylistShapeOption("pixelcircle", "Circle"),
    PlaylistShapeOption("pixeltriangle", "Triangle"),
    PlaylistShapeOption("sunny", "Sunny"),
    PlaylistShapeOption("arch", "Arch"),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun resolvePlaylistShape(shapeKey: String?): Shape {
    return when (shapeKey) {
        "clover4leaf" -> MaterialShapes.Clover4Leaf.toShape()
        "heart" -> MaterialShapes.Heart.toShape()
        "cookie9sided" -> MaterialShapes.Cookie9Sided.toShape()
        "softburst" -> MaterialShapes.SoftBurst.toShape()
        "pentagon" -> MaterialShapes.Pentagon.toShape()
        "gem" -> MaterialShapes.Gem.toShape()
        "flower" -> MaterialShapes.Flower.toShape()
        "pill" -> MaterialShapes.Pill.toShape()
        "ghostish" -> MaterialShapes.Ghostish.toShape()
        "slanted" -> MaterialShapes.Slanted.toShape()
        "pixelcircle" -> MaterialShapes.PixelCircle.toShape()
        "pixeltriangle" -> MaterialShapes.PixelTriangle.toShape()
        "sunny" -> MaterialShapes.Sunny.toShape()
        "arch" -> MaterialShapes.Arch.toShape()
        else -> MaterialShapes.Clover4Leaf.toShape()
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun resolveLibraryItemShape(shapeKey: String?, itemType: String): Shape {
    if (shapeKey != null) return resolvePlaylistShape(shapeKey)
    return when (itemType) {
        "album" -> AppShapes.LibraryAlbum
        "artist" -> AppShapes.LibraryArtist
        else -> resolvePlaylistShape(null)
    }
}

/**
 * Shape for segmented list items — first item has large top corners,
 * last item has large bottom corners, middle items have small corners.
 */
fun segmentedItemShape(index: Int, count: Int): RoundedCornerShape {
    val large = 28.dp
    val small = 4.dp
    return when {
        count == 1 -> RoundedCornerShape(large)
        index == 0 -> RoundedCornerShape(topStart = large, topEnd = large, bottomStart = small, bottomEnd = small)
        index == count - 1 -> RoundedCornerShape(topStart = small, topEnd = small, bottomStart = large, bottomEnd = large)
        else -> RoundedCornerShape(small)
    }
}
