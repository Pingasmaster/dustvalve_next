package com.dustvalve.next.android.data.local.scanner

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import com.dustvalve.next.android.data.local.DatabaseGateway
import com.dustvalve.next.android.data.local.db.dao.TrackDao
import com.dustvalve.next.android.data.local.db.dao.deleteByIds
import com.dustvalve.next.android.data.local.db.entity.TrackEntity
import com.dustvalve.next.android.di.qualifiers.AppDispatchers
import com.dustvalve.next.android.di.qualifiers.Dispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaStoreScanner(private val context: Context, private val trackDao: TrackDao, private val ioDispatcher: CoroutineDispatcher) {

    @Inject constructor(
        @ApplicationContext context: Context,
        gateway: DatabaseGateway,
        @Dispatcher(AppDispatchers.IO) ioDispatcher: CoroutineDispatcher,
    ) : this(context, gateway.trackDao, ioDispatcher)

    companion object {
        private const val FOLDER_URI_SENTINEL = "mediastore"
    }

    suspend fun scan(): ScanResult = withContext(ioDispatcher) {
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
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.YEAR,
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
            val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)

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
                val dateAdded = cursor.getLong(dateAddedCol)
                val year = cursor.getInt(yearCol)

                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    mediaId,
                )

                // First track seen for an album resolves art for the whole album:
                // prefer embedded high-res cover, fall back to MediaStore albumart.
                val artUrl = albumArtCache.getOrPut(albumId) {
                    resolveAlbumArt(albumId, contentUri)
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
                        dateAdded = dateAdded,
                        year = year,
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
            android.util.Log.w(
                "MediaStoreScanner",
                "Scan returned 0 files but DB has ${existingIds.size} tracks - skipping deletion (possible permission issue)",
            )
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

    /**
     * Prefer embedded cover art from a sample track (full-res APIC/covr) over
     * MediaStore's often-downscaled albumart URI. Cache under local_art/ so
     * Coil and the player get a stable file:// URL.
     */
    private fun resolveAlbumArt(albumId: Long, sampleTrackUri: Uri): String {
        val artFile = File(context.filesDir, "local_art/local_ms_album_$albumId.jpg")
        val embedded = readEmbeddedPicture(sampleTrackUri)
        if (embedded != null && embedded.isNotEmpty()) {
            try {
                artFile.parentFile?.mkdirs()
                if (!artFile.exists() || artFile.length() != embedded.size.toLong()) {
                    artFile.writeBytes(embedded)
                }
                return Uri.fromFile(artFile).toString()
            } catch (_: Exception) {
                // Fall through to MediaStore albumart.
            }
        }
        return mediaStoreAlbumArtUri(albumId)
    }

    private fun readEmbeddedPicture(trackUri: Uri): ByteArray? {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(context, trackUri)
            mmr.embeddedPicture
        } catch (_: Exception) {
            null
        } finally {
            try {
                mmr.release()
            } catch (_: Exception) {
                // Ignore release errors
            }
        }
    }

    private fun mediaStoreAlbumArtUri(albumId: Long): String {
        val artUri = ContentUris.withAppendedId(
            "content://media/external/audio/albumart".toUri(),
            albumId,
        )
        return try {
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

    private fun String.toUri(): Uri = Uri.parse(this)
}
