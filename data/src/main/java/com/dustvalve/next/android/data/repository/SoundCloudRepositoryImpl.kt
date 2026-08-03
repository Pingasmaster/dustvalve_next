package com.dustvalve.next.android.data.repository

import com.dustvalve.next.android.data.remote.soundcloud.SoundCloudApi
import com.dustvalve.next.android.data.remote.soundcloud.SoundCloudMappers
import com.dustvalve.next.android.data.remote.soundcloud.numericIdFromTrackId
import com.dustvalve.next.android.data.remote.soundcloud.path
import com.dustvalve.next.android.data.remote.soundcloud.str
import com.dustvalve.next.android.domain.model.Album
import com.dustvalve.next.android.domain.model.Artist
import com.dustvalve.next.android.domain.model.MusicCollection
import com.dustvalve.next.android.domain.model.SearchResult
import com.dustvalve.next.android.domain.model.SoundCloudHomeFeed
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.repository.SoundCloudRepository
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SoundCloudRepositoryImpl @Inject constructor(private val api: SoundCloudApi) : SoundCloudRepository {

    override suspend fun getHome(genre: String): SoundCloudHomeFeed {
        val charts = api.charts(genreSlug = genre, kind = "trending")
        val mixed = api.mixedSelections()
        return SoundCloudMappers.parseHome(genre = genre, chartsRoot = charts, mixedRoot = mixed)
    }

    override suspend fun search(query: String, filter: String?): List<SearchResult> {
        if (query.isBlank()) return emptyList()
        return SoundCloudMappers.parseSearchResults(api.search(query = query, filter = filter))
    }

    override suspend fun getTrack(urlOrId: String): Track {
        val numeric = numericIdFromTrackId(urlOrId)
        val element = if (numeric != null) {
            api.track(numeric)
        } else {
            api.resolve(normalizePermalink(urlOrId))
        }
        return SoundCloudMappers.parseTrack(element)
            ?: throw IOException("SoundCloud track not found for $urlOrId")
    }

    override suspend fun getStreamUrl(track: Track): String {
        val numeric = numericIdFromTrackId(track.id)
            ?: throw IOException("Invalid SoundCloud track id: ${track.id}")
        val trackJson = api.track(numeric)
        val auth = trackJson.str("track_authorization")
        val candidates = SoundCloudMappers.pickBestTranscodingUrls(trackJson)
        var lastError: IOException? = null
        for (url in candidates) {
            try {
                val resolved = api.resolveStream(url, auth).str("url")
                if (!resolved.isNullOrBlank()) return resolved
            } catch (e: IOException) {
                lastError = e
            }
        }
        throw lastError ?: IOException("No playable SoundCloud stream for ${track.id}")
    }

    override suspend fun getArtist(url: String): Artist {
        val element = api.resolve(normalizePermalink(url))
        return SoundCloudMappers.parseArtist(element)
            ?: throw IOException("SoundCloud artist not found for $url")
    }

    override suspend fun getArtistTracks(url: String, continuation: Any?): MusicCollection {
        val nextHref = continuation as? String
        val resolvedUser = if (nextHref == null) {
            api.resolve(normalizePermalink(url))
        } else {
            null
        }
        val artist = resolvedUser?.let { SoundCloudMappers.parseArtist(it) }
        val userId = artist?.id?.removePrefix("sc_user_")
        val page = if (nextHref != null) {
            api.userTracks(userId = "", nextHref = nextHref)
        } else {
            val id = userId ?: throw IOException("SoundCloud artist not found for $url")
            api.userTracks(userId = id)
        }
        val (tracks, next) = SoundCloudMappers.parsePagedTracks(page)
        val artistUrl = artist?.url ?: url
        val artistName = artist?.name.orEmpty()
        return MusicCollection(
            id = userId?.let { "sc_user_$it" } ?: artistUrl,
            url = artistUrl,
            name = artistName,
            owner = artistName,
            coverUrl = artist?.imageUrl,
            tracks = tracks,
            continuation = next,
            hasMore = !next.isNullOrBlank() && tracks.isNotEmpty(),
        )
    }

    override suspend fun getAlbum(url: String): Album {
        val playlist = api.resolve(normalizePermalink(url))
        val tracks = hydratePlaylistTracks(playlist)
        return SoundCloudMappers.parseAlbum(playlist, tracks)
            ?: throw IOException("SoundCloud album not found for $url")
    }

    override suspend fun getCollection(url: String, continuation: Any?): MusicCollection {
        // Playlists are returned in one shot; [continuation] is unused.
        val playlist = api.resolve(normalizePermalink(url))
        val tracks = hydratePlaylistTracks(playlist)
        return SoundCloudMappers.parseCollection(playlist, tracks, nextHref = null)
            ?: throw IOException("SoundCloud playlist not found for $url")
    }

    private suspend fun hydratePlaylistTracks(playlist: JsonElement): List<Track> {
        val stubs = SoundCloudMappers.playlistTrackStubs(playlist).take(MAX_PLAYLIST_TRACKS)
        if (stubs.isEmpty()) {
            val embedded = playlist.path("tracks") ?: return emptyList()
            return SoundCloudMappers.parseTracksArray(embedded).take(MAX_PLAYLIST_TRACKS)
        }
        val byId = LinkedHashMap<String, Track>()
        for (chunk in stubs.chunked(TRACK_IDS_CHUNK)) {
            for (track in SoundCloudMappers.parseTracksArray(api.tracksByIds(chunk))) {
                byId[track.id.removePrefix("sc_")] = track
            }
        }
        val embedded = SoundCloudMappers.parseTracksArray(
            playlist.path("tracks") ?: JsonArray(emptyList()),
        ).associateBy { it.id.removePrefix("sc_") }
        return stubs.mapNotNull { id -> byId[id] ?: embedded[id] }
    }

    private fun normalizePermalink(url: String): String {
        var out = url.trim()
        if (out.endsWith("/")) out = out.dropLast(1)
        if (!out.startsWith("http")) {
            out = "https://soundcloud.com/" + out.trimStart('/')
        }
        return out
            .replace("https://m.soundcloud.com/", "https://soundcloud.com/")
            .replace("https://www.soundcloud.com/", "https://soundcloud.com/")
    }

    private companion object {
        const val TRACK_IDS_CHUNK = 50

        /** Match [ExpandSourceTracksUseCase.MAX_TRACKS] for queue/download parity. */
        const val MAX_PLAYLIST_TRACKS = 5_000
    }
}
