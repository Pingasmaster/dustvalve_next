package com.dustvalve.next.android.data.mapper

import com.dustvalve.next.android.data.local.db.entity.PlaylistEntity
import com.dustvalve.next.android.domain.model.Playlist
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlaylistMapperTest {

    private fun sample(
        isSystem: Boolean = false,
        systemType: Playlist.SystemPlaylistType? = null,
    ) = Playlist(
        id = "p1",
        name = "Playlist",
        iconUrl = "https://x/icon",
        shapeKey = "circle",
        isSystem = isSystem,
        systemType = systemType,
        isPinned = true,
        sortOrder = 3,
        trackCount = 10,
        autoDownload = true,
        createdAt = 100L,
        updatedAt = 200L,
    )

    @Test fun `user playlist round trip`() {
        val p = sample()
        val back = p.toEntity().toDomain()
        assertThat(back).isEqualTo(p)
    }

    @Test fun `system playlist round trip preserves system type`() {
        for (t in Playlist.SystemPlaylistType.entries) {
            val p = sample(isSystem = true, systemType = t)
            val back = p.toEntity().toDomain()
            assertThat(back.systemType).isEqualTo(t)
            assertThat(back.isSystem).isTrue()
        }
    }

    @Test fun `entity with unknown system type returns null type`() {
        val e = PlaylistEntity(
            id = "p1", name = "n", isSystem = true, systemType = "UNKNOWN_VALUE"
        )
        val p = e.toDomain()
        assertThat(p.systemType).isNull()
        assertThat(p.isSystem).isTrue()
    }

    @Test fun `domain without system type maps to null string`() {
        val p = sample().copy(systemType = null, isSystem = false)
        val e = p.toEntity()
        assertThat(e.systemType).isNull()
    }
}
