package com.dustvalve.next.android.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlaylistTest {

    private fun base(
        isSystem: Boolean = false,
        trackCount: Int = 0,
        systemType: Playlist.SystemPlaylistType? = null,
    ) = Playlist(
        id = "p1",
        name = "Test",
        isSystem = isSystem,
        systemType = systemType,
        trackCount = trackCount,
    )

    @Test fun `user playlist is editable and deletable`() {
        val p = base()
        assertThat(p.isEditable).isTrue()
        assertThat(p.isDeletable).isTrue()
    }

    @Test fun `system playlist is not editable or deletable`() {
        val p = base(isSystem = true, systemType = Playlist.SystemPlaylistType.FAVORITES)
        assertThat(p.isEditable).isFalse()
        assertThat(p.isDeletable).isFalse()
    }

    @Test fun `system subtitle is Auto playlist`() {
        val p = base(isSystem = true, trackCount = 5, systemType = Playlist.SystemPlaylistType.RECENT)
        assertThat(p.displaySubtitle).isEqualTo("Auto playlist")
    }

    @Test fun `user subtitle for single track`() {
        assertThat(base(trackCount = 1).displaySubtitle).isEqualTo("1 song")
    }

    @Test fun `user subtitle for zero and many tracks`() {
        assertThat(base(trackCount = 0).displaySubtitle).isEqualTo("0 songs")
        assertThat(base(trackCount = 2).displaySubtitle).isEqualTo("2 songs")
        assertThat(base(trackCount = 100).displaySubtitle).isEqualTo("100 songs")
    }

    @Test fun `system playlist ids are distinct`() {
        val ids = listOf(
            Playlist.ID_DOWNLOADS,
            Playlist.ID_RECENT,
            Playlist.ID_COLLECTION,
            Playlist.ID_FAVORITES,
        )
        assertThat(ids).containsNoDuplicates()
    }
}
