package com.dustvalve.next.android.di

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.dustvalve.next.android.MainActivity
import com.dustvalve.next.android.R
import com.dustvalve.next.android.di.qualifiers.MediaHttp
import com.dustvalve.next.android.domain.repository.LibraryRepository
import com.dustvalve.next.android.player.MediaSessionConstants
import com.dustvalve.next.android.player.PlaybackManager
import com.dustvalve.next.android.player.QueueForwardingPlayer
import com.dustvalve.next.android.player.QueueManager
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PlayerModule {

    private const val MAX_CACHE_SIZE_BYTES = 512L * 1024L * 1024L // 512 MB

    @OptIn(UnstableApi::class)
    @Provides
    @Singleton
    fun provideSimpleCache(@ApplicationContext context: Context): SimpleCache {
        val cacheDir = File(context.cacheDir, "media_cache")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        val evictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_SIZE_BYTES)
        return SimpleCache(cacheDir, evictor, androidx.media3.database.StandaloneDatabaseProvider(context))
    }

    @OptIn(UnstableApi::class)
    @Provides
    @Singleton
    fun provideExoPlayer(
        @ApplicationContext context: Context,
        // MediaHttp: no callTimeout. The base client's 30s callTimeout caps the
        // whole call including body consumption, and ExoPlayer holds a stream's
        // response body open for the life of the track - the base client
        // force-aborted every streamed track ~30s in (v0.5.0 regression).
        @MediaHttp okHttpClient: OkHttpClient,
        simpleCache: SimpleCache,
    ): ExoPlayer {
        // Built directly on whatever thread Dagger resolves this dependency:
        // setLooper(mainLooper) in buildExoPlayer pins the player's application
        // thread to main, which Media3 supports from any construction thread.
        // The previous post-to-main + CountDownLatch approach blocked inside
        // Dagger's DoubleCheck lock while waiting on the main thread - if the
        // main thread was itself entering the same DI graph, that deadlocked
        // (10 s frozen main thread, then IllegalStateException).
        return buildExoPlayer(context, okHttpClient, simpleCache)
    }

    @OptIn(UnstableApi::class)
    private fun buildExoPlayer(context: Context, okHttpClient: OkHttpClient, simpleCache: SimpleCache): ExoPlayer {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val okHttpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
        val defaultDataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(context, okHttpDataSourceFactory)

        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(simpleCache)
            .setUpstreamDataSourceFactory(defaultDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        // Defaults are video-tuned. For audio-only HTTP streaming, bigger min/max
        // buffers (60s/120s) cut HTTP fetch cycles ~2x per hour at 128 kbps;
        // prioritizing time-over-size lets the player fill the time window even
        // for high-bitrate streams. Back buffer disabled (music doesn't rewind).
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(60_000, 120_000, 1_000, 2_000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(0, false)
            .build()

        val player = ExoPlayer.Builder(context)
            .setAudioAttributes(audioAttributes, true)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(
                // Pass the DataSource.Factory directly: Media3 1.10 deprecated
                // the (Context) ctor + setDataSourceFactory() flow. The
                // remaining DeprecatedCall warning is a slack-lints false
                // positive (class has some @Deprecated methods so any
                // constructor call is flagged); kotlinc emits no warning.
                @Suppress("DeprecatedCall")
                androidx.media3.exoplayer.source.DefaultMediaSourceFactory(cacheDataSourceFactory),
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            // Pin the player's application thread to the main looper so
            // Builder.build() is legal off-main (see provideExoPlayer).
            .setLooper(Looper.getMainLooper())
            .build()

        // Audio offload is DISABLED on the legacy branch (Android 8-16).
        //
        // v0.5.0 enabled AUDIO_OFFLOAD_MODE_ENABLED with gapless-required
        // (perf: stream compressed audio straight to the DSP). On the wide
        // range of OEM HALs this branch targets, offloaded AudioTrack is a
        // well-known source of silent playback failure: no audio and/or a
        // playback position frozen at 0:00, with no PlaybackException raised -
        // exactly the "stuck on 0:00, play does nothing" reports against
        // v0.5.x. The JVM test tier cannot see this (TestExoPlayerBuilder
        // never touches the device audio sink), and emulators fall back to
        // the PCM path, so only real devices regressed. Battery savings are
        // not worth broken playback; if offload returns here it must be an
        // opt-in setting with a same-screen kill switch.
        return player
    }

    // Main is intentionally absent from AppDispatchers (see Dispatcher.kt):
    // tests substitute it globally via Dispatchers.setMain, so qualifying
    // it would only add ceremony.
    @OptIn(UnstableApi::class)
    @Suppress("RawDispatchersUse")
    @Provides
    @Singleton
    fun provideMediaSession(
        @ApplicationContext context: Context,
        exoPlayer: ExoPlayer,
        playbackManager: PlaybackManager,
        queueManager: QueueManager,
        libraryRepository: LibraryRepository,
    ): MediaSession {
        val forwardingPlayer = QueueForwardingPlayer(exoPlayer, playbackManager, queueManager)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val callback = object : MediaSession.Callback {
            override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
                val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                    .add(MediaSessionConstants.COMMAND_TOGGLE_FAVORITE)
                    .build()
                val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                    .setAvailableSessionCommands(sessionCommands)
                    .setAvailablePlayerCommands(playerCommands)
                    .build()
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle,
            ): ListenableFuture<SessionResult> {
                if (customCommand.customAction == MediaSessionConstants.ACTION_TOGGLE_FAVORITE) {
                    scope.launch {
                        val trackId = queueManager.currentTrack.value?.id ?: return@launch
                        val newIsFavorite = libraryRepository.toggleTrackFavorite(trackId)
                        // Queue state is patched via PlayerViewModel.collectFavoriteTrackIds
                        // -> applyFavoriteIds, which preserves the unshuffle snapshot.
                        // setQueue here would null it.
                        updateFavoriteLayout(session, newIsFavorite)
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                return super.onCustomCommand(session, controller, customCommand, args)
            }
        }

        val mediaSession = MediaSession.Builder(context, forwardingPlayer)
            .setSessionActivity(pendingIntent)
            .setCallback(callback)
            .build()

        // Set initial custom layout with unfilled heart
        updateFavoriteLayout(mediaSession, false)

        // Observe current track changes to update favorite layout
        scope.launch {
            queueManager.currentTrack.collect { track ->
                updateFavoriteLayout(mediaSession, track?.isFavorite == true)
            }
        }

        return mediaSession
    }

    @OptIn(UnstableApi::class)
    private fun updateFavoriteLayout(session: MediaSession, isFavorite: Boolean) {
        val iconType = if (isFavorite) {
            CommandButton.ICON_HEART_FILLED
        } else {
            CommandButton.ICON_HEART_UNFILLED
        }
        val favoriteButton = CommandButton.Builder(iconType)
            .setSessionCommand(MediaSessionConstants.COMMAND_TOGGLE_FAVORITE)
            .setDisplayName(if (isFavorite) "Remove from favorites" else "Add to favorites")
            .setSlots(CommandButton.SLOT_CENTRAL)
            .build()
        session.setMediaButtonPreferences(listOf(favoriteButton))
    }
}
