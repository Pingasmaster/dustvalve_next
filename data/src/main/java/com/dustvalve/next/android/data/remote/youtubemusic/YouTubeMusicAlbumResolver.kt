package com.dustvalve.next.android.data.remote.youtubemusic

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves a YT Music album browse id (`MPREb_...`) to the album's
 * audio playlist id (`OLAK5uy_...`) by POSTing /browse and walking the
 * response. Prefer the dedicated `audioPlaylistId` field when present;
 * fall back to any `playlistId` that starts with `OLAK` (current WEB_REMIX
 * album pages omit `audioPlaylistId` and only emit `playlistId`).
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

    private fun findAudioPlaylistId(root: JsonElement): String? = when (root) {
        is JsonObject -> playlistIdFromObject(root)
            ?: root.values.asSequence().mapNotNull { findAudioPlaylistId(it) }.firstOrNull()

        is JsonArray -> root.asSequence().mapNotNull { findAudioPlaylistId(it) }.firstOrNull()

        else -> null
    }

    private fun playlistIdFromObject(obj: JsonObject): String? {
        val audio = (obj["audioPlaylistId"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
        if (audio != null) return audio
        return (obj["playlistId"] as? JsonPrimitive)?.content?.takeIf { it.startsWith(OLAK_PREFIX) }
    }

    private companion object {
        const val OLAK_PREFIX = "OLAK"
    }
}
