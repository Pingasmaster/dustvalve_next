package com.dustvalve.next.android.util

import androidx.test.core.app.ApplicationProvider
import com.dustvalve.next.android.R
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UiTextTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test fun `dynamic string returns value`() {
        val t = UiText.DynamicString("hi")
        assertThat(t.asString(context)).isEqualTo("hi")
    }

    @Test fun `string resource with no args`() {
        val t = UiText.StringResource(R.string.app_name)
        assertThat(t.asString(context)).isEqualTo("Dustvalve Next")
    }

    @Test fun `string resource with format args`() {
        val t = UiText.StringResource(R.string.common_playlist_imported, listOf("My Mix"))
        assertThat(t.asString(context)).isEqualTo("Playlist imported: My Mix")
    }

    @Test fun `plurals one`() {
        val t = UiText.PluralsResource(R.plurals.song_count, 1)
        assertThat(t.asString(context)).isEqualTo("1 song")
    }

    @Test fun `plurals many`() {
        val t = UiText.PluralsResource(R.plurals.song_count, 5)
        assertThat(t.asString(context)).isEqualTo("5 songs")
    }

    @Test fun `plurals zero uses other`() {
        val t = UiText.PluralsResource(R.plurals.song_count, 0)
        assertThat(t.asString(context)).isEqualTo("0 songs")
    }
}
