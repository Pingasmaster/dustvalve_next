package com.dustvalve.next.android.data.repository

import com.dustvalve.next.android.data.local.DatabaseGateway
import com.dustvalve.next.android.data.local.db.dao.YouTubePlaylistCacheDao
import com.dustvalve.next.android.data.local.db.dao.YouTubeVideoCacheDao
import com.dustvalve.next.android.data.local.db.entity.YouTubePlaylistCacheEntity
import com.dustvalve.next.android.data.local.db.entity.YouTubeVideoCacheEntity
import com.dustvalve.next.android.data.network.OpportunisticRefreshGate
import com.dustvalve.next.android.data.network.UnmeteredRefreshGate
import com.dustvalve.next.android.data.remote.youtube.innertube.YouTubeChannelParser
import com.dustvalve.next.android.data.remote.youtube.innertube.YouTubeClient
import com.dustvalve.next.android.data.remote.youtube.innertube.YouTubeInnertubeClient
import com.dustvalve.next.android.data.remote.youtube.innertube.YouTubeNextParser
import com.dustvalve.next.android.data.remote.youtube.innertube.YouTubePlayerParser
import com.dustvalve.next.android.data.remote.youtube.innertube.YouTubePlaylistParser
import com.dustvalve.next.android.data.remote.youtube.innertube.YouTubeSearchParser
import com.dustvalve.next.android.data.remote.youtubemusic.YouTubeMusicAlbumResolver
import com.dustvalve.next.android.data.remote.youtubemusic.YouTubeMusicArtistParser
import com.dustvalve.next.android.data.remote.youtubemusic.YouTubeMusicInnertubeClient
import com.dustvalve.next.android.di.qualifiers.AppDispatchers
import com.dustvalve.next.android.di.qualifiers.Dispatcher
import com.dustvalve.next.android.domain.model.AudioFormat
import com.dustvalve.next.android.domain.model.SearchResult
import com.dustvalve.next.android.domain.model.SearchResultType
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.model.TrackSource
import com.dustvalve.next.android.domain.repository.YouTubeArtistAlbum
import com.dustvalve.next.android.domain.repository.YouTubeChannelResult
import com.dustvalve.next.android.domain.repository.YouTubePlaylistResult
import com.dustvalve.next.android.domain.repository.YouTubeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * First-party YouTube repository backed by the no-auth Innertube layer
 * (see com.dustvalve.next.android.data.remote.youtube.innertube). Public
 * signatures match the YouTubeRepository interface so callers (player VM,
 * search VM, etc.) keep working unchanged.
 */
@Singleton
class YouTubeRepositoryImpl(
    private val client: YouTubeInnertubeClient,
    private val playerParser: YouTubePlayerParser,
    private val searchParser: YouTubeSearchParser,
    private val playlistParser: YouTubePlaylistParser,
    private val channelParser: YouTubeChannelParser,
    private val nextParser: YouTubeNextParser,
    private val videoCache: YouTubeVideoCacheDao,
    private val playlistCache: YouTubePlaylistCacheDao,
    private val youTubeMusicRepository: com.dustvalve.next.android.domain.repository.YouTubeMusicRepository,
    private val albumResolver: YouTubeMusicAlbumResolver,
    private val ytmClient: YouTubeMusicInnertubeClient,
    private val ytmArtistParser: YouTubeMusicArtistParser,
    ioDispatcher: CoroutineDispatcher,
    private val refreshGate: OpportunisticRefreshGate = OpportunisticRefreshGate.ALWAYS,
) : YouTubeRepository {

    @Inject constructor(
        client: YouTubeInnertubeClient,
        playerParser: YouTubePlayerParser,
        searchParser: YouTubeSearchParser,
        playlistParser: YouTubePlaylistParser,
        channelParser: YouTubeChannelParser,
        nextParser: YouTubeNextParser,
        gateway: DatabaseGateway,
        youTubeMusicRepository: com.dustvalve.next.android.domain.repository.YouTubeMusicRepository,
        albumResolver: YouTubeMusicAlbumResolver,
        ytmClient: YouTubeMusicInnertubeClient,
        ytmArtistParser: YouTubeMusicArtistParser,
        @Dispatcher(AppDispatchers.IO) ioDispatcher: CoroutineDispatcher,
        refreshGate: UnmeteredRefreshGate,
    ) : this(
        client,
        playerParser,
        searchParser,
        playlistParser,
        channelParser,
        nextParser,
        gateway.youtubeVideoCacheDao,
        gateway.youtubePlaylistCacheDao,
        youTubeMusicRepository,
        albumResolver,
        ytmClient,
        ytmArtistParser,
        ioDispatcher,
        refreshGate,
    )

    private val backgroundScope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val json = Json { ignoreUnknownKeys = true }
    private val stringListSerializer = ListSerializer(String.serializer())

    /**
     * Dedupes the ArtistDetail double-call (`getArtist` then
     * `getArtistTracks`) so Topic fallback only hits the network once.
     */
    @Volatile private var lastChannelPage: Pair<String, YouTubeChannelResult>? = null

    private companion object {
        // Video metadata (title, duration, uploader) is immutable
        // post-publish and cached forever. Playlists revalidate in the
        // background on unmetered networks only (see getPlaylistTracks).

        // Opaque YouTube `params` token selecting the channel "Videos" tab.
        // This is the same value Metrolist / NewPipe / yt-dlp use; YT has
        // not rotated it in years.
        const val VIDEOS_TAB_PARAMS = "EgZ2aWRlb3PyBgQKAjoA"

        // Innertube /search filter params (sp= on youtube.com). Keep in sync
        // with LiveProviderMetadataIntegrityTest. Client-side type filtering
        // remains as a safety net when the server still mixes result kinds.
        const val SEARCH_VIDEOS_PARAMS = "EgIQAQ%3D%3D"
        const val SEARCH_CHANNELS_PARAMS = "EgIQAg%3D%3D"
        const val SEARCH_PLAYLISTS_PARAMS = "EgIQAw%3D%3D"

        // YTM album browse ids. Album search results carry these inside
        // playlist-shaped URLs; they must be resolved to the album's real
        // audioPlaylistId (OLAK5uy_...) before the playlist browse.
        const val ALBUM_BROWSE_ID_PREFIX = "MPREb"

        /** Match [ExpandSourceTracksUseCase.MAX_TRACKS] for play/download parity. */
        const val MAX_PLAYLIST_TRACKS = 5_000

        /** ~25 tracks/page -> well past the track cap; safety against tiny pages. */
        const val MAX_PLAYLIST_PAGES = 200
    }

    /**
     * filter: "songs" -> videos only, "playlists" -> playlists only,
     * "artists" -> channels only. Pagination uses the search response's
     * continuation token; the [page] parameter is the opaque token from
     * the previous call (or null on the first page).
     *
     * When a filter chip is selected we send the matching Innertube `params`
     * token (same values as the live integrity suite) and still filter
     * client-side so mixed leftovers never leak into the chip view.
     */
    override suspend fun search(query: String, filter: String?, page: Any?): Pair<List<SearchResult>, Any?> {
        val params = searchFilterParams(filter)
        val continuation = page as? String
        val response = if (continuation.isNullOrBlank()) {
            client.search(query = query, params = params)
        } else {
            client.searchContinuation(continuation)
        }
        val parsed = if (continuation.isNullOrBlank()) {
            searchParser.parse(response)
        } else {
            searchParser.parseContinuation(response)
        }
        val filtered = when (filter) {
            "songs", "videos" -> parsed.items.filter { it.type == SearchResultType.YOUTUBE_TRACK }
            "playlists" -> parsed.items.filter { it.type == SearchResultType.YOUTUBE_PLAYLIST }
            "artists" -> parsed.items.filter { it.type == SearchResultType.YOUTUBE_ARTIST }
            else -> parsed.items
        }
        return filtered to parsed.continuation
    }

    private fun searchFilterParams(filter: String?): String? = when (filter) {
        "songs", "videos" -> SEARCH_VIDEOS_PARAMS
        "playlists" -> SEARCH_PLAYLISTS_PARAMS
        "artists" -> SEARCH_CHANNELS_PARAMS
        else -> null
    }

    override suspend fun getStreamUrl(videoUrl: String): String {
        val videoId = extractVideoId(videoUrl)
            ?: throw IllegalArgumentException("Cannot extract videoId from $videoUrl")
        val response = client.player(videoId)
        return playerParser.parsePlayerStreamInfo(response).streamUrl
    }

    override suspend fun getDownloadableStream(videoUrl: String): Pair<String, AudioFormat> {
        val videoId = extractVideoId(videoUrl)
            ?: throw IllegalArgumentException("Cannot extract videoId from $videoUrl")
        val response = client.player(videoId)
        val info = playerParser.parsePlayerStreamInfo(response)
        return info.streamUrl to info.format
    }

    override suspend fun getTrackInfo(videoUrl: String): Track {
        val videoId = extractVideoId(videoUrl)
            ?: throw IllegalArgumentException("Cannot extract videoId from $videoUrl")
        // Cache-first: video metadata is immutable post-publish, so a hit
        // returns instantly with no network access. If the row pre-dates the
        // albumUrl lookup (playlist-seeded entries have albumLookupDone=false),
        // fire the lookup once and upgrade the row in place before returning.
        videoCache.getById(videoId)?.let { cached ->
            if (cached.albumLookupDone) return cachedToTrack(cached)
            // A null lookup result means it FAILED (network / API error):
            // leave the row untouched so a later attempt retries instead of
            // caching a poisoned "no album" negative.
            val resolvedAlbumUrl = resolveAlbumOnce(videoId) ?: return cachedToTrack(cached)
            val upgraded = cached.copy(albumUrl = resolvedAlbumUrl, albumLookupDone = true)
            YouTubeRepositorySupport.bestEffort { videoCache.insert(upgraded) }
            return cachedToTrack(upgraded)
        }
        val response = client.player(videoId)
        val parsed = playerParser.parseTrack(response, videoId)
        val albumUrl = resolveAlbumOnce(videoId)
        val track = parsed.copy(albumUrl = albumUrl.orEmpty())
        // Persist for future reads. Errors swallowed silently - caching is
        // best-effort and must never break the user-facing call. A failed
        // album lookup (albumUrl == null) is persisted with
        // albumLookupDone=false so the next getTrackInfo retries it.
        YouTubeRepositorySupport.bestEffort { videoCache.insert(track.toCacheEntity(videoId, albumLookupDone = albumUrl != null)) }
        return track
    }

    /**
     * YTM album lookup. Returns the album playlist URL, `""` when the lookup
     * completed and the video definitively has no album (the entity's
     * empty-string "no album known" convention), or `null` when the lookup
     * FAILED (network / API error) - callers must not mark the row as
     * looked-up in that case. Cancellation always propagates.
     */
    private suspend fun resolveAlbumOnce(videoId: String): String? {
        try {
            return youTubeMusicRepository.lookupAlbumPlaylistForVideo(videoId) ?: ""
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            return null
        }
    }

    private fun cachedToTrack(cached: YouTubeVideoCacheEntity): Track = Track(
        id = "yt_${cached.videoId}",
        albumId = "",
        title = cached.title,
        artist = cached.artist,
        artistUrl = cached.artistUrl,
        trackNumber = 0,
        duration = cached.durationSec,
        // The canonical watch URL, NOT null: PlayerViewModel.resolveTrackForPlayback
        // resolves the real googlevideo stream from track.streamUrl, and bails out
        // when it is null. Cached tracks used to carry null here, so REPLAYING any
        // previously-cached video (or "play all" on a cached playlist) silently did
        // nothing - the "play button does nothing" class of bug.
        streamUrl = "https://www.youtube.com/watch?v=${cached.videoId}",
        artUrl = cached.artUrl,
        albumTitle = "",
        albumUrl = cached.albumUrl,
        source = TrackSource.YOUTUBE,
    )

    private fun Track.toCacheEntity(videoId: String, albumLookupDone: Boolean = false): YouTubeVideoCacheEntity = YouTubeVideoCacheEntity(
        videoId = videoId,
        title = title,
        artist = artist,
        artistUrl = artistUrl,
        durationSec = duration,
        artUrl = artUrl,
        albumUrl = albumUrl,
        albumLookupDone = albumLookupDone,
    )

    override suspend fun getRecommendations(videoUrl: String): List<SearchResult> {
        val videoId = extractVideoId(videoUrl)
            ?: throw IllegalArgumentException("Cannot extract videoId from $videoUrl")
        val response = client.next(videoId)
        return nextParser.parse(response)
    }

    override suspend fun getPlaylistTracks(playlistUrl: String): YouTubePlaylistResult {
        val extractedId = extractPlaylistId(playlistUrl)
            ?: throw IllegalArgumentException("Cannot extract playlistId from $playlistUrl")

        // Album / playlist radio IDs are RDAMPL + inner playlist id. Strip the
        // prefix so OLAK*/PL* land on the VL browse path; remaining RD* (including
        // RDAMPLRDCLAK*) stay on the mix path below.
        val playlistId = YouTubeRepositorySupport.stripRdAmplPrefix(extractedId)

        // Mixes (RD*) are not VL-browsable; they paginate via /next. Import /
        // resolvePlaylistTracks share this entry point with YouTubeSource, so
        // route them through getMixPage and surface an empty Mix as an error
        // instead of a silent 0-track success.
        if (playlistId.startsWith("RD")) {
            return playlistTracksFromMix(playlistId)
        }

        // Cache-first: rebuild the playlist from cached video metadata if
        // available. Then trigger a silent background refresh (errors
        // swallowed) to pick up any newly-added videos. MPREb album ids get
        // a snapshot cached under their own key (see below), so this path
        // also serves them without a network round-trip.
        val cached = playlistCache.getById(extractedId)
            ?: playlistCache.getById(playlistId)
        if (cached != null) {
            val fromCache = playlistTracksFromCache(cached, playlistId, extractedId)
            if (fromCache != null) return fromCache
        }

        // Cache miss / partial cache: fetch synchronously.
        return fetchAndCachePlaylist(
            playlistId = resolveBrowsablePlaylistId(playlistId),
            aliasId = extractedId,
        )
    }

    private suspend fun playlistTracksFromMix(playlistId: String): YouTubePlaylistResult {
        val mixUrl = "https://www.youtube.com/playlist?list=$playlistId"
        val (tracks, title, _) = getMixPage(mixUrl = mixUrl)
        if (tracks.isEmpty()) {
            throw IllegalStateException("Mix returned no tracks")
        }
        val cover = tracks.firstOrNull()?.artUrl?.takeIf { it.isNotBlank() }
        return YouTubePlaylistResult(tracks, title, cover)
    }

    private suspend fun playlistTracksFromCache(
        cached: YouTubePlaylistCacheEntity,
        playlistId: String,
        extractedId: String,
    ): YouTubePlaylistResult? {
        val ids = YouTubeRepositorySupport.bestEffortValue(emptyList()) {
            json.decodeFromString(stringListSerializer, cached.videoIdsJson)
        }
        if (ids.isEmpty()) return null
        val cachedVideos = videoCache.getByIds(ids).associateBy { it.videoId }
        val tracks = ids.mapNotNull { id -> cachedVideos[id]?.let { cachedToTrack(it) } }
        if (tracks.isEmpty()) return null
        // Playlists grow like artist discographies: return whatever we have
        // immediately (including a partial join), then silently revalidate
        // on unmetered so newly-added videos land on the next open.
        if (refreshGate.allowRefresh()) {
            backgroundScope.launch {
                YouTubeRepositorySupport.bestEffort {
                    fetchAndCachePlaylist(
                        playlistId = resolveBrowsablePlaylistId(playlistId),
                        aliasId = extractedId,
                    )
                }
            }
        }
        // Cache entity has no cover column yet; first-track art is
        // the best offline cover we can offer without a migration.
        val cover = tracks.firstOrNull()?.artUrl?.takeIf { it.isNotBlank() }
        return YouTubePlaylistResult(tracks, cached.title, cover)
    }

    /**
     * YTM album search results emit playlist-shaped URLs carrying `MPREb_...`
     * album browse ids; browsing "VLMPREb_..." is invalid. Resolve those to
     * the album's audioPlaylistId (`OLAK5uy_...`) first. Everything else
     * passes through unchanged.
     */
    private suspend fun resolveBrowsablePlaylistId(extractedId: String): String {
        if (!extractedId.startsWith(ALBUM_BROWSE_ID_PREFIX)) return extractedId
        return albumResolver.resolveAudioPlaylistId(extractedId)
            ?: throw IllegalStateException("Cannot resolve album $extractedId to an audio playlist")
    }

    /**
     * Fetches the full playlist and persists video + playlist snapshots
     * (best-effort). When [aliasId] differs from [playlistId] (an MPREb
     * album id resolved to its OLAK playlist), the snapshot is stored under
     * BOTH keys so future opens of the album URL hit the cache directly.
     */
    private suspend fun fetchAndCachePlaylist(playlistId: String, aliasId: String = playlistId): YouTubePlaylistResult {
        val response = client.browse("VL$playlistId")
        // Unviewable / private / deleted playlists often return an ERROR
        // alertRenderer with an empty section list. Fail clearly instead of
        // treating that as a successful empty playlist.
        YouTubeRepositorySupport.playlistAlertMessage(response)?.let { msg ->
            throw IllegalStateException(msg)
        }
        val first = playlistParser.parse(response, playlistId)
        val all = first.tracks.toMutableList()
        var cont = first.continuation
        var safety = 0

        // Paginate through continuations until exhausted or we hit the
        // shared expansion ceiling (same cap as play/download queue fill).
        while (cont != null && all.size < MAX_PLAYLIST_TRACKS && safety < MAX_PLAYLIST_PAGES) {
            val contResp = client.browseContinuation(cont)
            val nextPage = playlistParser.parseContinuation(contResp, playlistId, all.size + 1)
            all += nextPage.tracks
            cont = nextPage.continuation
            safety += 1
        }
        if (all.size > MAX_PLAYLIST_TRACKS) {
            all.subList(MAX_PLAYLIST_TRACKS, all.size).clear()
        }
        val title = first.title ?: ""
        val coverUrl = first.coverUrl
            ?: all.firstOrNull()?.artUrl?.takeIf { it.isNotBlank() }

        // Persist video + playlist snapshots. Best-effort. Videos use the
        // non-destructive bulk insert: these entities carry default
        // albumUrl/albumLookupDone and must never clobber rows already
        // upgraded by the single-video path. An empty parse (deleted
        // playlist / error page that slipped past the alert check) must
        // not wipe a snapshot we already have.
        YouTubeRepositorySupport.bestEffort {
            val ids = all.map { it.id.removePrefix("yt_") }
            val videoEntities = all.zip(ids).map { (track, vid) -> track.toCacheEntity(vid) }
            videoCache.insertAllIgnore(videoEntities)
            if (all.isEmpty()) {
                val existing = playlistCache.getById(playlistId)
                    ?: playlistCache.getById(aliasId)
                if (existing != null) return@bestEffort
            }
            val idsJson = json.encodeToString(stringListSerializer, ids)
            playlistCache.insert(
                YouTubePlaylistCacheEntity(
                    playlistId = playlistId,
                    title = title,
                    videoIdsJson = idsJson,
                ),
            )
            if (aliasId != playlistId) {
                playlistCache.insert(
                    YouTubePlaylistCacheEntity(
                        playlistId = aliasId,
                        title = title,
                        videoIdsJson = idsJson,
                    ),
                )
            }
        }
        return YouTubePlaylistResult(all, title, coverUrl)
    }

    override suspend fun getMixPage(mixUrl: String, cursor: Any?, seenVideoIds: Set<String>): Triple<List<Track>, String, Any?> {
        val mixId = YouTubeRepositorySupport.requireMixPlaylistId(mixUrl, ::extractPlaylistId)
        val typed = cursor as? YouTubePlaylistParser.MixContinuation
        val seed = if (typed == null) playlistParser.extractMixSeedVideoId(mixId) else null
        YouTubeRepositorySupport.requireMixSeedOrSeedless(
            mixId,
            typed,
            seed,
            playlistParser::isSeedlessMixId,
        )
        val response = if (typed == null) {
            client.next(videoId = seed, playlistId = mixId)
        } else {
            client.next(
                videoId = typed.lastVideoId,
                playlistId = mixId,
                playlistIndex = typed.playlistIndex,
                params = typed.params,
            )
        }
        val startIndex = (typed?.playlistIndex ?: 0) + 1
        val page = playlistParser.parseMix(
            root = response,
            playlistId = mixId,
            startIndex = startIndex,
            seenVideoIds = seenVideoIds,
        )
        // First page empty -> clear failure (not a silent empty playlist).
        // Later pages empty -> mix exhausted (null cursor).
        if (page.tracks.isEmpty() && typed == null) {
            throw IllegalStateException("Mix returned no tracks")
        }
        val nextCursor = if (page.tracks.isEmpty()) null else page.continuation
        // Best-effort cache write so freshly seen videos benefit getTrackInfo
        // etc. Non-destructive: default-seeded entities must not clobber
        // rows already upgraded with a resolved albumUrl.
        YouTubeRepositorySupport.bestEffort {
            val entities = page.tracks.map { it.toCacheEntity(it.id.removePrefix("yt_")) }
            videoCache.insertAllIgnore(entities)
        }
        return Triple(page.tracks, page.title.orEmpty(), nextCursor)
    }

    override suspend fun getChannelVideos(channelUrl: String, page: Any?): YouTubeChannelResult {
        val channelId = extractChannelId(channelUrl)
            ?: throw IllegalArgumentException("Cannot extract channelId from $channelUrl")
        val token = page as? ChannelPageToken
        return if (token == null) {
            lastChannelPage?.let { (cachedId, cached) ->
                if (cachedId == channelId) return cached
            }
            val result = resolveChannelFirstPage(channelId)
            lastChannelPage = channelId to result
            result
        } else {
            // Channel browse runs on WEB; the continuation must too, or the
            // response comes back MWEB-shaped and parses to zero tracks
            // (silently truncating every channel to page 1).
            val response = client.browseContinuation(token.continuation, YouTubeClient.WebNoAuth)
            val parsed = channelParser.parseContinuation(response, token.channelId, token.channelName, token.totalSoFar + 1)
            val newTotal = token.totalSoFar + parsed.tracks.size
            YouTubeChannelResult(
                tracks = parsed.tracks,
                channelName = token.channelName,
                nextPage = parsed.continuation?.let {
                    ChannelPageToken(token.channelId, token.channelName, newTotal, it, token.avatarUrl)
                },
                avatarUrl = token.avatarUrl,
                albums = emptyList(),
            )
        }
    }

    /**
     * Videos tab first. When empty (Topic / music-only channels), fall back
     * to YTM artist browse for Top songs + album shelves; if that still has
     * no songs, follow the linked official USER_CHANNEL Videos tab.
     */
    private suspend fun resolveChannelFirstPage(channelId: String): YouTubeChannelResult {
        val videosResponse = client.browse(channelId, params = VIDEOS_TAB_PARAMS)
        val videos = channelParser.parse(videosResponse, channelId)
        if (videos.tracks.isNotEmpty()) {
            return YouTubeChannelResult(
                tracks = videos.tracks,
                channelName = videos.channelName,
                nextPage = videos.continuation?.let {
                    ChannelPageToken(channelId, videos.channelName, videos.tracks.size, it, videos.avatarUrl)
                },
                avatarUrl = videos.avatarUrl,
            )
        }

        val ytmPage = ytmArtistPageOrNull(channelId)

        if (ytmPage != null && (ytmPage.songs.isNotEmpty() || ytmPage.albums.isNotEmpty())) {
            val songs = expandYtmTopSongs(ytmPage, channelId)
            return YouTubeChannelResult(
                tracks = songs,
                channelName = ytmPage.name ?: videos.channelName,
                nextPage = null,
                avatarUrl = ytmPage.avatarUrl ?: videos.avatarUrl,
                albums = ytmPage.albums.map {
                    YouTubeArtistAlbum(
                        browseId = it.browseId,
                        title = it.title,
                        artUrl = it.artUrl,
                        year = it.year,
                    )
                },
            )
        }

        val linkedId = ytmPage?.linkedChannelId
        if (ytmPage != null && !linkedId.isNullOrBlank() && linkedId != channelId) {
            val linkedResponse = client.browse(linkedId, params = VIDEOS_TAB_PARAMS)
            val linked = channelParser.parse(linkedResponse, linkedId)
            if (linked.tracks.isNotEmpty()) {
                return YouTubeChannelResult(
                    tracks = linked.tracks,
                    channelName = videos.channelName ?: linked.channelName ?: ytmPage.name,
                    nextPage = linked.continuation?.let {
                        ChannelPageToken(linkedId, linked.channelName, linked.tracks.size, it, linked.avatarUrl)
                    },
                    avatarUrl = videos.avatarUrl ?: ytmPage.avatarUrl ?: linked.avatarUrl,
                    albums = emptyList(),
                )
            }
        }

        return YouTubeChannelResult(
            tracks = emptyList(),
            channelName = videos.channelName ?: ytmPage?.name,
            nextPage = null,
            avatarUrl = videos.avatarUrl ?: ytmPage?.avatarUrl,
            albums = emptyList(),
        )
    }

    /**
     * Best-effort YTM artist browse. NOT runCatching: it would swallow the
     * CancellationException from a cancelled browse, and with no later
     * suspension point in [resolveChannelFirstPage] the empty fallback result
     * would be built and cached in lastChannelPage - permanently showing an
     * empty artist page for that channel.
     */
    private suspend fun ytmArtistPageOrNull(channelId: String): YouTubeMusicArtistParser.ArtistPage? = try {
        ytmArtistParser.parse(ytmClient.browse(channelId), channelId)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    /**
     * When the Top songs shelf exposes a "Show all" bottomEndpoint, follow
     * that browse/playlist for the full list; otherwise keep the shelf preview.
     */
    private suspend fun expandYtmTopSongs(page: YouTubeMusicArtistParser.ArtistPage, channelId: String): List<Track> {
        val endpoint = page.songsMoreEndpoint ?: return page.songs
        return try {
            val full = when {
                !endpoint.browseId.isNullOrBlank() -> {
                    val browseId = endpoint.browseId
                    ytmArtistParser.parseSongList(
                        root = ytmClient.browse(browseId),
                        channelId = channelId,
                        artistName = page.name,
                    )
                }

                !endpoint.playlistId.isNullOrBlank() -> {
                    val playlistId = endpoint.playlistId.removePrefix("VL")
                    val result = fetchAndCachePlaylist(playlistId)
                    result.tracks
                }

                else -> emptyList()
            }
            if (full.isNotEmpty()) full else page.songs
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            page.songs
        }
    }

    /** Opaque page token for getChannelVideos pagination. */
    private data class ChannelPageToken(
        val channelId: String,
        val channelName: String?,
        val totalSoFar: Int,
        val continuation: String,
        val avatarUrl: String? = null,
    )

    private fun extractVideoId(url: String): String? {
        val patterns = listOf(
            Regex("[?&]v=([a-zA-Z0-9_-]{11})"),
            Regex("/shorts/([a-zA-Z0-9_-]{11})"),
            Regex("youtu\\.be/([a-zA-Z0-9_-]{11})"),
        )
        for (p in patterns) p.find(url)?.groupValues?.getOrNull(1)?.let { return it }
        // Bare 11-char videoId
        if (url.matches(Regex("[a-zA-Z0-9_-]{11}"))) return url
        return null
    }

    private fun extractPlaylistId(url: String): String? {
        Regex("[?&]list=([A-Za-z0-9_-]+)").find(url)?.groupValues?.getOrNull(1)?.let { return it }
        // Allow callers to pass a bare playlistId
        if (url.matches(Regex("[A-Za-z0-9_-]+")) && url.length in 8..64) return url
        return null
    }

    private fun extractChannelId(url: String): String? {
        Regex("/channel/(UC[A-Za-z0-9_-]{22})").find(url)?.groupValues?.getOrNull(1)?.let { return it }
        if (url.matches(Regex("UC[A-Za-z0-9_-]{22}"))) return url
        return null
    }
}
