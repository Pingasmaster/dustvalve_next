package com.dustvalve.next.android.data.remote.youtubemusic

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves a YT Music album browse id (`MPREb_...`) to the album's
 * audio playlist id (`OLAK5uy_...`) by POSTing /browse and reading the
 * album page carefully.
 *
 * Preference order (do NOT DFS-first into related carousels - those often
 * carry a different album's OLAK):
 *   1. `microformat...urlCanonical` list=OLAK...
 *   2. dedicated `audioPlaylistId` in the album header / shelf region
 *   3. first track `watchEndpoint.playlistId` (OLAK) on the album shelf
 *   4. `playlistId` OLAK on the album shelf / header menu only
 *
 * Shared by
 * [com.dustvalve.next.android.data.repository.YouTubeMusicRepositoryImpl]
 * (step 2 of the video -> album lookup) and
 * [com.dustvalve.next.android.data.repository.YouTubeRepositoryImpl]
 * (opening `playlist?list=MPREb_...` URLs emitted by YTM album search
 * results, which are not directly browsable as playlists).
 *
 * Network / API failures propagate to the caller (including
 * [kotlinx.coroutines.CancellationException]); a `null` return always means
 * the browse succeeded but the response carried no album playlist id.
 */
@Singleton
class YouTubeMusicAlbumResolver @Inject constructor(private val client: YouTubeMusicInnertubeClient) {

    suspend fun resolveAudioPlaylistId(albumBrowseId: String): String? = findAudioPlaylistId(client.browse(albumBrowseId))

    private fun findAudioPlaylistId(root: JsonElement): String? {
        extractOlakFromUrlCanonical(root)?.let { return it }
        findInAlbumRegions(root, preferAudioPlaylistId = true)?.let { return it }
        findFirstTrackWatchPlaylistId(root)?.let { return it }
        findInAlbumRegions(root, preferAudioPlaylistId = false)?.let { return it }
        return null
    }

    private fun extractOlakFromUrlCanonical(root: JsonElement): String? {
        val canonical = (root as? JsonObject)
            ?.let { it["microformat"] as? JsonObject }
            ?.let { it["microformatDataRenderer"] as? JsonObject }
            ?.let { (it["urlCanonical"] as? JsonPrimitive)?.content }
            ?: return null
        val match = OLAK_IN_URL.find(canonical) ?: return null
        return match.groupValues[1]
    }

    /**
     * Walks album-owned regions only: header, contents (excluding related
     * carousels), secondaryContents, microformat, frameworkUpdates. Related
     * `musicCarouselShelfRenderer` nodes are skipped so a "Fans also like"
     * OLAK cannot win.
     */
    private fun findInAlbumRegions(root: JsonElement, preferAudioPlaylistId: Boolean): String? {
        if (root !is JsonObject) return null
        val regions = listOfNotNull(
            root["header"],
            root["microformat"],
            root["frameworkUpdates"],
            root["contents"],
        )
        for (region in regions) {
            findOlakPreferential(region, preferAudioPlaylistId, skipCarousels = true)?.let { return it }
        }
        return null
    }

    private fun findFirstTrackWatchPlaylistId(root: JsonElement): String? {
        val shelfCandidates = mutableListOf<JsonElement>()
        collectAlbumShelves(root, shelfCandidates)
        for (shelf in shelfCandidates) {
            val contents = when (shelf) {
                is JsonObject -> (shelf["contents"] as? JsonArray)
                    ?: ((shelf["musicPlaylistShelfRenderer"] as? JsonObject)?.get("contents") as? JsonArray)
                    ?: ((shelf["musicShelfRenderer"] as? JsonObject)?.get("contents") as? JsonArray)

                else -> null
            } ?: continue
            for (row in contents) {
                extractWatchEndpointPlaylistId(row)?.let { return it }
            }
        }
        return null
    }

    private fun collectAlbumShelves(el: JsonElement?, out: MutableList<JsonElement>) {
        when (el) {
            is JsonObject -> {
                if (el.containsKey("musicPlaylistShelfRenderer") || el.containsKey("musicShelfRenderer")) {
                    out += el
                }
                // Skip related carousels entirely.
                if (el.containsKey("musicCarouselShelfRenderer")) return
                el.values.forEach { collectAlbumShelves(it, out) }
            }

            is JsonArray -> el.forEach { collectAlbumShelves(it, out) }

            else -> Unit
        }
    }

    private fun extractWatchEndpointPlaylistId(el: JsonElement?): String? {
        when (el) {
            is JsonObject -> {
                val watch = el["watchEndpoint"] as? JsonObject
                val playlistId = (watch?.get("playlistId") as? JsonPrimitive)?.content
                if (playlistId != null && playlistId.startsWith(OLAK_PREFIX)) return playlistId
                for (v in el.values) {
                    // Do not walk into nested carousels while scanning a row.
                    if (v is JsonObject && v.containsKey("musicCarouselShelfRenderer")) continue
                    extractWatchEndpointPlaylistId(v)?.let { return it }
                }
            }

            is JsonArray -> for (v in el) extractWatchEndpointPlaylistId(v)?.let { return it }

            else -> Unit
        }
        return null
    }

    private fun findOlakPreferential(
        el: JsonElement?,
        preferAudioPlaylistId: Boolean,
        skipCarousels: Boolean,
    ): String? {
        when (el) {
            is JsonObject -> {
                if (skipCarousels && el.containsKey("musicCarouselShelfRenderer")) return null
                playlistIdFromObject(el, preferAudioPlaylistId)?.let { return it }
                for (v in el.values) {
                    findOlakPreferential(v, preferAudioPlaylistId, skipCarousels)?.let { return it }
                }
            }

            is JsonArray -> for (v in el) {
                findOlakPreferential(v, preferAudioPlaylistId, skipCarousels)?.let { return it }
            }

            else -> Unit
        }
        return null
    }

    private fun playlistIdFromObject(obj: JsonObject, preferAudioPlaylistId: Boolean): String? {
        if (preferAudioPlaylistId) {
            val audio = (obj["audioPlaylistId"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
            if (audio != null) return audio
            return null
        }
        return (obj["playlistId"] as? JsonPrimitive)?.content?.takeIf { it.startsWith(OLAK_PREFIX) }
    }

    private companion object {
        const val OLAK_PREFIX = "OLAK"
        val OLAK_IN_URL = Regex("""[?&]list=(OLAK[A-Za-z0-9_-]+)""")
    }
}
