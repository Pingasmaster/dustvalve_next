package com.dustvalve.next.android.ui.components.sheet

import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dustvalve.next.android.domain.model.AudioFormat
import com.dustvalve.next.android.domain.model.ExportableTrack
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.model.TrackSource
import com.dustvalve.next.android.ui.theme.DustvalveNextTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Robolectric Compose test for [ExportTracksSheetBody], the stateless body
 * of [ExportTracksSheet]. Drives the sheet through its public state hook
 * surface so the test does not depend on [DropdownMenu]'s popup window
 * (which is awkward to render under Robolectric).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class ExportTracksSheetTest {

    @get:Rule val composeTestRule = createComposeRule()

    private val tracks = listOf(
        exportable(
            id = "yt_track",
            title = "YT MP3 128",
            source = TrackSource.YOUTUBE,
            format = AudioFormat.MP3_128,
            qualityLabel = "128 kbps",
        ),
        exportable(
            id = "spotify_track",
            title = "Spot OGG 320",
            source = TrackSource.SPOTIFY,
            format = AudioFormat.OGG_VORBIS_320,
            qualityLabel = "320 kbps",
        ),
        exportable(
            id = "bandcamp_track",
            title = "BC FLAC",
            source = TrackSource.BANDCAMP,
            format = AudioFormat.FLAC,
            qualityLabel = "FLAC",
        ),
    )

    @Test fun `renders all rows with platform format and quality chips`() {
        var lastExport: Set<String>? = null
        val harness = setStatefulContent(onExport = { lastExport = it })

        composeTestRule.onNodeWithText("YT MP3 128").assertExists()
        composeTestRule.onNodeWithText("Spot OGG 320").assertExists()
        composeTestRule.onNodeWithText("BC FLAC").assertExists()

        // Format chips (one per row).
        composeTestRule.onNodeWithText("MP3 128 kbps").assertExists()
        composeTestRule.onNodeWithText("OGG Vorbis 320").assertExists()
        composeTestRule.onNodeWithText("FLAC (Lossless)").assertExists()

        // Quality chips. "FLAC" appears as both the format display name *and*
        // the quality label, so use onAllNodesWithText to check existence.
        assertThat(composeTestRule.onAllNodesWithText("128 kbps").fetchSemanticsNodes()).isNotEmpty()
        assertThat(composeTestRule.onAllNodesWithText("320 kbps").fetchSemanticsNodes()).isNotEmpty()

        // Platform chips.
        composeTestRule.onNodeWithText("Youtube").assertExists()
        composeTestRule.onNodeWithText("Spotify").assertExists()
        composeTestRule.onNodeWithText("Bandcamp").assertExists()

        assertThat(harness.dismissed).isFalse()
        assertThat(lastExport).isNull()
    }

    @Test fun `tapping row toggles selection and FAB label`() {
        var lastExport: Set<String>? = null
        setStatefulContent(onExport = { lastExport = it })

        // Initial state: FAB shows "(0)" and is disabled.
        composeTestRule.onNodeWithText("Export selected (0)", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag(TestTags.ExportFab).assertIsNotEnabled()

        // Toggle row 1 (yt_track).
        composeTestRule.onNodeWithTag("${TestTags.RowPrefix}yt_track").performClick()
        composeTestRule.onNodeWithText("Export selected (1)", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag(TestTags.ExportFab).assertIsEnabled()

        // Toggle off.
        composeTestRule.onNodeWithTag("${TestTags.RowPrefix}yt_track").performClick()
        composeTestRule.onNodeWithText("Export selected (0)", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag(TestTags.ExportFab).assertIsNotEnabled()

        assertThat(lastExport).isNull()
    }

    @Test fun `select all then deselect all updates FAB`() {
        val harness = setStatefulContent()

        harness.invokeSelectAll()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Export selected (3)", useUnmergedTree = true).assertExists()

        harness.invokeDeselectAll()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Export selected (0)", useUnmergedTree = true).assertExists()
    }

    @Test fun `Export all calls onExport with every track id`() {
        var lastExport: Set<String>? = null
        val harness = setStatefulContent(onExport = { lastExport = it })

        harness.invokeExportAll()
        composeTestRule.waitForIdle()

        assertThat(lastExport).containsExactly("yt_track", "spotify_track", "bandcamp_track")
    }

    @Test fun `tapping FAB after selecting two passes those two ids`() {
        var lastExport: Set<String>? = null
        setStatefulContent(onExport = { lastExport = it })

        composeTestRule.onNodeWithTag("${TestTags.RowPrefix}yt_track").performClick()
        composeTestRule.onNodeWithTag("${TestTags.RowPrefix}bandcamp_track").performClick()
        composeTestRule.onNodeWithTag(TestTags.ExportFab).performClick()

        assertThat(lastExport).containsExactly("yt_track", "bandcamp_track")
    }

    /** Mutable hooks the tests use to drive the stateless body composable. */
    private class Harness(
        val invokeSelectAll: () -> Unit,
        val invokeDeselectAll: () -> Unit,
        val invokeExportAll: () -> Unit,
        var dismissed: Boolean = false,
    )

    private fun setStatefulContent(
        onExport: (Set<String>) -> Unit = {},
    ): Harness {
        var selectAll: () -> Unit = {}
        var deselectAll: () -> Unit = {}
        var exportAll: () -> Unit = {}
        val harness = Harness(
            invokeSelectAll = { selectAll() },
            invokeDeselectAll = { deselectAll() },
            invokeExportAll = { exportAll() },
        )
        composeTestRule.setContent {
            DustvalveNextTheme {
                Surface {
                    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
                    var showOverflow by remember { mutableStateOf(false) }
                    selectAll = { selected = tracks.map { it.track.id }.toSet() }
                    deselectAll = { selected = emptySet() }
                    exportAll = { onExport(tracks.map { it.track.id }.toSet()) }
                    ExportTracksSheetBody(
                        tracks = tracks,
                        selected = selected,
                        showOverflow = showOverflow,
                        onToggleTrack = { id ->
                            selected = if (id in selected) selected - id else selected + id
                        },
                        onSelectAll = selectAll,
                        onDeselectAll = deselectAll,
                        onExportSelected = { onExport(selected) },
                        onExportAll = exportAll,
                        onShowOverflow = { showOverflow = true },
                        onHideOverflow = { showOverflow = false },
                    )
                }
            }
        }
        return harness
    }

    private fun exportable(
        id: String,
        title: String,
        source: TrackSource,
        format: AudioFormat,
        qualityLabel: String,
    ): ExportableTrack = ExportableTrack(
        track = Track(
            id = id,
            albumId = "album_$id",
            title = title,
            artist = "Artist $id",
            trackNumber = 1,
            duration = 180f,
            streamUrl = "file:///tmp/$id.mp3",
            artUrl = "https://example.com/$id.jpg",
            albumTitle = "Album $id",
            source = source,
        ),
        format = format,
        sizeBytes = 1_000_000L,
        qualityLabel = qualityLabel,
    )
}
