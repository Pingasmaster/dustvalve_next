package com.dustvalve.next.android.data.local.scanner

import android.content.Context
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import com.dustvalve.next.android.data.local.db.dao.TrackDao
import com.dustvalve.next.android.data.local.db.dao.deleteByIds
import com.dustvalve.next.android.data.local.db.entity.TrackEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

data class ScanResult(
    val added: Int,
    val removed: Int,
    val total: Int,
)

@Singleton
class LocalMusicScanner @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val trackDao: TrackDao,
) {

    companion object {
        private val AUDIO_EXTENSIONS = setOf(
            "mp3", "flac", "m4a", "ogg", "wav", "opus", "aac", "wma", "alac",
        )
    }

    suspend fun scan(folderUri: Uri): ScanResult = withContext(Dispatchers.IO) {
        val audioFiles = mutableListOf<AudioFileInfo>()
        listAudioFilesRecursive(folderUri, folderUri, audioFiles)

        val trackEntities = audioFiles.mapNotNull { fileInfo ->
            ensureActive()
            extractTrackEntity(fileInfo)
        }

        // Get existing local track IDs before inserting
        val existingIds = trackDao.getLocalTrackIdsSync().toSet()
        val scannedIds = trackEntities.map { it.id }.toSet()

        // Insert/update scanned tracks
        if (trackEntities.isNotEmpty()) {
            trackEntities.chunked(500).forEach { chunk ->
                trackDao.insertAll(chunk)
            }
        }

        // Remove tracks whose files no longer exist
        val removedIds = existingIds - scannedIds
        if (removedIds.isNotEmpty()) {
            trackDao.deleteByIds(removedIds)
            // Clean up orphaned cover art
            removedIds.forEach { id ->
                getCoverArtFile(id).delete()
            }
        }

        ScanResult(
            added = (scannedIds - existingIds).size,
            removed = removedIds.size,
            total = trackEntities.size,
        )
    }

    private fun listAudioFilesRecursive(
        treeUri: Uri,
        parentUri: Uri,
        result: MutableList<AudioFileInfo>,
    ) {
        val docId = when {
            parentUri == treeUri -> DocumentsContract.getTreeDocumentId(treeUri)
            else -> DocumentsContract.getDocumentId(parentUri)
        }
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                ),
                null, null, null,
            )
            cursor?.use {
                while (it.moveToNext()) {
                    val childDocId = it.getString(0)
                    val displayName = it.getString(1) ?: continue
                    val mimeType = it.getString(2) ?: ""

                    val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)

                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        // Recurse into subdirectories
                        listAudioFilesRecursive(treeUri, childUri, result)
                    } else if (isAudioFile(displayName, mimeType)) {
                        result.add(AudioFileInfo(
                            documentId = childDocId,
                            displayName = displayName,
                            contentUri = childUri,
                            mimeType = mimeType,
                        ))
                    }
                }
            }
        } catch (_: Exception) {
            // Skip inaccessible directories
        }
    }

    private fun isAudioFile(displayName: String, mimeType: String): Boolean {
        if (mimeType.startsWith("audio/")) return true
        val extension = displayName.substringAfterLast('.', "").lowercase()
        return extension in AUDIO_EXTENSIONS
    }

    private fun extractTrackEntity(fileInfo: AudioFileInfo): TrackEntity? {
        val trackId = generateStableId(fileInfo.documentId)
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(context, fileInfo.contentUri)

            val title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?: fileInfo.displayName.substringBeforeLast('.')
            val artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                ?: "Unknown Artist"
            val albumTitle = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?: "Unknown Album"
            val durationMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val trackNumber = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                ?.split("/")?.firstOrNull()?.trim()?.toIntOrNull() ?: 0

            // Extract and cache cover art
            val artUrl = extractCoverArt(mmr, trackId) ?: ""

            val albumId = "local_album_" + md5Hash(albumTitle.lowercase()).take(12)

            TrackEntity(
                id = trackId,
                albumId = albumId,
                title = title,
                artist = artist,
                artistUrl = "",
                trackNumber = trackNumber,
                duration = durationMs / 1000f,
                streamUrl = fileInfo.contentUri.toString(),
                artUrl = artUrl,
                albumTitle = albumTitle,
                isLocal = true,
            )
        } catch (_: Exception) {
            // Skip files that can't be read
            null
        } finally {
            try {
                mmr.release()
            } catch (_: Exception) {
                // Ignore release errors
            }
        }
    }

    private fun extractCoverArt(mmr: MediaMetadataRetriever, trackId: String): String? {
        val artBytes = mmr.embeddedPicture ?: return null
        val artFile = getCoverArtFile(trackId)
        // Skip if art already cached (same track ID = same file)
        if (artFile.exists() && artFile.length() > 0) {
            return Uri.fromFile(artFile).toString()
        }
        return try {
            artFile.parentFile?.mkdirs()
            artFile.writeBytes(artBytes)
            Uri.fromFile(artFile).toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun getCoverArtFile(trackId: String): File {
        return File(context.filesDir, "local_art/$trackId.jpg")
    }

    private fun generateStableId(documentId: String): String {
        return "local_" + md5Hash(documentId).take(16)
    }

    private fun md5Hash(input: String): String {
        return MessageDigest.getInstance("MD5")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private data class AudioFileInfo(
        val documentId: String,
        val displayName: String,
        val contentUri: Uri,
        val mimeType: String,
    )
}
