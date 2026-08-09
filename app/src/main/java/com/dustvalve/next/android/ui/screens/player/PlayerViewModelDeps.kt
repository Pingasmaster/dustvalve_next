package com.dustvalve.next.android.ui.screens.player

import android.content.Context
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.domain.repository.DownloadRepository
import com.dustvalve.next.android.domain.repository.FavoriteRepository
import com.dustvalve.next.android.domain.repository.LibraryRepository
import com.dustvalve.next.android.domain.repository.PlaylistRepository
import com.dustvalve.next.android.domain.usecase.DownloadAlbumUseCase
import com.dustvalve.next.android.domain.usecase.ResolveTrackForPlaybackUseCase
import com.dustvalve.next.android.download.DownloadController
import com.dustvalve.next.android.player.PlaybackManager
import com.dustvalve.next.android.player.QueueManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Playback / resolve / settings deps shared by [PlayerViewModel] and coordinators. */
class PlayerCoreDeps @Inject constructor(
    val playbackManager: PlaybackManager,
    val queueManager: QueueManager,
    val libraryRepository: LibraryRepository,
    val downloadRepository: DownloadRepository,
    val settingsDataStore: SettingsDataStore,
    val resolveTrackForPlaybackUseCase: ResolveTrackForPlaybackUseCase,
    val playbackStreamResolver: PlaybackStreamResolver,
    @param:ApplicationContext val appContext: Context,
)

/** Library / download / playlist deps for [PlayerViewModel] and [PlayerLibraryCoordinator]. */
class PlayerLibraryDeps @Inject constructor(
    val downloadAlbumUseCase: DownloadAlbumUseCase,
    val downloadController: DownloadController,
    val playlistRepository: PlaylistRepository,
    val favoriteRepository: FavoriteRepository,
)
