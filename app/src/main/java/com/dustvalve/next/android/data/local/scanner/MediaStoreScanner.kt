package com.dustvalve.next.android.data.local.scanner

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.dustvalve.next.android.data.local.db.dao.TrackDao
import com.dustvalve.next.android.data.local.db.dao.deleteByIds
import com.dustvalve.next.android.data.local.db.entity.TrackEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaStoreScanner @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val trackDao: TrackDao,
) {

    companion object {
        private const val FOLDER_URI_SENTINEL = "mediastore"
    }

    suspend fun scan(): ScanResult = withContext(Dispatchers.IO) {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.DISPLAY_NAME,
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        val trackEntities = mutableListOf<TrackEntity>()
        val albumArtCache = mutableMapOf<Long, String>()

        context.contentResolver.query(
            collection,
            projection,
            selection,
            null,
            "${MediaStore.Audio.Media.TITLE} ASC",
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val displayNameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)

            while (cursor.moveToNext()) {
                ensureActive()
                val mediaId = cursor.getLong(idCol)
                val displayName = cursor.getString(displayNameCol) ?: continue
                val title = cursor.getString(titleCol)
                    ?: displayName.substringBeforeLast('.')
                val artist = cursor.getString(artistCol)
                    ?.takeIf { it != "<unknown>" }
                    ?: "Unknown Artist"
                val album = cursor.getString(albumCol)
                    ?.takeIf { it != "<unknown>" }
                    ?: "Unknown Album"
                val albumId = cursor.getLong(albumIdCol)
                val durationMs = cursor.getLong(durationCol)
                val trackNumber = cursor.getInt(trackCol).let { raw ->
                    // MediaStore TRACK can be encoded as DTTT (disc * 1000 + track)
                    if (raw > 1000) raw % 1000 else raw
                }

                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    mediaId,
                )

                val artUrl = albumArtCache.getOrPut(albumId) {
                    val artUri = ContentUris.withAppendedId(
                        "content://media/external/audio/albumart".toUri(),
                        albumId,
                    )
                    try {
                        val stream = context.contentResolver.openInputStream(artUri)
                        if (stream != null) {
                            stream.close()
                            artUri.toString()
                        } else {
                            ""
                        }
                    } catch (_: Exception) {
                        ""
                    }
                }

                trackEntities.add(
                    TrackEntity(
                        id = "local_ms_$mediaId",
                        albumId = "local_album_ms_$albumId",
                        title = title,
                        artist = artist,
                        artistUrl = "",
                        trackNumber = trackNumber,
                        duration = durationMs / 1000f,
                        streamUrl = contentUri.toString(),
                        artUrl = artUrl,
                        albumTitle = album,
                        source = "local",
                        folderUri = FOLDER_URI_SENTINEL,
                    ),
                )
            }
        }

        // Diff against existing MediaStore tracks in DB
        val existingIds = trackDao.getLocalTrackIdsByFolderSync(FOLDER_URI_SENTINEL).toSet()
        val scannedIds = trackEntities.map { it.id }.toSet()

        // Insert/update scanned tracks
        if (trackEntities.isNotEmpty()) {
            trackEntities.chunked(500).forEach { chunk ->
                trackDao.insertAll(chunk)
            }
        }

        // Remove tracks that no longer exist in MediaStore
        val removedIds = if (scannedIds.isEmpty() && existingIds.isNotEmpty()) {
            android.util.Log.w("MediaStoreScanner", "Scan returned 0 files but DB has ${existingIds.size} tracks — skipping deletion (possible permission issue)")
            emptySet()
        } else {
            existingIds - scannedIds
        }
        if (removedIds.isNotEmpty()) {
            trackDao.deleteByIds(removedIds)
        }

        ScanResult(
            added = (scannedIds - existingIds).size,
            removed = removedIds.size,
            total = trackEntities.size,
        )
    }

    private fun String.toUri(): android.net.Uri = android.net.Uri.parse(this)
}
