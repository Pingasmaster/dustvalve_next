package com.dustvalve.next.android.domain.repository

import com.dustvalve.next.android.domain.model.Album
import com.dustvalve.next.android.domain.model.AudioFormat
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
    suspend fun isTrackDownloaded(trackId: String): Boolean
    suspend fun getDownloadInfo(trackId: String): DownloadInfo?
    suspend fun deleteDownload(trackId: String)
    suspend fun deleteAlbumDownloads(albumId: String)
    fun getDownloadedTrackIds(): Flow<List<String>>
    fun getDownloadedAlbumIds(): Flow<List<String>>
}
