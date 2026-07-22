package com.dustvalve.next.android.domain.usecase

import com.dustvalve.next.android.domain.model.Album
import com.dustvalve.next.android.domain.model.Artist
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.repository.AlbumRepository
import com.dustvalve.next.android.domain.repository.DownloadProgressReporter
import com.dustvalve.next.android.domain.repository.DownloadRepository
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

class DownloadAlbumUseCase @Inject constructor(
    private val downloadRepository: DownloadRepository,
    private val albumRepository: AlbumRepository,
    private val notificationCenter: DownloadProgressReporter,
) {
    suspend operator fun invoke(album: Album) {
        downloadRepository.downloadAlbum(album)
    }

    suspend fun downloadTrack(track: Track) {
        downloadRepository.downloadTrack(track)
    }

    suspend fun downloadPlaylist(label: String, tracks: List<Track>) {
        if (tracks.isEmpty()) return
        notificationCenter.withBatch(
            label = label,
            totalTracks = tracks.size,
            kind = DownloadProgressReporter.BatchKind.PLAYLIST,
        ) {
            val errors = mutableListOf<Exception>()
            for (track in tracks) {
                try {
                    downloadRepository.downloadTrack(track)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Best-effort: continue with the remaining tracks, then
                    // surface the aggregate failure below (mirrors
                    // downloadAlbumInner) so an all-failed playlist doesn't
                    // report success.
                    errors.add(e)
                }
            }
            if (errors.isNotEmpty()) {
                throw IOException(
                    "Failed to download ${errors.size} of ${tracks.size} tracks: ${errors.first().message}",
                )
            }
        }
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
        // Pre-load album details so the batch knows the total track count
        // upfront. Failed loads are dropped silently and counted as zero
        // tracks; the per-album catch below still surfaces them as errors.
        val resolved = mutableListOf<Album>()
        val errors = mutableListOf<Exception>()
        for (albumStub in artist.albums) {
            try {
                resolved += albumRepository.getAlbumDetail(albumStub.url)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errors.add(e)
            }
        }
        val totalTracks = resolved.sumOf { it.tracks.count { t -> t.streamUrl != null } }
        notificationCenter.withBatch(
            label = artist.name,
            totalTracks = totalTracks,
            kind = DownloadProgressReporter.BatchKind.ARTIST,
        ) {
            for (album in resolved) {
                try {
                    downloadRepository.downloadAlbum(album)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    errors.add(e)
                }
            }
        }
        if (errors.size == artist.albums.size) {
            throw IOException(
                "Failed to download all ${artist.albums.size} albums: ${errors.first().message}",
            )
        }
    }
}
