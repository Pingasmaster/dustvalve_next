package com.dustvalve.next.android.data.remote.youtube.innertube

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Innertube client identities used by the first-party YouTube layer. Every
 * variant here is chosen so the /player response carries adaptiveFormats
 * with direct, signature-free URLs (no nsig/sig deciphering, no PoToken).
 *
 * - [ANDROID_VR_NO_AUTH]: primary /player client. Returns AAC + Opus URLs
 *   that play back without ciphering. Trade-off: no Premium 256 kbps Opus,
 *   no age-gated video.
 * - [IOS]: /player fallback when ANDROID_VR returns no audio formats (rare,
 *   typically transient). Returns the same kind of direct URLs.
 * - [WEB_NO_AUTH]: used for /search, /browse (channel), and unauthenticated
 *   list endpoints whose JSON response uses the standard videoRenderer /
 *   playlistRenderer / channelRenderer shape. The clientVersion is sourced
 *   from [YouTubeVisitorDataFetcher] so it auto-tracks Google's rotations.
 * - [MWEB_NO_AUTH]: used for /browse (playlist) and /next, where MWEB is
 *   the only public client that still emits the legacy
 *   playlistVideoListRenderer / videoWithContextRenderer shapes. Targets
 *   m.youtube.com (not www) because Innertube enforces Origin == Host for
 *   MWEB. The clientVersion is also sourced from the visitor data fetcher.
 */
sealed class YouTubeClient(
    val clientName: String,
    val clientNameCode: String,
    val userAgent: String,
    val origin: String,
    val referer: String,
) {
    /** Builds the JSON `context.client` block this client expects. */
    abstract fun toContext(visitorData: String, clientVersion: String): JsonObject

    /** The version this client should report. Some clients use a fixed version. */
    open fun resolveClientVersion(dynamic: String): String = dynamic

    object ANDROID_VR_NO_AUTH : YouTubeClient(
        clientName = "ANDROID_VR",
        clientNameCode = "28",
        userAgent = "com.google.android.apps.youtube.vr.oculus/1.61.48 " +
            "(Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
        origin = "https://www.youtube.com",
        referer = "https://www.youtube.com",
    ) {
        const val VERSION = "1.61.48"
        override fun resolveClientVersion(dynamic: String): String = VERSION
        override fun toContext(visitorData: String, clientVersion: String): JsonObject =
            buildJsonObject {
                put("clientName", "ANDROID_VR")
                put("clientVersion", VERSION)
                put("androidSdkVersion", 32)
                put("osName", "Android")
                put("osVersion", "12L")
                put("hl", "en")
                put("gl", "US")
                put("visitorData", visitorData)
            }
    }

    object IOS : YouTubeClient(
        clientName = "IOS",
        clientNameCode = "5",
        userAgent = "com.google.ios.youtube/21.03.1 " +
            "(iPhone16,2; CPU iOS 18_2 like Mac OS X; en_US)",
        origin = "https://www.youtube.com",
        referer = "https://www.youtube.com",
    ) {
        const val VERSION = "21.03.1"
        override fun resolveClientVersion(dynamic: String): String = VERSION
        override fun toContext(visitorData: String, clientVersion: String): JsonObject =
            buildJsonObject {
                put("clientName", "IOS")
                put("clientVersion", VERSION)
                put("deviceMake", "Apple")
                put("deviceModel", "iPhone16,2")
                put("osName", "iOS")
                put("osVersion", "18.2.21C5054b")
                put("hl", "en")
                put("gl", "US")
                put("visitorData", visitorData)
            }
    }

    object WEB_NO_AUTH : YouTubeClient(
        clientName = "WEB",
        clientNameCode = "1",
        userAgent = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        origin = "https://www.youtube.com",
        referer = "https://www.youtube.com",
    ) {
        override fun toContext(visitorData: String, clientVersion: String): JsonObject =
            buildJsonObject {
                put("clientName", "WEB")
                put("clientVersion", clientVersion)
                put("hl", "en")
                put("gl", "US")
                put("visitorData", visitorData)
            }
    }

    object MWEB_NO_AUTH : YouTubeClient(
        clientName = "MWEB",
        clientNameCode = "2",
        userAgent = "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
        origin = "https://m.youtube.com",
        referer = "https://m.youtube.com",
    ) {
        override fun toContext(visitorData: String, clientVersion: String): JsonObject =
            buildJsonObject {
                put("clientName", "MWEB")
                put("clientVersion", clientVersion)
                put("hl", "en")
                put("gl", "US")
                put("visitorData", visitorData)
            }
    }
}
