package com.dustvalve.next.android.util

import android.content.Context
import android.net.ConnectivityManager
import java.net.URI
import java.net.URISyntaxException

object NetworkUtils {

    private val DUSTVALVE_HOST_REGEX = Regex(
        """^(?:[\w-]+\.)?bandcamp\.com$""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Returns true if the URL is a valid HTTPS URL with a non-empty hostname.
     * This validates URL structure only - it does NOT verify the URL belongs to Dustvalve.
     * Use [isDustvalveDomain] for strict Dustvalve domain checks.
     */
    fun isValidHttpsUrl(url: String): Boolean {
        return try {
            val uri = URI(url)
            if (uri.scheme != "https") return false
            val host = uri.host ?: return false
            host.isNotEmpty() && host.contains('.')
        } catch (_: URISyntaxException) {
            false
        }
    }

    /**
     * Returns true if the URL is on the bandcamp.com domain (strict check).
     * Use this for security-sensitive checks like cookie scoping.
     */
    fun isDustvalveDomain(url: String): Boolean {
        return try {
            val uri = URI(url)
            if (uri.scheme != "https") return false
            val host = uri.host ?: return false
            DUSTVALVE_HOST_REGEX.matches(host)
        } catch (_: URISyntaxException) {
            false
        }
    }

    /**
     * Builds the art/image URL for a given Dustvalve art ID.
     * Size `_0` is Bandcamp's full-original JPEG tier.
     */
    fun buildArtUrl(artId: Long): String = "https://f4.bcbits.com/img/a${artId}_0.jpg"

    /**
     * Rewrites a Bandcamp CDN image URL to the full-original `_0` size.
     * Leaves non-bcbits URLs and already-`_0` URLs unchanged. The `_1` PNG
     * sibling can be tens of MB; rewrite it to the JPEG `_0` instead.
     */
    fun upgradeBandcampArtUrl(url: String): String {
        if (!url.contains("bcbits.com/img/")) return url
        val match = Regex("""_(\d+)\.(jpg|png|webp)""", RegexOption.IGNORE_CASE).find(url)
            ?: return url
        val size = match.groupValues[1].toIntOrNull() ?: return url
        if (size == 0) return url
        // Prefer the JPEG full-original over the giant PNG `_1` sibling.
        val ext = if (size == 1) "jpg" else match.groupValues[2]
        return url.replaceRange(match.range, "_0.$ext")
    }

    /**
     * Sanitizes a file name by replacing any character not in [a-zA-Z0-9._-] with underscore.
     */
    fun sanitizeFileName(name: String): String {
        val sanitized = name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return if (sanitized.isBlank() || sanitized.all { it == '_' }) "unnamed" else sanitized
    }

    /**
     * Returns true if the active network connection is metered (e.g. mobile data).
     */
    fun isMeteredConnection(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        return cm.isActiveNetworkMetered
    }
}
