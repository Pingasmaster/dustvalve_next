package com.dustvalve.next.android.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dustvalve.next.android.domain.model.AudioFormat
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.model.TrackSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.pendingDownloadQueueDataStore: DataStore<androidx.datastore.preferences.core.Preferences>
    by preferencesDataStore(
        name = "pending_download_queue",
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
    )

/**
 * Durable snapshot of the in-memory [com.dustvalve.next.android.download.DownloadController]
 * queue. Survives process death so cold start can re-enqueue unfinished track
 * downloads. Schema: see docs/download-queue-schema.md (version 1).
 */
@Singleton
class PendingDownloadQueueStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val dataStore = context.pendingDownloadQueueDataStore
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun load(): PendingDownloadQueueV1 {
        val raw = try {
            dataStore.data.first()[KEY_QUEUE_JSON]
        } catch (e: IOException) {
            null
        }
        if (raw.isNullOrBlank()) return PendingDownloadQueueV1()
        return try {
            val parsed = json.decodeFromString(PendingDownloadQueueV1.serializer(), raw)
            if (parsed.version != PendingDownloadQueueV1.VERSION) PendingDownloadQueueV1() else parsed
        } catch (_: IllegalArgumentException) {
            PendingDownloadQueueV1()
        } catch (_: kotlinx.serialization.SerializationException) {
            PendingDownloadQueueV1()
        }
    }

    suspend fun save(queue: PendingDownloadQueueV1) {
        val payload = json.encodeToString(PendingDownloadQueueV1.serializer(), queue)
        dataStore.edit { prefs ->
            if (queue.items.isEmpty()) {
                prefs.remove(KEY_QUEUE_JSON)
            } else {
                prefs[KEY_QUEUE_JSON] = payload
            }
        }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(KEY_QUEUE_JSON) }
    }

    private companion object {
        val KEY_QUEUE_JSON = stringPreferencesKey("queue_json")
    }
}

/** Versioned pending-download queue document (DataStore JSON). */
@Serializable
data class PendingDownloadQueueV1(
    val version: Int = VERSION,
    val items: List<PendingDownloadItemV1> = emptyList(),
) {
    companion object {
        const val VERSION = 1
    }
}

/**
 * One pending track download. Carries enough metadata to rebuild a [Track]
 * without a Room lookup (album scrapes may not be in TrackDao yet).
 */
@Serializable
data class PendingDownloadItemV1(
    val trackId: String,
    val formatKey: String? = null,
    val albumId: String = "",
    val title: String = "",
    val artist: String = "",
    val artistUrl: String = "",
    val trackNumber: Int = 0,
    val duration: Float = 0f,
    val streamUrl: String? = null,
    val artUrl: String = "",
    val albumTitle: String = "",
    val source: String = TrackSource.BANDCAMP.key,
    val folderUri: String = "",
    val dateAdded: Long = 0L,
    val year: Int = 0,
    val albumUrl: String = "",
    val bandcampTrackUrl: String? = null,
) {
    fun toTrack(): Track = Track(
        id = trackId,
        albumId = albumId,
        title = title.ifBlank { trackId },
        artist = artist,
        artistUrl = artistUrl,
        trackNumber = trackNumber,
        duration = duration,
        streamUrl = streamUrl,
        artUrl = artUrl,
        albumTitle = albumTitle,
        source = TrackSource.fromKey(source),
        folderUri = folderUri,
        dateAdded = dateAdded,
        year = year,
        albumUrl = albumUrl,
        bandcampTrackUrl = bandcampTrackUrl,
    )

    fun formatOverride(): AudioFormat? = formatKey?.let { AudioFormat.fromKey(it) }

    companion object {
        fun fromTrack(track: Track, formatOverride: AudioFormat? = null) = PendingDownloadItemV1(
            trackId = track.id,
            formatKey = formatOverride?.key,
            albumId = track.albumId,
            title = track.title,
            artist = track.artist,
            artistUrl = track.artistUrl,
            trackNumber = track.trackNumber,
            duration = track.duration,
            streamUrl = track.streamUrl,
            artUrl = track.artUrl,
            albumTitle = track.albumTitle,
            source = track.source.key,
            folderUri = track.folderUri,
            dateAdded = track.dateAdded,
            year = track.year,
            albumUrl = track.albumUrl,
            bandcampTrackUrl = track.bandcampTrackUrl,
        )
    }
}
