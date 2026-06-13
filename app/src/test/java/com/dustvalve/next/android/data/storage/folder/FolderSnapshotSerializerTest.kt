package com.dustvalve.next.android.data.storage.folder

import com.dustvalve.next.android.data.local.db.entity.TrackEntity
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FolderSnapshotSerializerTest {

    private val track = TrackEntity(
        id = "t1",
        albumId = "a1",
        title = "Title",
        artist = "Artist",
        artistUrl = "https://artist.bandcamp.com",
        trackNumber = 3,
        duration = 123.5f,
        streamUrl = "https://example.com/stream",
        artUrl = "https://example.com/art.jpg",
        albumTitle = "Album",
        source = "bandcamp",
        folderUri = "content://tree/folder",
        dateAdded = 42L,
        year = 2024,
        albumUrl = "https://artist.bandcamp.com/album/x",
        bandcampTrackUrl = "https://artist.bandcamp.com/track/y",
    )

    @Test
    fun `track entity round-trips losslessly through snapshot`() {
        assertThat(track.toSnapshot().toEntity()).isEqualTo(track)
    }

    @Test
    fun `track entity round-trips losslessly through json`() {
        val json = FolderSnapshotSerializer.json
        val encoded = json.encodeToString(TracksFile.serializer(), TracksFile(listOf(track.toSnapshot())))
        val decoded = json.decodeFromString(TracksFile.serializer(), encoded)
        assertThat(decoded.tracks.single().toEntity()).isEqualTo(track)
    }

    @Test
    fun `null bandcampTrackUrl survives round-trip`() {
        val local = track.copy(source = "local", bandcampTrackUrl = null)
        assertThat(local.toSnapshot().toEntity()).isEqualTo(local)
    }
}
