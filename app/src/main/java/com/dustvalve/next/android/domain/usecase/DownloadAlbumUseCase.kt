package com.dustvalve.next.android.domain.usecase

import com.dustvalve.next.android.domain.model.Album
import com.dustvalve.next.android.domain.model.Artist
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.repository.AlbumRepository
import com.dustvalve.next.android.domain.repository.DownloadRepository
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

class DownloadAlbumUseCase @Inject constructor(
    private val downloadRepository: DownloadRepository,
    private val albumRepository: AlbumRepository,
) {
    suspend operator fun invoke(album: Album) {
        downloadRepository.downloadAlbum(album)
    }

    suspend fun downloadTrack(track: Track) {
        downloadRepository.downloadTrack(track)
    }

    suspend fun deleteAlbumDownloads(albumId: String) {
        downloadRepository.deleteAlbumDownloads(albumId)
    }

    suspend fun deleteTrackDownload(trackId: String) {
        downloadRepository.deleteDownload(trackId)
    }

    suspend fun deleteArtistDownloads(artist: Artist) {
        for (album in artist.albums) {
            downloadRepository.deleteAlbumDownloads(album.id)
        }
    }

    suspend fun downloadArtist(artist: Artist) {
        if (artist.albums.isEmpty()) {
            throw IOException("No albums to download for ${artist.name}")
        }
        val errors = mutableListOf<Exception>()
        for (albumStub in artist.albums) {
            try {
                val album = albumRepository.getAlbumDetail(albumStub.url)
                downloadRepository.downloadAlbum(album)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errors.add(e)
            }
        }
        if (errors.size == artist.albums.size) {
            throw IOException(
                "Failed to download all ${artist.albums.size} albums: ${errors.first().message}"
            )
        }
    }
}
