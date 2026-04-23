package com.dustvalve.next.android.domain.repository

import com.dustvalve.next.android.domain.model.Album
import com.dustvalve.next.android.domain.model.AudioFormat
import com.dustvalve.next.android.domain.model.ExportableTrack
import com.dustvalve.next.android.domain.model.Track
import kotlinx.coroutines.flow.Flow

data class DownloadInfo(
    val filePath: String,
    val format: AudioFormat,
)

interface DownloadRepository {
    suspend fun downloadAlbum(album: Album)
    suspend fun downloadTrack(track: Track, formatOverride: AudioFormat? = null)
    fun getDownloadedAlbums(): Flow<List<Album>>
    fun getDownloadedTracks(): Flow<List<Track>>
    fun getExportableTracks(): Flow<List<ExportableTrack>>
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
    suspend fun exportDownloads(
        destinationUri: String,
        trackIds: Set<String>? = null,
        onProgress: (exported: Int, total: Int) -> Unit,
    ): Int
}
