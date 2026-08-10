package com.dustvalve.next.android.util

import android.content.Context
import android.net.ConnectivityManager
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.URISyntaxException
import java.net.UnknownHostException

object NetworkUtils {

    private const val BYTE_MASK = 0xff
    private const val CGNAT_FIRST_OCTET = 100
    private const val CGNAT_SECOND_MIN = 64
    private const val CGNAT_SECOND_MAX = 127
    private const val TEST_NET_1_FIRST = 192
    private const val TEST_NET_1_THIRD = 2
    private const val BENCHMARK_FIRST = 198
    private const val BENCHMARK_SECOND_MIN = 18
    private const val BENCHMARK_SECOND_MAX = 19
    private const val IPV6_ULA_MASK = 0xfe
    private const val IPV6_ULA_PREFIX = 0xfc
    private const val IPV4_BYTE_COUNT = 4
    private const val IPV6_BYTE_COUNT = 16
    private const val IPV4_MAPPED_ZERO_PREFIX_LEN = 10
    private const val IPV4_MAPPED_MARKER_INDEX = 10
    private const val IPV4_MAPPED_MARKER_BYTE = 0xff

    private val DUSTVALVE_HOST_REGEX = Regex(
        """^(?:[\w-]+\.)?bandcamp\.com$""",
        RegexOption.IGNORE_CASE,
    )

    /** Bandcamp page + CDN hosts accepted on playlist import. */
    private val BANDCAMP_IMPORT_HOSTS = listOf("bandcamp.com", "bcbits.com")

    /** YouTube / Google media + art hosts accepted on playlist import. */
    private val YOUTUBE_IMPORT_HOSTS = listOf(
        "youtube.com",
        "youtu.be",
        "googlevideo.com",
        "ytimg.com",
        "ggpht.com",
        "googleusercontent.com",
        "gvt1.com",
        "youtube-nocookie.com",
    )

    /** SoundCloud page + CDN hosts accepted on playlist import. */
    private val SOUNDCLOUD_IMPORT_HOSTS = listOf("soundcloud.com", "sndcdn.com")

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
     * True when [host] is loopback, link-local, RFC1918, CGNAT (100.64.0.0/10),
     * IPv6 ULA (fc00::/7), or a localhost/.local name. Literal IPs are checked
     * without DNS; hostnames are resolved and every answer is checked so a
     * public-looking name cannot SSRF into a private address.
     */
    fun isDisallowedPrivateHost(host: String): Boolean {
        val normalized = normalizeHost(host) ?: return true
        if (isLiteralDisallowedHost(normalized)) return true
        return try {
            InetAddress.getAllByName(normalized).any(::isDisallowedAddress)
        } catch (_: UnknownHostException) {
            // NXDOMAIN / no answers: not a private hit; callers still fail on connect.
            false
        } catch (_: SecurityException) {
            true
        }
    }

    /**
     * Literal-only private/local check (no DNS). Used on playlist-import
     * sanitization where resolving attacker-controlled names is undesirable.
     */
    fun isLiteralDisallowedHost(host: String): Boolean {
        val normalized = normalizeHost(host) ?: return true
        if (normalized == "localhost" ||
            normalized.endsWith(".localhost") ||
            normalized.endsWith(".local")
        ) {
            return true
        }
        val literal = parseLiteralIp(normalized) ?: return false
        return isDisallowedAddress(literal)
    }

    /** True when [address] must not be contacted for user-supplied fetches. */
    fun isDisallowedAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress) {
            return true
        }
        if (address.isSiteLocalAddress || address.isMulticastAddress) {
            return true
        }
        return when (address) {
            is Inet4Address -> isCgNat(address) || isCarrierGradeOrBenchmark(address)
            is Inet6Address -> isUniqueLocalIpv6(address) || isIpv4MappedDisallowed(address)
            else -> false
        }
    }

    /**
     * Throws [IllegalArgumentException] when [url] targets a disallowed private
     * or link-local host. Call before opening a connection to user-supplied URLs.
     */
    fun requirePublicRemoteUrl(url: String) {
        val host = try {
            URI(url).host
        } catch (_: URISyntaxException) {
            null
        } ?: throw IllegalArgumentException("refusing URL without host: $url")
        if (isDisallowedPrivateHost(host)) {
            throw IllegalArgumentException("refusing private/link-local host: $host")
        }
    }

    /**
     * Playlist-import allowlist: https only, known provider hosts for [sourceKey],
     * and never a literal private/link-local host. Non-matching URLs become "".
     */
    fun sanitizeImportedMediaUrl(url: String?, sourceKey: String): String {
        if (url.isNullOrBlank()) return ""
        if (!url.startsWith("https://", ignoreCase = true)) return ""
        val host = try {
            URI(url).host
        } catch (_: URISyntaxException) {
            null
        }?.lowercase() ?: return ""
        if (isLiteralDisallowedHost(host)) return ""
        if (!isAllowedImportHost(host, sourceKey)) return ""
        return url
    }

    /** Null-safe blanking for optional import URLs (e.g. playlist iconUrl). */
    fun sanitizeImportedOptionalUrl(url: String?, sourceKey: String): String? {
        val cleaned = sanitizeImportedMediaUrl(url, sourceKey)
        return cleaned.ifBlank { null }
    }

    private fun isAllowedImportHost(host: String, sourceKey: String): Boolean {
        val hosts = when (sourceKey.lowercase()) {
            "bandcamp" -> BANDCAMP_IMPORT_HOSTS

            "youtube" -> YOUTUBE_IMPORT_HOSTS

            "soundcloud" -> SOUNDCLOUD_IMPORT_HOSTS

            "local" -> emptyList()

            // Playlist icon / unknown source: any known media host is fine.
            else -> BANDCAMP_IMPORT_HOSTS + YOUTUBE_IMPORT_HOSTS + SOUNDCLOUD_IMPORT_HOSTS
        }
        return hosts.any { suffix -> host == suffix || host.endsWith(".$suffix") }
    }

    private fun normalizeHost(host: String): String? {
        val trimmed = host.trim().lowercase()
            .removePrefix("[")
            .removeSuffix("]")
        return trimmed.ifEmpty { null }
    }

    private fun parseLiteralIp(host: String): InetAddress? = try {
        // getByName still resolves hostnames; only accept literals so we do
        // not trigger DNS inside literal-only checks.
        val looksIpv4 = host.all { it.isDigit() || it == '.' }
        val looksIpv6 = host.contains(':')
        if (!looksIpv4 && !looksIpv6) return null
        InetAddress.getByName(host)
    } catch (_: Exception) {
        null
    }

    /** CGNAT shared address space 100.64.0.0/10 (RFC 6598). */
    private fun isCgNat(address: Inet4Address): Boolean {
        val b = address.address
        val first = b[0].toInt() and BYTE_MASK
        val second = b[1].toInt() and BYTE_MASK
        return first == CGNAT_FIRST_OCTET && second in CGNAT_SECOND_MIN..CGNAT_SECOND_MAX
    }

    /** 0.0.0.0/8 already covered by isAnyLocal; also block 169.254/16 via link-local. */
    private fun isCarrierGradeOrBenchmark(address: Inet4Address): Boolean {
        val b = address.address
        val first = b[0].toInt() and BYTE_MASK
        val second = b[1].toInt() and BYTE_MASK
        val third = b[2].toInt() and BYTE_MASK
        // TEST-NET and documentation ranges are not useful as stream targets.
        return first == 0 ||
            (first == TEST_NET_1_FIRST && second == 0 && third == TEST_NET_1_THIRD) ||
            (first == BENCHMARK_FIRST && second in BENCHMARK_SECOND_MIN..BENCHMARK_SECOND_MAX)
    }

    /** IPv6 unique-local addresses fc00::/7 (RFC 4193). */
    private fun isUniqueLocalIpv6(address: Inet6Address): Boolean {
        val b = address.address
        return (b[0].toInt() and IPV6_ULA_MASK) == IPV6_ULA_PREFIX
    }

    /** IPv4-mapped IPv6 that wraps a disallowed IPv4. */
    private fun isIpv4MappedDisallowed(address: Inet6Address): Boolean {
        if (!address.isIPv4CompatibleAddress && !isIpv4Mapped(address)) return false
        val bytes = address.address
        val v4 = InetAddress.getByAddress(bytes.copyOfRange(bytes.size - IPV4_BYTE_COUNT, bytes.size))
        return isDisallowedAddress(v4)
    }

    private fun isIpv4Mapped(address: Inet6Address): Boolean {
        val b = address.address
        if (b.size < IPV6_BYTE_COUNT) return false
        for (i in 0 until IPV4_MAPPED_ZERO_PREFIX_LEN) {
            if (b[i].toInt() != 0) return false
        }
        val mappedHi = b[IPV4_MAPPED_MARKER_INDEX].toInt() and BYTE_MASK
        val mappedLo = b[IPV4_MAPPED_MARKER_INDEX + 1].toInt() and BYTE_MASK
        return mappedHi == IPV4_MAPPED_MARKER_BYTE && mappedLo == IPV4_MAPPED_MARKER_BYTE
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
     * Size `_0` is Bandcamp's full-original JPEG tier. Returns empty string
     * for non-positive ids (unguarded `a0_0.jpg` is a persistent 404).
     */
    fun buildArtUrl(artId: Long): String {
        if (artId <= 0L) return ""
        return "https://f4.bcbits.com/img/a${artId}_0.jpg"
    }

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
