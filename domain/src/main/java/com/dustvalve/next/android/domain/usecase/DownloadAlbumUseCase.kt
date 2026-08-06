package com.dustvalve.next.android.domain.usecase

import com.dustvalve.next.android.domain.model.Album
import com.dustvalve.next.android.domain.model.Artist
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.repository.AlbumRepository
import com.dustvalve.next.android.domain.repository.DownloadProgressReporter
import com.dustvalve.next.android.domain.repository.DownloadRepository
import com.dustvalve.next.android.download.downloadEachDeferringFailures
import java.io.IOException
import javax.inject.Inject

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
            // Skip a failing track immediately, retry it once after the rest
            // of the playlist, and only then give up on it (mirrors
            // downloadAlbumInner). The aggregate failure is still surfaced so
            // an all-failed playlist doesn't report success.
            val result = downloadEachDeferringFailures(tracks) { track ->
                downloadRepository.downloadTrack(track)
            }
            if (result.hasUnavailable) {
                throw IOException(
                    "Failed to download ${result.unavailable.size} of ${tracks.size} tracks: ${result.error?.message}",
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
        // upfront. An album whose detail fetch fails twice is dropped and
        // counted as zero tracks, exactly like an album that fails to
        // download - both feed the all-albums-failed check below.
        val resolved = mutableListOf<Album>()
        val resolveResult = downloadEachDeferringFailures(artist.albums) { albumStub ->
            resolved += albumRepository.getAlbumDetail(albumStub.url)
        }

        val totalTracks = resolved.sumOf { it.tracks.count { t -> t.streamUrl != null } }
        var lostAlbums = resolveResult.unavailable.size
        var firstError: Throwable? = resolveResult.error
        notificationCenter.withBatch(
            label = artist.name,
            totalTracks = totalTracks,
            kind = DownloadProgressReporter.BatchKind.ARTIST,
        ) {
            // Same rule one level up: a failing album is skipped now and
            // retried once at the end of the artist. Tracks it already got
            // are cheap to re-walk - downloadTrackInner short-circuits on
            // same-or-higher quality.
            val result = downloadEachDeferringFailures(resolved) { album ->
                downloadRepository.downloadAlbum(album)
            }
            lostAlbums += result.unavailable.size
            if (firstError == null) firstError = result.error
        }
        if (lostAlbums == artist.albums.size) {
            throw IOException(
                "Failed to download all ${artist.albums.size} albums: ${firstError?.message}",
            )
        }
    }
}
