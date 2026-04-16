package com.dustvalve.next.android.data.remote.spotify

import com.dustvalve.next.android.domain.model.AudioFormat
import com.dustvalve.next.android.domain.model.SearchResult
import com.dustvalve.next.android.domain.model.SearchResultType
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.model.TrackSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpotifyExtractorWrapper @Inject constructor() {

    private var initialized = false

    suspend fun initialize(cacheDir: String, credentialsPath: String) = withContext(Dispatchers.IO) {
        SpotifyBridge.ensureLoaded()
        SpotifyBridge.nativeInitSession(cacheDir, credentialsPath)
        initialized = true
    }

    fun shutdown() {
        if (initialized) {
            SpotifyBridge.nativeShutdownSession()
            initialized = false
        }
    }

    fun hasCachedCredentials(credentialsPath: String): Boolean {
        return try {
            SpotifyBridge.ensureLoaded()
            SpotifyBridge.nativeHasCachedCredentials(credentialsPath)
        } catch (_: SpotifyNotAvailableException) {
            false
        }
    }

    fun getAuthorizationURL(): String {
        SpotifyBridge.ensureLoaded()
        return SpotifyBridge.nativeGetAuthorizationURL()
    }

    suspend fun handleOAuthCode(code: String, state: String) = withContext(Dispatchers.IO) {
        SpotifyBridge.nativeHandleOAuthCode(code, state)
    }

    suspend fun search(query: String, filter: String?): List<SearchResult> = withContext(Dispatchers.IO) {
        val json = SpotifyBridge.nativeSearch(query, filter)
        parseSearchResults(JSONObject(json))
    }

    suspend fun getTrackInfo(uri: String): Track = withContext(Dispatchers.IO) {
        val json = SpotifyBridge.nativeGetTrackMetadata(uri)
        parseTrack(JSONObject(json))
    }

    suspend fun getAlbumTracks(uri: String): Pair<List<Track>, AlbumInfo> = withContext(Dispatchers.IO) {
        val json = SpotifyBridge.nativeGetAlbumMetadata(uri)
        parseAlbumWithTracks(JSONObject(json))
    }

    suspend fun getArtistInfo(uri: String): ArtistInfo = withContext(Dispatchers.IO) {
        val json = SpotifyBridge.nativeGetArtistMetadata(uri)
        parseArtistInfo(JSONObject(json))
    }

    suspend fun getPlaylistTracks(uri: String): Pair<List<Track>, String> = withContext(Dispatchers.IO) {
        val json = SpotifyBridge.nativeGetPlaylistMetadata(uri)
        val obj = JSONObject(json)
        val name = obj.optString("name", "Playlist")
        val tracks = parseTrackList(obj.optJSONArray("tracks") ?: JSONArray())
        tracks to name
    }

    suspend fun downloadTrackToFile(uri: String, outputPath: String): AudioFormat = withContext(Dispatchers.IO) {
        val success = SpotifyBridge.nativeDownloadTrack(uri, outputPath, "VERY_HIGH")
        if (!success) throw IOException("Failed to download Spotify track: $uri")
        AudioFormat.OGG_VORBIS_320
    }

    // --- JSON Parsing ---

    private fun parseSearchResults(json: JSONObject): List<SearchResult> {
        val results = mutableListOf<SearchResult>()

        json.optJSONArray("tracks")?.let { arr ->
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                results.add(SearchResult(
                    type = SearchResultType.SPOTIFY_TRACK,
                    name = item.optString("name"),
                    url = item.optString("uri"),
                    imageUrl = item.optString("imageUrl").takeIf { it.isNotEmpty() },
                    artist = item.optString("artist").takeIf { it.isNotEmpty() },
                    album = item.optString("album").takeIf { it.isNotEmpty() },
                    genre = null,
                    releaseDate = null,
                ))
            }
        }

        json.optJSONArray("albums")?.let { arr ->
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                results.add(SearchResult(
                    type = SearchResultType.SPOTIFY_ALBUM,
                    name = item.optString("name"),
                    url = item.optString("uri"),
                    imageUrl = item.optString("imageUrl").takeIf { it.isNotEmpty() },
                    artist = item.optString("artist").takeIf { it.isNotEmpty() },
                    album = null,
                    genre = null,
                    releaseDate = item.optString("releaseDate").takeIf { it.isNotEmpty() },
                ))
            }
        }

        json.optJSONArray("artists")?.let { arr ->
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                results.add(SearchResult(
                    type = SearchResultType.SPOTIFY_ARTIST,
                    name = item.optString("name"),
                    url = item.optString("uri"),
                    imageUrl = item.optString("imageUrl").takeIf { it.isNotEmpty() },
                    artist = null,
                    album = null,
                    genre = null,
                    releaseDate = null,
                ))
            }
        }

        json.optJSONArray("playlists")?.let { arr ->
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                results.add(SearchResult(
                    type = SearchResultType.SPOTIFY_PLAYLIST,
                    name = item.optString("name"),
                    url = item.optString("uri"),
                    imageUrl = item.optString("imageUrl").takeIf { it.isNotEmpty() },
                    artist = item.optString("owner").takeIf { it.isNotEmpty() },
                    album = null,
                    genre = null,
                    releaseDate = null,
                ))
            }
        }

        return results
    }

    private fun parseTrack(json: JSONObject): Track {
        val spotifyId = extractSpotifyId(json.optString("uri", ""))
        return Track(
            id = "sp_$spotifyId",
            albumId = "sp_album_${extractSpotifyId(json.optString("albumUri", spotifyId))}",
            title = json.optString("name", "Unknown"),
            artist = json.optString("artist", "Unknown"),
            artistUrl = json.optString("artistUri", ""),
            trackNumber = json.optInt("trackNumber", 0),
            duration = json.optLong("durationMs", 0).toFloat() / 1000f,
            streamUrl = json.optString("uri"),
            artUrl = json.optString("imageUrl", ""),
            albumTitle = json.optString("album", ""),
            source = TrackSource.SPOTIFY,
        )
    }

    private fun parseTrackList(arr: JSONArray): List<Track> {
        val tracks = mutableListOf<Track>()
        for (i in 0 until arr.length()) {
            tracks.add(parseTrack(arr.getJSONObject(i)))
        }
        return tracks
    }

    private fun parseAlbumWithTracks(json: JSONObject): Pair<List<Track>, AlbumInfo> {
        val tracks = parseTrackList(json.optJSONArray("tracks") ?: JSONArray())
        val albumInfo = AlbumInfo(
            uri = json.optString("uri"),
            name = json.optString("name", "Unknown Album"),
            artist = json.optString("artist", "Unknown"),
            artistUri = json.optString("artistUri", ""),
            imageUrl = json.optString("imageUrl"),
            releaseDate = json.optString("releaseDate").takeIf { it.isNotEmpty() },
        )
        return tracks to albumInfo
    }

    private fun parseArtistInfo(json: JSONObject): ArtistInfo {
        val topTracks = parseTrackList(json.optJSONArray("topTracks") ?: JSONArray())
        val albums = mutableListOf<AlbumInfo>()
        json.optJSONArray("albums")?.let { arr ->
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                albums.add(AlbumInfo(
                    uri = item.optString("uri"),
                    name = item.optString("name"),
                    artist = json.optString("name"),
                    artistUri = json.optString("uri"),
                    imageUrl = item.optString("imageUrl").takeIf { it.isNotEmpty() },
                    releaseDate = item.optString("releaseDate").takeIf { it.isNotEmpty() },
                ))
            }
        }
        return ArtistInfo(
            uri = json.optString("uri"),
            name = json.optString("name", "Unknown"),
            imageUrl = json.optString("imageUrl"),
            topTracks = topTracks,
            albums = albums,
        )
    }

    private fun extractSpotifyId(uri: String): String {
        // "spotify:track:abc123" -> "abc123"
        return uri.substringAfterLast(":")
    }
}

data class AlbumInfo(
    val uri: String,
    val name: String,
    val artist: String,
    val artistUri: String,
    val imageUrl: String?,
    val releaseDate: String?,
)

data class ArtistInfo(
    val uri: String,
    val name: String,
    val imageUrl: String?,
    val topTracks: List<Track>,
    val albums: List<AlbumInfo>,
)
