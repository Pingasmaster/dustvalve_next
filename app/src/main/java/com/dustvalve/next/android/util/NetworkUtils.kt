package com.dustvalve.next.android.util

import android.content.Context
import android.net.ConnectivityManager
import java.net.URI
import java.net.URLEncoder

object NetworkUtils {

    private val DUSTVALVE_HOST_REGEX = Regex(
        """^(?:[\w-]+\.)?bandcamp\.com$""",
        RegexOption.IGNORE_CASE
    )

    private val NON_ARTIST_PATHS = setOf(
        "search", "discover", "api", "login", "signup", "tag", "help"
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
     * Extracts the artist slug from a Dustvalve URL.
     * For URLs like "https://artistname.bandcamp.com/...", returns "artistname".
     * For URLs like "https://bandcamp.com/artistname", returns "artistname".
     * Returns null if extraction fails or the path is a known non-artist path.
     */
    fun extractArtistSlug(url: String): String? {
        return try {
            val uri = URI(url)
            val host = uri.host ?: return null

            // Check subdomain pattern: artistname.bandcamp.com
            if (host.endsWith(".bandcamp.com") && host != "bandcamp.com") {
                val subdomain = host.removeSuffix(".bandcamp.com")
                if (subdomain.isNotEmpty() && !subdomain.contains('.')) {
                    return subdomain
                }
            }

            // Check path pattern: bandcamp.com/artistname
            if (host.equals("bandcamp.com", ignoreCase = true)) {
                val path = uri.path ?: return null
                val segments = path.trim('/').split('/')
                if (segments.isNotEmpty() && segments[0].isNotEmpty()) {
                    val slug = segments[0]
                    if (slug.lowercase() in NON_ARTIST_PATHS) return null
                    return slug
                }
            }

            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Builds a Dustvalve search URL with the given query, page number, and optional item type filter.
     */
    fun buildSearchUrl(query: String, page: Int, itemType: String?): String {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val baseUrl = "https://bandcamp.com/search?q=$encodedQuery&page=$page"
        return if (itemType != null) {
            "$baseUrl&item_type=${URLEncoder.encode(itemType, "UTF-8")}"
        } else {
            baseUrl
        }
    }

    /**
     * Builds the art/image URL for a given Dustvalve art ID.
     * Returns a URL in the format used by Dustvalve's CDN for album/artist artwork.
     */
    fun buildArtUrl(artId: Long): String {
        return "https://f4.bcbits.com/img/a${artId}_16.jpg"
    }

    private val BCBITS_SIZE_REGEX = Regex("""(https://f4\.bcbits\.com/img/\w+)_\d+\.jpg""")

    /**
     * Upgrades a Bandcamp CDN image URL to size 16 (~700x700).
     * Returns the original URL unchanged if it doesn't match the bcbits pattern.
     */
    fun upgradeBandcampImageUrl(url: String): String {
        return BCBITS_SIZE_REGEX.matchEntire(url)?.let { "${it.groupValues[1]}_16.jpg" } ?: url
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
