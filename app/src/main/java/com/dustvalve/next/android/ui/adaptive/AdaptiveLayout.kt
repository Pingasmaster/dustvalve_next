package com.dustvalve.next.android.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass

/**
 * App-facing width buckets used for layout decisions.
 *
 * Mapped from [WindowSizeClass] breakpoints:
 * Compact < 600dp, Medium 600-839dp, Expanded >= 840dp.
 */
enum class WidthSizeClass {
    Compact,
    Medium,
    Expanded,
}

/**
 * Shared adaptive metrics for chrome, content width, grids, and panes.
 * Computed once near the activity root and threaded as an explicit parameter
 * so screens do not re-derive breakpoints.
 */
@Immutable
data class AdaptiveLayoutInfo(val widthSizeClass: WidthSizeClass, val windowWidthDp: Dp) {
    /** Navigation rail replaces the bottom bar at Medium and above. */
    val useNavRail: Boolean get() = widthSizeClass != WidthSizeClass.Compact

    /** Library list-detail and player supporting pane at Expanded. */
    val useDualPane: Boolean get() = widthSizeClass == WidthSizeClass.Expanded

    /** Side-by-side player art + controls when the window is at least Medium. */
    val useSplitPlayer: Boolean get() = widthSizeClass != WidthSizeClass.Compact

    val contentMaxWidth: Dp
        get() = when (widthSizeClass) {
            WidthSizeClass.Compact -> Dp.Unspecified
            WidthSizeClass.Medium -> AdaptiveTokens.ContentMaxWidthMedium
            WidthSizeClass.Expanded -> AdaptiveTokens.ContentMaxWidthExpanded
        }

    val heroMaxSize: Dp
        get() = when (widthSizeClass) {
            WidthSizeClass.Compact -> Dp.Unspecified
            WidthSizeClass.Medium -> AdaptiveTokens.HeroMaxSizeMedium
            WidthSizeClass.Expanded -> AdaptiveTokens.HeroMaxSizeExpanded
        }

    val carouselItemWidth: Dp
        get() = when (widthSizeClass) {
            WidthSizeClass.Compact -> AdaptiveTokens.CarouselCompact
            WidthSizeClass.Medium -> AdaptiveTokens.CarouselMedium
            WidthSizeClass.Expanded -> AdaptiveTokens.CarouselExpanded
        }

    val sheetMaxWidth: Dp get() = AdaptiveTokens.SheetMaxWidth

    val miniPlayerMaxWidth: Dp
        get() = when (widthSizeClass) {
            WidthSizeClass.Compact -> Dp.Unspecified
            else -> AdaptiveTokens.MiniPlayerMaxWidth
        }

    val gridMinSize: Dp get() = AdaptiveTokens.GridMinSize
}

object AdaptiveTokens {
    val ContentMaxWidthMedium = 840.dp
    val ContentMaxWidthExpanded = 1080.dp
    val HeroMaxSizeMedium = 420.dp
    val HeroMaxSizeExpanded = 480.dp
    val CarouselCompact = 200.dp
    val CarouselMedium = 240.dp
    val CarouselExpanded = 280.dp
    val SheetMaxWidth = 640.dp
    val MiniPlayerMaxWidth = 480.dp
    val GridMinSize = 160.dp
    val PrimaryActionMaxWidth = 480.dp
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun rememberAdaptiveLayoutInfo(): AdaptiveLayoutInfo {
    val windowAdaptiveInfo = currentWindowAdaptiveInfoV2()
    val sizeClass = windowAdaptiveInfo.windowSizeClass
    val widthDp = sizeClass.minWidthDp.dp
    val widthSizeClass = when {
        sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) ->
            WidthSizeClass.Expanded

        sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) ->
            WidthSizeClass.Medium

        else -> WidthSizeClass.Compact
    }
    return remember(widthSizeClass, widthDp) {
        AdaptiveLayoutInfo(
            widthSizeClass = widthSizeClass,
            windowWidthDp = widthDp,
        )
    }
}

/**
 * Centers content and caps its width on Medium/Expanded windows.
 * On Compact this is a no-op (full available width).
 */
fun Modifier.adaptiveContentWidth(maxWidth: Dp = Dp.Unspecified): Modifier {
    if (maxWidth == Dp.Unspecified) return this
    return this
        .fillMaxWidth()
        .widthIn(max = maxWidth)
}

/**
 * Caps a square hero (album art, artist image) so it does not dominate tablets.
 * Caller should place the result in a centered parent (e.g. Box with Center).
 */
fun Modifier.adaptiveHeroSize(maxSize: Dp): Modifier {
    if (maxSize == Dp.Unspecified) {
        return this.fillMaxWidth()
    }
    return this
        .fillMaxWidth()
        .widthIn(max = maxSize)
}

/**
 * Convenience wrapper that centers content capped by [contentMaxWidth].
 */
@Composable
fun AdaptiveContentColumn(contentMaxWidth: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .adaptiveContentWidth(contentMaxWidth),
        contentAlignment = Alignment.TopCenter,
    ) {
        content()
    }
}
