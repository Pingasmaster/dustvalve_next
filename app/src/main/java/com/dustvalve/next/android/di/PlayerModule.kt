package com.dustvalve.next.android.di

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.dustvalve.next.android.MainActivity
import com.dustvalve.next.android.R
import com.dustvalve.next.android.data.asset.StoragePaths
import com.dustvalve.next.android.di.qualifiers.MediaHttp
import com.dustvalve.next.android.domain.repository.LibraryRepository
import com.dustvalve.next.android.player.AudioPowerPolicy
import com.dustvalve.next.android.player.HiFiAudioRenderersFactory
import com.dustvalve.next.android.player.MediaSessionConstants
import com.dustvalve.next.android.player.MediaSessionTrust
import com.dustvalve.next.android.player.PlaybackAudioTuning
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
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PlayerModule {

    @OptIn(UnstableApi::class)
    @Provides
    @Singleton
    fun provideSimpleCache(@ApplicationContext context: Context): SimpleCache {
        val databaseProvider = androidx.media3.database.StandaloneDatabaseProvider(context)
        val cacheDir = StoragePaths.mediaCacheDir(context)
        val legacyDir = StoragePaths.legacyMediaCacheDir(context)
        // URL-keyed spans in cacheDir are useless after the track-id cache
        // key change, and the OS may reclaim cacheDir. Drop the legacy tree
        // (and its index rows) so we do not keep a split-brain cache.
        if (legacyDir.exists() && legacyDir.canonicalFile != cacheDir.canonicalFile) {
            try {
                SimpleCache.delete(legacyDir, databaseProvider)
            } catch (_: Exception) {
                legacyDir.deleteRecursively()
            }
        }
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        return SimpleCache(cacheDir, NoOpCacheEvictor(), databaseProvider)
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
        audioPowerPolicy: AudioPowerPolicy,
        audioTuning: PlaybackAudioTuning,
    ): ExoPlayer {
        // Built directly on whatever thread Dagger resolves this dependency:
        // setLooper(mainLooper) in buildExoPlayer pins the player's application
        // thread to main, which Media3 supports from any construction thread.
        // The previous post-to-main + CountDownLatch approach blocked inside
        // Dagger's DoubleCheck lock while waiting on the main thread - if the
        // main thread was itself entering the same DI graph, that deadlocked
        // (10 s frozen main thread, then IllegalStateException).
        return buildExoPlayer(context, okHttpClient, simpleCache, audioPowerPolicy, audioTuning)
    }

    @OptIn(UnstableApi::class)
    private fun buildExoPlayer(
        context: Context,
        okHttpClient: OkHttpClient,
        simpleCache: SimpleCache,
        audioPowerPolicy: AudioPowerPolicy,
        audioTuning: PlaybackAudioTuning,
    ): ExoPlayer {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val okHttpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
        val defaultDataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(context, okHttpDataSourceFactory)

        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(simpleCache)
            .setUpstreamDataSourceFactory(defaultDataSourceFactory)
            // MediaItem.customCacheKey (track id) lands in DataSpec.key so a
            // freshly resolved googlevideo URL hits spans from the last play
            // instead of downloading the same bytes under a new URI key.
            .setCacheKeyFactory { spec -> spec.key ?: spec.uri.toString() }
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        // Defaults are video-tuned. For audio-only HTTP streaming, bigger min/max
        // buffers (60s/120s) cut HTTP fetch cycles ~2x per hour at 128 kbps;
        // prioritizing time-over-size lets the player fill the time window even
        // for high-bitrate streams. Start / after-rebuffer thresholds match
        // Media3's music-friendly defaults (was 1s/2s): thin cushions after a
        // stall + LDAC underrun risk produced play/hiccup loops. Back buffer
        // disabled (music doesn't rewind). Bluetooth stability mode may boost
        // these further via [PlaybackAudioTuning].
        val loadControl = audioTuning.buildLoadControl()

        val player = ExoPlayer.Builder(context)
            .setAudioAttributes(audioAttributes, true)
            .setLoadControl(loadControl)
            // Larger PCM AudioTrack cushion + float output for hi-res / LDAC.
            .setRenderersFactory(HiFiAudioRenderersFactory(context, audioTuning))
            .setMediaSourceFactory(
                // Pass the DataSource.Factory directly: Media3 1.10 deprecated
                // the (Context) ctor + setDataSourceFactory() flow. KEEP the
                // DeprecatedCall suppress: slack-lints 0.11.1 still false-
                // positives any ctor when the class has other @Deprecated
                // members (verified: lint fails without this); kotlinc is clean.
                @Suppress("DeprecatedCall")
                androidx.media3.exoplayer.source.DefaultMediaSourceFactory(cacheDataSourceFactory),
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            // Pin the player's application thread to the main looper so
            // Builder.build() is legal off-main (see provideExoPlayer).
            .setLooper(Looper.getMainLooper())
            .build()

        // Flavor-specific: OffloadAudioPowerPolicy (future) or SafeAudioPowerPolicy (compat).
        audioPowerPolicy.apply(player)

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
        val scope = CoroutineScope(
            SupervisorJob() + Dispatchers.Main.immediate +
                CoroutineExceptionHandler { _, throwable ->
                    Log.e(TAG, "Unhandled MediaSession coroutine error", throwable)
                },
        )

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
                // Full transport + custom favorite only for this app and
                // system/OEM media controllers. Everyone else gets Media3's
                // untrusted (read-mostly) command sets so a random third-party
                // MediaController cannot drive playback.
                if (!MediaSessionTrust.isTrustedController(context, controller.packageName)) {
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                        .setAvailableSessionCommands(MediaSession.ConnectionResult.DEFAULT_UNTRUSTED_SESSION_COMMANDS)
                        .setAvailablePlayerCommands(MediaSession.ConnectionResult.DEFAULT_UNTRUSTED_PLAYER_COMMANDS)
                        .build()
                }
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
                if (!MediaSessionTrust.isTrustedController(context, controller.packageName)) {
                    return Futures.immediateFuture(SessionResult(SessionError.ERROR_PERMISSION_DENIED))
                }
                if (customCommand.customAction == MediaSessionConstants.ACTION_TOGGLE_FAVORITE) {
                    scope.launch {
                        val track = queueManager.currentTrack.value ?: return@launch
                        val newIsFavorite = libraryRepository.toggleTrackFavorite(track)
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

    private const val TAG = "MediaSession"
}
