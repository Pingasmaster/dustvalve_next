package com.dustvalve.next.android.data.remote.youtube.innertube

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Scrapes `ytcfg.set({...})` blocks from a YouTube / YT Music landing page
 * HTML. Shared by [YouTubeMusicVisitorDataFetcher] and
 * [YouTubeVisitorDataFetcher] — both need the same robust behaviour:
 *
 *  * Walks every `ytcfg.set(` occurrence, not just the first: Google sometimes
 *    emits small side-calls (`CSI_SERVICE_NAME`, `YTMUSIC_INITIAL_DATA`)
 *    alongside the real INNERTUBE_CONTEXT block and the order is not stable.
 *  * Uses a balanced-brace walker (respecting JSON strings) rather than a
 *    lazy regex, so nested `{}` inside the body (e.g. `"TIMING_INFO": {}`)
 *    don't truncate the capture.
 *  * Does not require a terminal `;` — Google emits `ytcfg.set({...}))</script>`
 *    in some variants.
 *  * Returns the first body that carries either `INNERTUBE_CONTEXT` or a
 *    top-level `VISITOR_DATA` — we only care about the block with auth context.
 */
internal object YouTubeYtcfgExtractor {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    data class YtcfgData(
        val visitorData: String,
        val clientVersion: String?,
    )

    /**
     * Returns the first ytcfg body that yields a non-empty visitorData, or
     * null if no such block exists in [html]. [clientVersion] is also
     * extracted when present (look at `INNERTUBE_CLIENT_VERSION` then
     * `INNERTUBE_CONTEXT_CLIENT_VERSION`); otherwise null so the caller can
     * pick a client-specific default.
     */
    fun extract(html: String): YtcfgData? {
        for (body in allYtcfgBodies(html)) {
            val obj = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
                ?: continue
            val visitor = obj["INNERTUBE_CONTEXT"]?.jsonObject
                ?.get("client")?.jsonObject
                ?.get("visitorData")?.jsonPrimitive?.content
                ?: obj["VISITOR_DATA"]?.jsonPrimitive?.content
                ?: continue
            if (visitor.isBlank()) continue
            val version = obj["INNERTUBE_CLIENT_VERSION"]?.jsonPrimitive?.content
                ?: obj["INNERTUBE_CONTEXT_CLIENT_VERSION"]?.jsonPrimitive?.content
            return YtcfgData(visitorData = visitor, clientVersion = version)
        }
        return null
    }

    /** All brace-balanced JSON bodies passed to `ytcfg.set(...)` in [html]. */
    private fun allYtcfgBodies(html: String): Sequence<String> = sequence {
        val marker = "ytcfg.set("
        var searchFrom = 0
        while (true) {
            val call = html.indexOf(marker, searchFrom)
            if (call < 0) return@sequence
            val openBrace = html.indexOf('{', startIndex = call + marker.length)
            if (openBrace < 0) return@sequence
            val endBrace = findMatchingBrace(html, openBrace)
            if (endBrace < 0) {
                // Malformed call — skip past this occurrence and keep looking.
                searchFrom = call + marker.length
                continue
            }
            yield(html.substring(openBrace, endBrace + 1))
            searchFrom = endBrace + 1
        }
    }

    /**
     * Walks forward from [openIndex] (which must point at `{`) and returns
     * the index of the matching `}`, or -1 if the string ends first.
     * Respects JSON string literals (`"..."` with `\"` escapes) and JS
     * single-quoted strings (YT emits both in the same HTML).
     */
    private fun findMatchingBrace(s: String, openIndex: Int): Int {
        var depth = 0
        var i = openIndex
        val len = s.length
        while (i < len) {
            when (val c = s[i]) {
                '"', '\'' -> {
                    // Skip to the closing quote (same char), honouring
                    // backslash escapes. JSON uses `"`, but YT's ytcfg calls
                    // can wrap keys/values in `'` (seen in YT Music pages).
                    i = skipQuotedString(s, i, c)
                    if (i < 0) return -1
                }
                '{' -> {
                    depth++; i++
                }
                '}' -> {
                    depth--
                    if (depth == 0) return i
                    i++
                }
                else -> i++
            }
        }
        return -1
    }

    /**
     * Given [i] pointing at an opening quote [quote] (either `"` or `'`),
     * returns the index AFTER the matching closing quote, honouring `\`
     * escapes. Returns -1 if the string runs off the end.
     */
    private fun skipQuotedString(s: String, i: Int, quote: Char): Int {
        var j = i + 1
        val len = s.length
        while (j < len) {
            val c = s[j]
            if (c == '\\') {
                j += 2 // skip escaped char
                continue
            }
            if (c == quote) return j + 1
            j++
        }
        return -1
    }

}
