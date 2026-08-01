package com.dustvalve.next.android.ui.adaptive

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pure unit coverage for [AdaptiveLayoutInfo] width-class policy used by
 * chrome, content max-width, heroes, carousels, and dual-pane decisions.
 *
 * Catalog: set-nav-rail-adaptive, set-adaptive-content-width,
 * set-library-list-detail, set-player-supporting-pane.
 */
class AdaptiveLayoutInfoTest {

    @Test
    fun compact_usesBottomNavAndNoContentCap() {
        val info = AdaptiveLayoutInfo(WidthSizeClass.Compact, 411.dp)
        assertThat(info.useNavRail).isFalse()
        assertThat(info.useDualPane).isFalse()
        assertThat(info.useSplitPlayer).isFalse()
        assertThat(info.contentMaxWidth).isEqualTo(Dp.Unspecified)
        assertThat(info.heroMaxSize).isEqualTo(Dp.Unspecified)
        assertThat(info.miniPlayerMaxWidth).isEqualTo(Dp.Unspecified)
        assertThat(info.carouselItemWidth).isEqualTo(AdaptiveTokens.CarouselCompact)
    }

    @Test
    fun medium_usesRailAndContentCap_withoutDualPane() {
        val info = AdaptiveLayoutInfo(WidthSizeClass.Medium, 700.dp)
        assertThat(info.useNavRail).isTrue()
        assertThat(info.useDualPane).isFalse()
        assertThat(info.useSplitPlayer).isTrue()
        assertThat(info.contentMaxWidth).isEqualTo(AdaptiveTokens.ContentMaxWidthMedium)
        assertThat(info.heroMaxSize).isEqualTo(AdaptiveTokens.HeroMaxSizeMedium)
        assertThat(info.miniPlayerMaxWidth).isEqualTo(AdaptiveTokens.MiniPlayerMaxWidth)
        assertThat(info.carouselItemWidth).isEqualTo(AdaptiveTokens.CarouselMedium)
    }

    @Test
    fun expanded_enablesDualPaneAndWiderContent() {
        val info = AdaptiveLayoutInfo(WidthSizeClass.Expanded, 1280.dp)
        assertThat(info.useNavRail).isTrue()
        assertThat(info.useDualPane).isTrue()
        assertThat(info.useSplitPlayer).isTrue()
        assertThat(info.contentMaxWidth).isEqualTo(AdaptiveTokens.ContentMaxWidthExpanded)
        assertThat(info.heroMaxSize).isEqualTo(AdaptiveTokens.HeroMaxSizeExpanded)
        assertThat(info.carouselItemWidth).isEqualTo(AdaptiveTokens.CarouselExpanded)
        assertThat(info.sheetMaxWidth).isEqualTo(AdaptiveTokens.SheetMaxWidth)
        assertThat(info.gridMinSize).isEqualTo(AdaptiveTokens.GridMinSize)
    }
}
