package com.dustvalve.next.android.ui.screens.player

import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Catalog: local-queue-window-load-more-after-click
 *
 * rememberUpNextQueueWindow must return the same instance after
 * currentQueueIndex changes. Keying remember() on the index recreates the
 * window while the load-more LaunchedEffect keeps the old one, which is
 * the stuck-footer regression.
 */
@RunWith(AndroidJUnit4::class)
class UpNextQueueWindowRememberTest {

    @get:Rule val rule = createComposeRule()

    @Test
    fun rememberedWindowIsTheSameInstanceAfterSkippingATrack() {
        val indexState = mutableIntStateOf(0)
        val seen = mutableListOf<UpNextQueueWindow>()

        rule.setContent {
            val window = rememberUpNextQueueWindow(indexState.intValue)
            SideEffect { seen.add(window) }
        }
        rule.waitForIdle()
        val beforeClick = seen.last()
        assertThat(beforeClick.displayCount).isEqualTo(QUEUE_WINDOW_PAGE_SIZE)

        rule.runOnIdle { indexState.intValue = 6 }
        rule.waitForIdle()
        val afterClick = seen.last()

        assertThat(afterClick).isSameInstanceAs(beforeClick)
        assertThat(seen.distinct()).hasSize(1)
        assertThat(afterClick.displayCount).isEqualTo(QUEUE_WINDOW_PAGE_SIZE)

        // Load-more effect still holds the instance captured on first composition.
        val expanded = beforeClick.expandIfNearEnd(
            lastVisibleIndex = 24,
            totalItemsCount = 27,
            remainingCount = 80,
        )
        assertThat(expanded).isTrue()
        assertThat(afterClick.displayCount).isEqualTo(QUEUE_WINDOW_PAGE_SIZE * 2)
    }
}
