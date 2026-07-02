package com.dustvalve.next.android.di

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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
    fun provideExoPlayer(@ApplicationContext context: Context, okHttpClient: OkHttpClient, simpleCache: SimpleCache): ExoPlayer {
        // ExoPlayer.Builder.build() must run on the main thread.
        // Use Handler.post + CountDownLatch instead of runBlocking(Dispatchers.Main)
        // to avoid potential deadlock with the coroutine dispatcher.
        if (Looper.myLooper() != Looper.getMainLooper()) {
            var result: ExoPlayer? = null
            var error: Exception? = null
            val latch = CountDownLatch(1)
            Handler(Looper.getMainLooper()).post {
                try {
                    result = buildExoPlayer(context, okHttpClient, simpleCache)
                } catch (e: Exception) {
                    error = e
                } finally {
                    latch.countDown()
                }
            }
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw IllegalStateException("ExoPlayer initialization timed out")
            }
            error?.let { throw it }
            return result ?: throw IllegalStateException("ExoPlayer initialization failed")
        }
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
            .build()

        // Stream compressed audio straight to the DSP, bypassing the CPU.
        // Speed change must stay off for offload to engage; the speed-control
        // path re-enables CPU decoding dynamically if the user picks != 1.0x.
        val offloadPrefs = AudioOffloadPreferences.Builder()
            .setAudioOffloadMode(AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED)
            .setIsGaplessSupportRequired(true)
            .setIsSpeedChangeSupportRequired(false)
            .build()
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setAudioOffloadPreferences(offloadPrefs)
            .build()

        return player
    }

    // Main is intentionally absent from AppDispatchers (see Dispatcher.kt):
    // tests substitute it globally via Dispatchers.setMain, so qualifying
    // it would only add ceremony.
    @OptIn(UnstableApi::class)
    @SuppressLint("SlackDispatchersUse")
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
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
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
                        // → applyFavoriteIds, which preserves the unshuffle snapshot.
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
