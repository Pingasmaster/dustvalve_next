package com.dustvalve.next.android.ui.screens.player

import com.dustvalve.next.android.R
import com.dustvalve.next.android.domain.model.AudioFormat
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.model.TrackSource
import com.dustvalve.next.android.util.NetworkUtils
import com.dustvalve.next.android.util.UiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    private val settingsDataStore = core.settingsDataStore
    private val resolveTrackForPlaybackUseCase = core.resolveTrackForPlaybackUseCase
    private val playbackStreamResolver = core.playbackStreamResolver
    private val appContext = core.appContext
    private var progressiveDownloadJob: Job? = null
    private var playJob: Job? = null
    private var loadingGeneration = 0

    private suspend fun resolveTrackForPlayback(track: Track, updateState: Boolean = true): Track {
        val result = resolveTrackForPlaybackUseCase(track, reportFailure = updateState)
        if (result.recordedRemoteResolution) {
            playbackStreamResolver.recordResolved(result.track.id)
        }
        if (updateState) {
            extraState.update {
                var next = it.copy(
                    currentPlaybackFormat = result.playbackFormat,
                    currentSourcePath = result.sourcePath,
                )
                if (result.streamFailed) {
                    next = next.copy(
                        snackbarMessage = UiText.StringResource(R.string.snackbar_audio_stream_failed),
                        isSnackbarError = true,
                    )
                }
                next
            }
        }
        return result.track
    }

    private fun triggerProgressiveDownload(track: Track) {
        if (track.isLocal) return
        progressiveDownloadJob?.cancel()
        progressiveDownloadJob = scope.launch {
            runPlayerUiAction {
                val progressiveEnabled = settingsDataStore.getProgressiveDownloadSync()
                if (!progressiveEnabled) return@runPlayerUiAction

                val existingDownload = downloadRepository.getDownloadInfo(track.id)
                if (existingDownload != null && existingDownload.format.qualityRank > AudioFormat.MP3_128.qualityRank) {
                    precacheNextTrack()
                    return@runPlayerUiAction
                }

                val formatOverride = if (track.source != TrackSource.YOUTUBE) {
                    val saveOnMetered = settingsDataStore.getSaveDataOnMeteredSync()
                    if (saveOnMetered && NetworkUtils.isMeteredConnection(appContext)) {
                        AudioFormat.MP3_320
                    } else {
                        null
                    }
                } else {
                    null
                }

                downloadRepository.downloadTrack(track, formatOverride)

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

        runPlayerUiAction {
            val formatOverride = if (nextTrack.source != TrackSource.YOUTUBE) {
                val saveOnMetered = settingsDataStore.getSaveDataOnMeteredSync()
                if (saveOnMetered && NetworkUtils.isMeteredConnection(appContext)) {
                    AudioFormat.MP3_320
                } else {
                    null
                }
            } else {
                null
            }
            downloadRepository.downloadTrack(nextTrack, formatOverride)
        }
    }

    fun playTrack(track: Track) {
        val generation = ++loadingGeneration
        playJob?.cancel()
        playJob = scope.launch {
            val isYouTubeStream = track.source == TrackSource.YOUTUBE &&
                downloadRepository.getDownloadInfo(track.id) == null

            if (isYouTubeStream) {
                playbackManager.pause()
                extraState.update { it.copy(isLoadingTrack = true) }
            }

            val resolved = try {
                resolveTrackForPlayback(track)
            } finally {
                if (isYouTubeStream && generation == loadingGeneration) {
                    extraState.update { it.copy(isLoadingTrack = false) }
                }
            }

            if (resolved.streamUrl == null) return@launch
            queueManager.setQueue(listOf(resolved), 0)
            playbackManager.playTrack(resolved)
            triggerProgressiveDownload(track)
            runPlayerUiAction {
                libraryRepository.addToRecent(track)
                if (track.source == TrackSource.YOUTUBE) {
                    settingsDataStore.setLastYoutubeVideoId(track.id.removePrefix("yt_"))
                }
            }
        }
    }

    fun playTrackInList(tracks: List<Track>, index: Int) {
        val generation = ++loadingGeneration
        playJob?.cancel()
        playJob = scope.launch {
            val targetTrack = tracks[index]
            val isYouTubeStream = targetTrack.source == TrackSource.YOUTUBE &&
                downloadRepository.getDownloadInfo(targetTrack.id) == null

            if (isYouTubeStream) {
                playbackManager.pause()
                queueManager.setQueue(tracks, index)
                extraState.update { it.copy(isLoadingTrack = true) }
            }

            val resolvedTarget = try {
                resolveTrackForPlayback(targetTrack)
            } finally {
                if (isYouTubeStream && generation == loadingGeneration) {
                    extraState.update { it.copy(isLoadingTrack = false) }
                }
            }

            val queueTracks = tracks.toMutableList().also { it[index] = resolvedTarget }
            playbackManager.playQueue(queueTracks, index)
            triggerProgressiveDownload(targetTrack)
            runPlayerUiAction {
                libraryRepository.addToRecent(targetTrack)
            }
            resolveRemainingTracks(queueTracks, index)
        }
    }

    fun playAlbum(tracks: List<Track>, startIndex: Int) {
        val generation = ++loadingGeneration
        playJob?.cancel()
        playJob = scope.launch {
            val targetTrack = tracks[startIndex]
            val isYouTubeStream = targetTrack.source == TrackSource.YOUTUBE &&
                downloadRepository.getDownloadInfo(targetTrack.id) == null

            if (isYouTubeStream) {
                playbackManager.pause()
                queueManager.setQueue(tracks, startIndex)
                extraState.update { it.copy(isLoadingTrack = true) }
            }

            val resolvedTarget = try {
                resolveTrackForPlayback(targetTrack)
            } finally {
                if (isYouTubeStream && generation == loadingGeneration) {
                    extraState.update { it.copy(isLoadingTrack = false) }
                }
            }

            val queueTracks = tracks.toMutableList().also { it[startIndex] = resolvedTarget }
            playbackManager.playQueue(queueTracks, startIndex)
            triggerProgressiveDownload(targetTrack)
            runPlayerUiAction {
                libraryRepository.addToRecent(targetTrack)
            }
            resolveRemainingTracks(queueTracks, startIndex)
        }
    }

    private suspend fun resolveRemainingTracks(tracks: List<Track>, skipIndex: Int) {
        for (i in tracks.indices) {
            if (i == skipIndex) continue
            val original = tracks[i]
            val resolved = resolveTrackForPlayback(original, updateState = false)
            if (resolved != original) {
                queueManager.applyResolvedTracks(mapOf(resolved.id to resolved))
            }
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
}
