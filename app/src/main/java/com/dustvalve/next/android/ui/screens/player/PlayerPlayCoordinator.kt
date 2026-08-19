package com.dustvalve.next.android.ui.screens.player

import com.dustvalve.next.android.R
import com.dustvalve.next.android.domain.model.AudioFormat
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.model.TrackSource
import com.dustvalve.next.android.domain.repository.DownloadInfo
import com.dustvalve.next.android.domain.usecase.PlaybackResolveResult
import com.dustvalve.next.android.util.UiText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/** Play / resolve / progressive-download orchestration for the player. */
internal class PlayerPlayCoordinator(
    private val scope: CoroutineScope,
    private val extraState: PlayerExtraStateFlow,
    core: PlayerCoreDeps,
) {
    private val playbackManager = core.playbackManager
    private val queueManager = core.queueManager
    private val libraryRepository = core.libraryRepository
    private val downloadRepository = core.downloadRepository
    private val downloadController = core.downloadController
    private val settingsDataStore = core.settingsDataStore
    private val playbackAudioTuning = core.playbackAudioTuning
    private val resolveTrackForPlaybackUseCase = core.resolveTrackForPlaybackUseCase
    private val playbackStreamResolver = core.playbackStreamResolver
    private var progressiveDownloadJob: Job? = null
    private var playJob: Job? = null
    private var loadingGeneration = 0

    private suspend fun resolveTrackForPlayback(track: Track, updateState: Boolean = true): PlaybackResolveResult {
        val result = resolveTrackForPlaybackUseCase(track, reportFailure = updateState)
        if (result.recordedRemoteResolution) {
            playbackStreamResolver.recordResolved(result.track.id)
        }
        if (updateState) {
            applyResolveState(result)
        }
        return result
    }

    private fun applyResolveState(result: PlaybackResolveResult) {
        extraState.update {
            var next = it.copy(
                currentPlaybackFormat = result.playbackFormat,
                currentSourcePath = result.sourcePath,
            )
            if (result.streamFailed) {
                next = next.copy(
                    snackbarMessage = streamFailedUiText(result.streamFailedMessage),
                    isSnackbarError = true,
                )
            }
            next
        }
    }

    private fun triggerProgressiveDownload(track: Track) {
        if (track.isLocal) return
        progressiveDownloadJob?.cancel()
        progressiveDownloadJob = scope.launch {
            runPlayerUiAction {
                if (!settingsDataStore.getProgressiveDownloadSync()) return@runPlayerUiAction
                if (!settingsDataStore.getBackgroundAutoDownloadSync()) return@runPlayerUiAction
                // Bluetooth stability: keep the radio free for A2DP/LDAC.
                if (playbackAudioTuning.shouldPauseDownloadsWhilePlaying()) return@runPlayerUiAction

                // Give ExoPlayer time to fill its start buffer before a full-file
                // download competes for the same CDN / radio. High-bitrate streams
                // + LDAC are especially sensitive to early bandwidth contention.
                delay(PROGRESSIVE_DOWNLOAD_START_DELAY_MS)

                val existingDownload = downloadRepository.getDownloadInfo(track.id)
                if (existingDownload != null && existingDownload.format.qualityRank > AudioFormat.MP3_128.qualityRank) {
                    precacheNextTrack()
                    return@runPlayerUiAction
                }

                // Route through DownloadController so progressive work shares the
                // FGS + pause/cancel notification path with explicit downloads
                // (and de-dupes against an in-flight enqueue for the same track).
                downloadController.downloadTrackBlocking(track)

                val downloadInfo = downloadRepository.getDownloadInfo(track.id)
                if (downloadInfo != null) {
                    val currentTrack = queueManager.currentTrack.value
                    if (currentTrack != null && currentTrack.id == track.id) {
                        val seamlessUpgrade = settingsDataStore.getSeamlessQualityUpgradeSync()
                        if (seamlessUpgrade) {
                            playbackManager.hotSwapSource(downloadInfo.streamUri, track.id)
                        }
                        extraState.update {
                            it.copy(
                                currentPlaybackFormat = downloadInfo.format,
                                currentSourcePath = downloadInfo.filePath,
                            )
                        }
                    }
                }

                precacheNextTrack()
            }
        }
    }

    private suspend fun precacheNextTrack() {
        val queue = queueManager.queue.value
        val currentIndex = queueManager.currentIndex.value
        val nextTrack = queue.getOrNull(currentIndex + 1) ?: return

        val existing = downloadRepository.getDownloadInfo(nextTrack.id)
        if (existing != null && existing.format.qualityRank >= AudioFormat.MP3_128.qualityRank) return
        if (extraState.value.downloadingTrackId == nextTrack.id) return

        // Fire-and-forget through the shared queue; no need to block playback.
        downloadController.enqueueTrack(nextTrack)
    }

    /**
     * Resolves [track] and starts playback. Returns true when a playable URL
     * was handed to ExoPlayer. Cancels any in-flight play job.
     */
    suspend fun playTrackAwaiting(track: Track): Boolean {
        val generation = ++loadingGeneration
        playJob?.cancel()
        val result = CompletableDeferred<Boolean>()
        playJob = scope.launch {
            try {
                result.complete(playResolvedTrack(track, generation))
            } catch (e: CancellationException) {
                result.complete(false)
                throw e
            }
        }
        return try {
            result.await()
        } catch (_: CancellationException) {
            false
        }
    }

    fun playTrack(track: Track) {
        val generation = ++loadingGeneration
        playJob?.cancel()
        playJob = scope.launch {
            playResolvedTrack(track, generation)
        }
    }

    private suspend fun playResolvedTrack(track: Track, generation: Int): Boolean {
        if (!track.isLocal && !settingsDataStore.getProgressiveDownloadSync()) {
            return playViaDownload(track, generation)
        }

        val isYouTubeStream = track.source == TrackSource.YOUTUBE &&
            downloadRepository.getDownloadInfo(track.id) == null

        // C1: do not pause current playback until resolve succeeds. A failed
        // YouTube tap must leave the previous track playing.
        extraState.update {
            it.copy(
                isLoadingTrack = isYouTubeStream,
                blockingDownloadTrackId = null,
                downloadProgressFraction = null,
            )
        }

        val result = try {
            resolveTrackForPlayback(track)
        } finally {
            if (isYouTubeStream && generation == loadingGeneration) {
                extraState.update { it.copy(isLoadingTrack = false) }
            }
        }

        val resolved = result.track
        if (resolved.streamUrl.isNullOrBlank()) return false
        queueManager.setQueue(listOf(resolved), 0)
        playbackManager.playTrack(resolved)
        triggerProgressiveDownload(track)
        runPlayerUiAction {
            libraryRepository.addToRecent(track)
            if (track.source == TrackSource.YOUTUBE) {
                settingsDataStore.setLastYoutubeVideoId(track.id.removePrefix("yt_"))
            }
        }
        return true
    }

    /**
     * Resolves and starts list playback. Returns true when a playable entry
     * from [index] onward was started (skips blank/failed start indices).
     */
    suspend fun playTrackInListAwaiting(tracks: List<Track>, index: Int): Boolean {
        if (tracks.getOrNull(index) == null) {
            extraState.update {
                it.copy(
                    snackbarMessage = UiText.StringResource(R.string.common_failed_to_play),
                    isSnackbarError = true,
                )
            }
            return false
        }
        val generation = ++loadingGeneration
        playJob?.cancel()
        val result = CompletableDeferred<Boolean>()
        playJob = scope.launch {
            try {
                result.complete(playListFromIndex(tracks, index, generation))
            } catch (e: CancellationException) {
                result.complete(false)
                throw e
            }
        }
        return try {
            result.await()
        } catch (_: CancellationException) {
            false
        }
    }

    fun playTrackInList(tracks: List<Track>, index: Int) {
        if (tracks.getOrNull(index) == null) {
            extraState.update {
                it.copy(
                    snackbarMessage = UiText.StringResource(R.string.common_failed_to_play),
                    isSnackbarError = true,
                )
            }
            return
        }
        val generation = ++loadingGeneration
        playJob?.cancel()
        playJob = scope.launch {
            playListFromIndex(tracks, index, generation)
        }
    }

    fun playAlbum(tracks: List<Track>, startIndex: Int) {
        playTrackInList(tracks, startIndex)
    }

    suspend fun playAlbumAwaiting(tracks: List<Track>, startIndex: Int): Boolean = playTrackInListAwaiting(tracks, startIndex)

    /**
     * Like [com.dustvalve.next.android.player.PlaybackMediaPreparer.resolveAndPlay]:
     * walk forward from [startIndex] until a playable URL resolves, then
     * [playQueue] there. Leaves the prior queue intact when every candidate
     * from [startIndex] fails.
     */
    private suspend fun playListFromIndex(tracks: List<Track>, startIndex: Int, generation: Int): Boolean {
        val first = tracks[startIndex]
        if (!first.isLocal && !settingsDataStore.getProgressiveDownloadSync()) {
            return playListViaDownload(tracks, startIndex, generation)
        }

        val showYtLoading = first.source == TrackSource.YOUTUBE &&
            downloadRepository.getDownloadInfo(first.id) == null
        extraState.update {
            it.copy(
                isLoadingTrack = showYtLoading,
                blockingDownloadTrackId = null,
                downloadProgressFraction = null,
            )
        }

        try {
            for (i in startIndex until tracks.size) {
                currentCoroutineContext().ensureActive()
                val candidate = tracks[i]
                // Report snackbar for the user-requested index (age-gate etc.);
                // silent skip for successors so Play All is not a snackbar storm.
                val result = resolveTrackForPlayback(candidate, updateState = i == startIndex)
                val resolved = result.track
                if (resolved.streamUrl.isNullOrBlank()) continue

                if (i != startIndex) {
                    applyResolveState(result)
                }
                clearYtLoading(showYtLoading, generation)

                val queueTracks = tracks.toMutableList().also { it[i] = resolved }
                playbackManager.playQueue(queueTracks, i)
                triggerProgressiveDownload(candidate)
                runPlayerUiAction {
                    libraryRepository.addToRecent(candidate)
                }
                resolveRemainingTracks(queueTracks, i)
                return true
            }
        } finally {
            clearYtLoading(showYtLoading, generation)
        }

        if (extraState.value.snackbarMessage == null) {
            extraState.update {
                it.copy(
                    snackbarMessage = UiText.StringResource(R.string.common_failed_to_play),
                    isSnackbarError = true,
                )
            }
        }
        return false
    }

    private fun clearYtLoading(showYtLoading: Boolean, generation: Int) {
        if (showYtLoading && generation == loadingGeneration) {
            extraState.update { it.copy(isLoadingTrack = false) }
        }
    }

    private suspend fun resolveRemainingTracks(tracks: List<Track>, skipIndex: Int) {
        val from = (skipIndex - RESOLVE_REMAINING_WINDOW).coerceAtLeast(0)
        val to = (skipIndex + RESOLVE_REMAINING_WINDOW).coerceAtMost(tracks.lastIndex)
        for (i in from..to) {
            currentCoroutineContext().ensureActive()
            if (i == skipIndex) continue
            val original = tracks[i]
            val resolved = resolveTrackForPlayback(original, updateState = false).track
            if (resolved != original) {
                queueManager.applyResolvedTracks(mapOf(resolved.id to resolved))
            }
        }
    }

    private suspend fun playViaDownload(track: Track, generation: Int): Boolean {
        queueManager.setQueue(listOf(track), 0)
        val info = ensureDownloadedForPlayback(track, generation)
        if (info == null) {
            if (generation == loadingGeneration) {
                failBlockingDownload()
            }
            return false
        }
        return startDownloadedTrack(track, info, listOf(track), 0, generation)
    }

    private suspend fun playListViaDownload(tracks: List<Track>, startIndex: Int, generation: Int): Boolean {
        for (i in startIndex until tracks.size) {
            currentCoroutineContext().ensureActive()
            val candidate = tracks[i]
            if (candidate.isLocal) {
                return startLocalTrackInList(tracks, i, generation)
            }
            queueManager.setQueue(tracks, i)
            val info = ensureDownloadedForPlayback(candidate, generation)
            if (info == null) continue
            return startDownloadedTrack(candidate, info, tracks, i, generation)
        }
        if (generation == loadingGeneration) {
            failBlockingDownload()
        }
        return false
    }

    private suspend fun ensureDownloadedForPlayback(track: Track, generation: Int): DownloadInfo? {
        downloadRepository.getDownloadInfo(track.id)?.let { return it }
        // Stop the previous track so the player can show this download.
        playbackManager.pause()
        extraState.update {
            it.copy(
                isLoadingTrack = true,
                blockingDownloadTrackId = track.id,
                downloadProgressFraction = 0f,
            )
        }
        val failed = try {
            downloadController.downloadTrackBlocking(track)
            false
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            true
        } catch (_: SecurityException) {
            true
        } catch (_: IllegalStateException) {
            true
        } catch (_: IllegalArgumentException) {
            true
        } catch (_: android.database.SQLException) {
            true
        } catch (_: kotlinx.serialization.SerializationException) {
            true
        }
        if (failed || generation != loadingGeneration) return null
        return downloadRepository.getDownloadInfo(track.id)
    }

    private suspend fun startLocalTrackInList(tracks: List<Track>, index: Int, generation: Int): Boolean {
        if (generation != loadingGeneration) return false
        extraState.update {
            it.copy(
                isLoadingTrack = false,
                blockingDownloadTrackId = null,
                downloadProgressFraction = null,
            )
        }
        playbackManager.playQueue(tracks, index)
        runPlayerUiAction {
            libraryRepository.addToRecent(tracks[index])
        }
        if (settingsDataStore.getBackgroundAutoDownloadSync()) {
            precacheNextTrack()
        }
        return true
    }

    private suspend fun startDownloadedTrack(track: Track, info: DownloadInfo, tracks: List<Track>, index: Int, generation: Int): Boolean {
        if (generation != loadingGeneration) return false
        val resolved = track.copy(streamUrl = info.streamUri)
        val queueTracks = tracks.toMutableList().also { it[index] = resolved }
        playbackManager.playQueue(queueTracks, index)
        extraState.update {
            it.copy(
                isLoadingTrack = false,
                blockingDownloadTrackId = null,
                downloadProgressFraction = null,
                currentPlaybackFormat = info.format,
                currentSourcePath = info.filePath,
            )
        }
        runPlayerUiAction {
            libraryRepository.addToRecent(track)
            if (track.source == TrackSource.YOUTUBE) {
                settingsDataStore.setLastYoutubeVideoId(track.id.removePrefix("yt_"))
            }
        }
        if (settingsDataStore.getBackgroundAutoDownloadSync()) {
            precacheNextTrack()
        }
        return true
    }

    private fun failBlockingDownload() {
        extraState.update {
            it.copy(
                isLoadingTrack = false,
                blockingDownloadTrackId = null,
                downloadProgressFraction = null,
                snackbarMessage = UiText.StringResource(R.string.common_failed_to_play),
                isSnackbarError = true,
            )
        }
    }

    fun skipToQueueIndex(index: Int) {
        playbackManager.skipToQueueIndex(index)
        scope.launch {
            runPlayerUiAction {
                queueManager.queue.value.getOrNull(index)?.let { libraryRepository.addToRecent(it) }
            }
        }
    }

    companion object {
        /** Indices around the playing entry to pre-resolve after list play. */
        internal const val RESOLVE_REMAINING_WINDOW = 5

        /** Pause before progressive download so ExoPlayer can fill its start buffer. */
        internal const val PROGRESSIVE_DOWNLOAD_START_DELAY_MS = 5_000L

        internal fun streamFailedUiText(detail: String?): UiText = when {
            detail?.contains("LOGIN_REQUIRED") == true ->
                UiText.StringResource(R.string.snackbar_youtube_login_required)

            !detail.isNullOrBlank() -> UiText.DynamicString(detail)

            else -> UiText.StringResource(R.string.snackbar_audio_stream_failed)
        }
    }
}
