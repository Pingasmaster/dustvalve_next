package com.dustvalve.next.android.data.remote.youtubemusic

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves a YT Music album browse id (`MPREb_...`) to the album's
 * `audioPlaylistId` - the `OLAK5uy_...` playlist the YTM UI plays - by
 * POSTing /browse and walking the response for the first non-blank
 * `audioPlaylistId` field.
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
 * the browse succeeded but the response carried no audioPlaylistId.
 */
@Singleton
class YouTubeMusicAlbumResolver @Inject constructor(private val client: YouTubeMusicInnertubeClient) {

    suspend fun resolveAudioPlaylistId(albumBrowseId: String): String? = findAudioPlaylistId(client.browse(albumBrowseId))

    private fun findAudioPlaylistId(root: JsonElement): String? {
        when (root) {
            is JsonObject -> {
                (root["audioPlaylistId"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
                    ?.let { return it }
                for (v in root.values) findAudioPlaylistId(v)?.let { return it }
            }

            is JsonArray -> for (v in root) findAudioPlaylistId(v)?.let { return it }

            else -> Unit
        }
        return null
    }
}
