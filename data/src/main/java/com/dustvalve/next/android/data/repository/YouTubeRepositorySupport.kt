package com.dustvalve.next.android.data.repository

import com.dustvalve.next.android.data.remote.youtube.innertube.YouTubePlaylistParser
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.coroutines.cancellation.CancellationException

/**
 * Shared helpers for [YouTubeRepositoryImpl], extracted so that class stays
 * under the TooManyFunctions ceiling.
 */
internal object YouTubeRepositorySupport {

    const val RDAMPL_PREFIX = "RDAMPL"
    const val RDAMPL_PREFIX_LENGTH = 6

    /** Best-effort side effects: always propagate cancellation, swallow everything else. */
    suspend inline fun bestEffort(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // Intentionally ignored - caching / album lookup must not break callers.
        }
    }

    inline fun <T> bestEffortValue(fallback: T, block: () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        fallback
    }

    /**
     * Album / playlist radio IDs are `RDAMPL` + the inner playlist id
     * (`OLAK5uy_...`, `PL...`, or even `RDCLAK...`). Strip once so callers
     * can browse the source playlist or keep routing an inner mix.
     */
    fun stripRdAmplPrefix(playlistId: String): String =
        if (playlistId.startsWith(RDAMPL_PREFIX) && playlistId.length > RDAMPL_PREFIX_LENGTH) {
            playlistId.removePrefix(RDAMPL_PREFIX)
        } else {
            playlistId
        }

    fun requireMixPlaylistId(mixUrl: String, extractPlaylistId: (String) -> String?): String {
        val extractedId = extractPlaylistId(mixUrl)
            ?: throw IllegalArgumentException("Cannot extract mix playlistId from $mixUrl")
        // RDAMPL + OLAK/PL is handled in getPlaylistTracks (strip + browse).
        // Remaining RDAMPLRD* / RDGMEM / RDEM / RDCLAK / RD{video} use /next.
        val mixId = stripRdAmplPrefix(extractedId)
        if (!mixId.startsWith("RD")) {
            throw IllegalArgumentException(
                "Playlist $extractedId is not a Mix; open it as a regular playlist",
            )
        }
        return mixId
    }

    fun requireMixSeedOrSeedless(
        mixId: String,
        typed: YouTubePlaylistParser.MixContinuation?,
        seed: String?,
        isSeedlessMixId: (String) -> Boolean,
    ) {
        // Seeded mixes (RD{video}, RDAMVM, RDMM) need a videoId. Seedless
        // families (RDGMEM, RDEM, RDCLAK) accept playlistId alone on /next.
        if (typed == null && seed == null && !isSeedlessMixId(mixId)) {
            throw IllegalStateException(
                "Unsupported Mix id $mixId (no seed video and not a known seedless family)",
            )
        }
    }

    /**
     * Pulls ERROR / unviewable alert text from a playlist browse response.
     * Returns null when the response has no actionable alert.
     */
    fun playlistAlertMessage(root: JsonElement): String? {
        val alerts = (root as? JsonObject)?.get("alerts") as? JsonArray ?: return null
        return alerts.firstNotNullOfOrNull { alert -> fatalPlaylistAlertText(alert) }
    }

    private fun fatalPlaylistAlertText(alert: JsonElement): String? {
        val renderer = (alert as? JsonObject)?.get("alertRenderer") as? JsonObject ?: return null
        val type = (renderer["type"] as? JsonPrimitive)?.content
        val text = alertRunsText(renderer)
            ?: (renderer["text"] as? JsonObject)?.let { textObj ->
                (textObj["simpleText"] as? JsonPrimitive)?.content
            }
        if (text.isNullOrBlank()) return null
        val looksFatal = type.equals("ERROR", ignoreCase = true) ||
            text.contains("unviewable", ignoreCase = true) ||
            text.contains("unavailable", ignoreCase = true) ||
            text.contains("private", ignoreCase = true)
        return text.takeIf { looksFatal }
    }

    private fun alertRunsText(renderer: JsonObject): String? {
        val text = renderer["text"] as? JsonObject ?: return null
        val runs = text["runs"] as? JsonArray ?: return null
        return runs.mapNotNull { (it as? JsonObject)?.get("text") as? JsonPrimitive }
            .joinToString("") { it.content }
            .takeIf { it.isNotBlank() }
    }
}
