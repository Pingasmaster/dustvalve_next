package com.dustvalve.next.android.domain.repository

import com.dustvalve.next.android.domain.model.Album
import com.dustvalve.next.android.domain.model.AudioFormat
import com.dustvalve.next.android.domain.model.Track
import kotlinx.coroutines.flow.Flow

data class DownloadInfo(
    /**
     * Absolute filesystem path under app-internal downloads. Prefer
     * [streamUri] when handing the path to ExoPlayer.
     */
    val filePath: String,
    val format: AudioFormat,
) {
    /** ExoPlayer-ready `file://` URI for [filePath]. */
    val streamUri: String
        get() = "file://$filePath"
}

interface DownloadRepository {
    suspend fun downloadAlbum(album: Album)
    suspend fun downloadTrack(track: Track, formatOverride: AudioFormat? = null)
    suspend fun isTrackDownloaded(trackId: String): Boolean
    suspend fun getDownloadInfo(trackId: String): DownloadInfo?
    suspend fun deleteDownload(trackId: String)
    suspend fun deleteAlbumDownloads(albumId: String)

    /**
     * Wipes the entire downloads pool: every track audio file, the Coil
     * image disk cache, and ExoPlayer's media_cache. Safe to call multiple
     * times; never throws on missing files.
     */
    suspend fun clearAll()
    fun getDownloadedTrackIds(): Flow<List<String>>
    fun getDownloadedAlbumIds(): Flow<List<String>>

    /**
     * Every recorded download file path, raw and unfiltered (blank rows
     * included); callers own filtering and error handling
     * (DownloadController's orphan-file reconciliation).
     */
    suspend fun getAllDownloadFilePaths(): List<String>

    /**
     * Deletes download rows whose file no longer exists on disk. Returns the
     * number of rows removed. Used by the cold-start sweep so the UI cannot
     * show a phantom "downloaded" badge for a missing file.
     */
    suspend fun purgeOrphanDownloadRows(): Int
}
