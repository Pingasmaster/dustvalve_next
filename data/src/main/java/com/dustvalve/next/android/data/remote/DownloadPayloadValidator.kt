package com.dustvalve.next.android.data.remote

import java.io.IOException

/**
 * Shared commit-time guards for downloaded audio bytes. Rejects HTML login
 * walls, JSON error bodies, and HLS playlists that would otherwise land as
 * "downloaded" tracks with a wrong extension. Used by [RangeResumeDownloader]
 * (headers + first-chunk sniff) and [DownloadRepositoryImpl] (final file).
 *
 * SoundCloud / Bandcamp resolve paths feed the same downloader - keep this
 * format-agnostic so provider-specific agents do not reimplement sniffing.
 */
object DownloadPayloadValidator {

    class InvalidPayloadException(message: String) : IOException(message)

    /**
     * Rejects Content-Types that can never be playable audio. Null / blank
     * types are allowed through so magic-byte sniffing can decide; servers
     * that omit Content-Type are common on CDNs.
     */
    fun assertAcceptableContentType(contentType: String?, trackId: String) {
        val mime = contentType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        if (mime.isEmpty()) return
        if (isRejectedMime(mime)) {
            throw InvalidPayloadException(
                "Rejected Content-Type '$mime' for track $trackId (not audio)",
            )
        }
        if (!isAcceptableMime(mime)) {
            throw InvalidPayloadException(
                "Unsupported Content-Type '$mime' for track $trackId",
            )
        }
    }

    /** True for MIME types that must never be committed as a download. */
    fun isRejectedMime(mime: String): Boolean {
        val m = mime.lowercase()
        return m.startsWith("text/html") ||
            m == "application/json" ||
            m == "text/json" ||
            m == "application/problem+json" ||
            m == "application/vnd.apple.mpegurl" ||
            m == "application/x-mpegurl" ||
            m == "audio/mpegurl" ||
            m == "audio/x-mpegurl" ||
            m == "application/dash+xml" ||
            m.startsWith("text/plain")
    }

    fun isAcceptableMime(mime: String): Boolean {
        val m = mime.lowercase()
        if (m.startsWith("audio/")) return !isRejectedMime(m)
        // Containers / aliases used by YouTube (webm/opus), AAC, Ogg.
        return m == "application/ogg" ||
            m == "application/octet-stream" ||
            m == "binary/octet-stream" ||
            m == "video/webm" ||
            m == "video/mp4"
    }

    /**
     * Maps a response Content-Type to a file extension. Returns null when the
     * type is missing or too generic to choose (octet-stream) - callers then
     * fall back to magic sniffing or the stream format's default extension.
     */
    fun extensionForMime(contentType: String?): String? {
        val mime = contentType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        if (mime.isEmpty() || mime == "application/octet-stream" || mime == "binary/octet-stream") {
            return null
        }
        return when {
            mime == "audio/mpeg" || mime == "audio/mp3" || mime == "audio/x-mpeg" -> "mp3"
            mime == "audio/flac" || mime == "audio/x-flac" -> "flac"
            mime == "audio/ogg" || mime == "application/ogg" || mime == "audio/vorbis" -> "ogg"
            mime == "audio/opus" || mime == "video/webm" || mime == "audio/webm" -> "webm"
            mime == "audio/mp4" || mime == "audio/aac" || mime == "audio/x-m4a" || mime == "video/mp4" -> "m4a"
            mime == "audio/wav" || mime == "audio/x-wav" || mime == "audio/wave" -> "wav"
            else -> null
        }
    }

    /**
     * Sniffs well-known audio container magic. Also detects HTML / JSON / M3U
     * payloads that slipped past a missing or lying Content-Type.
     *
     * @return Suggested extension, or null when the bytes are audio-like but
     *   the container is not identified (e.g. raw MPEG frame without ID3).
     * @throws InvalidPayloadException when the payload is clearly not audio.
     */
    fun sniffExtensionOrReject(header: ByteArray, trackId: String): String? {
        if (header.isEmpty()) {
            throw InvalidPayloadException("Empty payload for track $trackId")
        }
        if (looksLikeHtml(header) || looksLikeJson(header) || looksLikeM3u(header)) {
            throw InvalidPayloadException(
                "Downloaded payload is not audio (HTML/JSON/playlist) for track $trackId",
            )
        }
        return when {
            startsWith(header, "ID3") -> "mp3"
            header.size >= 2 && (header[0] == 0xFF.toByte() && (header[1].toInt() and 0xE0) == 0xE0) -> "mp3"
            startsWith(header, "fLaC") -> "flac"
            startsWith(header, "OggS") -> "ogg"
            startsWith(header, "RIFF") && header.size >= 12 &&
                String(header, 8, 4, Charsets.US_ASCII) == "WAVE" -> "wav"
            hasFtypBrand(header, "M4A", "mp4", "isom", "iso2", "MSDH") -> "m4a"
            startsWith(header, byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte())) -> "webm"
            else -> null
        }
    }

    private fun looksLikeHtml(header: ByteArray): Boolean {
        val s = asciiPrefix(header, 64).trimStart().lowercase()
        return s.startsWith("<!doctype html") ||
            s.startsWith("<html") ||
            s.startsWith("<head") ||
            s.startsWith("<body") ||
            s.startsWith("<?xml") && s.contains("<html")
    }

    private fun looksLikeJson(header: ByteArray): Boolean {
        val s = asciiPrefix(header, 32).trimStart()
        return s.startsWith("{") || s.startsWith("[")
    }

    private fun looksLikeM3u(header: ByteArray): Boolean {
        val s = asciiPrefix(header, 16).trimStart().uppercase()
        return s.startsWith("#EXTM3U") || s.startsWith("#EXT-X-")
    }

    private fun startsWith(header: ByteArray, ascii: String): Boolean {
        if (header.size < ascii.length) return false
        for (i in ascii.indices) {
            if (header[i] != ascii[i].code.toByte()) return false
        }
        return true
    }

    private fun startsWith(header: ByteArray, magic: ByteArray): Boolean {
        if (header.size < magic.size) return false
        for (i in magic.indices) {
            if (header[i] != magic[i]) return false
        }
        return true
    }

    private fun hasFtypBrand(header: ByteArray, vararg brands: String): Boolean {
        // ISO BMFF: size(4) + 'ftyp'(4) + major_brand(4)
        if (header.size < 12) return false
        if (header[4] != 'f'.code.toByte() ||
            header[5] != 't'.code.toByte() ||
            header[6] != 'y'.code.toByte() ||
            header[7] != 'p'.code.toByte()
        ) {
            return false
        }
        val brand = String(header, 8, 4, Charsets.US_ASCII)
        return brands.any { brand.startsWith(it, ignoreCase = true) || brand.equals(it, ignoreCase = true) }
    }

    private fun asciiPrefix(header: ByteArray, max: Int): String {
        val n = header.size.coerceAtMost(max)
        return String(header, 0, n, Charsets.US_ASCII)
    }
}
