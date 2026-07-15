package com.dustvalve.next.android.util

import android.content.Context
import android.net.ConnectivityManager
import java.net.URI

object NetworkUtils {

    private val DUSTVALVE_HOST_REGEX = Regex(
        """^(?:[\w-]+\.)?bandcamp\.com$""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Returns true if the URL is a valid HTTPS URL with a non-empty hostname.
     * This validates URL structure only — it does NOT verify the URL belongs to Dustvalve.
     * Use [isDustvalveDomain] for strict Dustvalve domain checks.
     */
    fun isValidHttpsUrl(url: String): Boolean {
        return try {
            val uri = URI(url)
            if (uri.scheme != "https") return false
            val host = uri.host ?: return false
            host.isNotEmpty() && host.contains('.')
        } catch (e: Exception) {
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
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Builds the art/image URL for a given Dustvalve art ID.
     * Returns a URL in the format used by Dustvalve's CDN for album/artist artwork.
     */
    fun buildArtUrl(artId: Long): String = "https://f4.bcbits.com/img/a${artId}_10.jpg"

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
