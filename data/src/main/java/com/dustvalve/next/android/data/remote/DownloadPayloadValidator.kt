package com.dustvalve.next.android.data.remote

import java.io.IOException

/**
 * Shared commit-time guards for downloaded audio bytes. Rejects HTML login
 * walls, JSON/XML error bodies, and HLS playlists that would otherwise land as
 * "downloaded" tracks with a wrong extension. Used by [RangeResumeDownloader]
 * (headers + first-chunk sniff) and [DownloadRepositoryImpl] (final file).
 *
 * SoundCloud / Bandcamp resolve paths feed the same downloader - keep this
 * format-agnostic so provider-specific agents do not reimplement sniffing.
 */
object DownloadPayloadValidator {

    class InvalidPayloadException(message: String) : IOException(message)

    private const val TEXT_SNIFF_BYTES = 64
    private const val JSON_SNIFF_BYTES = 32
    private const val M3U_SNIFF_BYTES = 16
    private const val WAV_HEADER_MIN_BYTES = 12
    private const val FTYP_HEADER_MIN_BYTES = 12
    private const val FTYP_BRAND_OFFSET = 8
    private const val FTYP_ATOM_OFFSET = 4
    private const val FOURCC_LENGTH = 4
    private const val UNSIGNED_BYTE_MASK = 0xFF
    private const val MPEG_SYNC_SECOND_MASK = 0xE0
    private const val ASCII_TAB = 9
    private const val ASCII_LF = 10
    private const val ASCII_CR = 13
    private const val ASCII_PRINTABLE_MIN = 32
    private const val ASCII_PRINTABLE_MAX = 126
    private const val TEXT_PAYLOAD_PRINTABLE_PERCENT = 85
    private const val PERCENT_BASE = 100

    private val WEBM_MAGIC = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte())
    private val FTYP_ATOM = byteArrayOf(
        'f'.code.toByte(),
        't'.code.toByte(),
        'y'.code.toByte(),
        'p'.code.toByte(),
    )

    private val MIME_TO_EXTENSION = mapOf(
        "audio/mpeg" to "mp3",
        "audio/mp3" to "mp3",
        "audio/x-mpeg" to "mp3",
        "audio/flac" to "flac",
        "audio/x-flac" to "flac",
        "audio/ogg" to "ogg",
        "application/ogg" to "ogg",
        "audio/vorbis" to "ogg",
        "audio/opus" to "webm",
        "video/webm" to "webm",
        "audio/webm" to "webm",
        "audio/mp4" to "m4a",
        "audio/aac" to "m4a",
        "audio/x-m4a" to "m4a",
        "video/mp4" to "m4a",
        "audio/wav" to "wav",
        "audio/x-wav" to "wav",
        "audio/wave" to "wav",
    )

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
            m.startsWith("text/xml") ||
            m.startsWith("text/plain") ||
            m == "application/json" ||
            m == "text/json" ||
            m == "application/problem+json" ||
            m == "application/xml" ||
            m == "application/xhtml+xml" ||
            m == "application/soap+xml" ||
            m == "application/rss+xml" ||
            m == "application/atom+xml" ||
            m == "application/vnd.apple.mpegurl" ||
            m == "application/x-mpegurl" ||
            m == "audio/mpegurl" ||
            m == "audio/x-mpegurl" ||
            m == "application/dash+xml"
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
        if (mime.isEmpty() || isGenericOctetStream(mime)) return null
        return MIME_TO_EXTENSION[mime]
    }

    private fun isGenericOctetStream(mime: String): Boolean = mime == "application/octet-stream" || mime == "binary/octet-stream"

    /**
     * Sniffs well-known audio container magic. Also detects HTML / JSON / XML /
     * M3U / text-error payloads that slipped past a missing or lying
     * Content-Type. Unknown headers that look like printable text (error
     * pages, API envelopes) are rejected; binary-looking unknowns still
     * return null so callers can fall back to the stream format extension.
     *
     * @return Suggested extension, or null when the bytes are audio-like but
     *   the container is not identified (e.g. raw MPEG frame without ID3).
     * @throws InvalidPayloadException when the payload is clearly not audio.
     */
    fun sniffExtensionOrReject(header: ByteArray, trackId: String): String? {
        rejectIfEmpty(header, trackId)
        rejectIfNonAudioDocument(header, trackId)
        val ext = sniffKnownExtension(header)
        if (ext != null) return ext
        rejectIfTextPayload(header, trackId)
        return null
    }

    private fun rejectIfEmpty(header: ByteArray, trackId: String) {
        if (header.isEmpty()) {
            throw InvalidPayloadException("Empty payload for track $trackId")
        }
    }

    private fun rejectIfNonAudioDocument(header: ByteArray, trackId: String) {
        if (looksLikeNonAudioDocument(header)) {
            throw InvalidPayloadException(
                "Downloaded payload is not audio (HTML/XML/JSON/playlist) for track $trackId",
            )
        }
    }

    private fun rejectIfTextPayload(header: ByteArray, trackId: String) {
        if (looksLikeTextPayload(header)) {
            throw InvalidPayloadException(
                "Downloaded payload is not audio (unknown text/error magic) for track $trackId",
            )
        }
    }

    private fun looksLikeNonAudioDocument(header: ByteArray): Boolean = looksLikeHtml(header) ||
        looksLikeXml(header) ||
        looksLikeJson(header) ||
        looksLikeM3u(header)

    private fun sniffKnownExtension(header: ByteArray): String? = when {
        startsWith(header, "ID3") -> "mp3"
        isMpegFrameSync(header) -> "mp3"
        startsWith(header, "fLaC") -> "flac"
        startsWith(header, "OggS") -> "ogg"
        isWavHeader(header) -> "wav"
        hasFtypBrand(header, "M4A", "mp4", "isom", "iso2", "MSDH") -> "m4a"
        startsWith(header, WEBM_MAGIC) -> "webm"
        else -> null
    }

    private fun isMpegFrameSync(header: ByteArray): Boolean = header.size >= 2 &&
        header[0] == UNSIGNED_BYTE_MASK.toByte() &&
        (header[1].toInt() and MPEG_SYNC_SECOND_MASK) == MPEG_SYNC_SECOND_MASK

    private fun isWavHeader(header: ByteArray): Boolean = startsWith(header, "RIFF") &&
        header.size >= WAV_HEADER_MIN_BYTES &&
        String(header, FTYP_BRAND_OFFSET, FOURCC_LENGTH, Charsets.US_ASCII) == "WAVE"

    private fun looksLikeHtml(header: ByteArray): Boolean {
        val s = asciiPrefix(header, TEXT_SNIFF_BYTES).trimStart().lowercase()
        return s.startsWith("<!doctype html") ||
            s.startsWith("<html") ||
            s.startsWith("<head") ||
            s.startsWith("<body")
    }

    private fun looksLikeXml(header: ByteArray): Boolean {
        val s = asciiPrefix(header, TEXT_SNIFF_BYTES).trimStart().lowercase()
        return s.startsWith("<?xml") ||
            s.startsWith("<error") ||
            s.startsWith("<fault") ||
            s.startsWith("<soap") ||
            s.startsWith("<rss") ||
            s.startsWith("<feed")
    }

    private fun looksLikeJson(header: ByteArray): Boolean {
        val s = asciiPrefix(header, JSON_SNIFF_BYTES).trimStart()
        return s.startsWith("{") || s.startsWith("[")
    }

    private fun looksLikeM3u(header: ByteArray): Boolean {
        val s = asciiPrefix(header, M3U_SNIFF_BYTES).trimStart().uppercase()
        return s.startsWith("#EXTM3U") || s.startsWith("#EXT-X-")
    }

    /**
     * High printable-ASCII ratio with no recognized audio magic usually means
     * an error document or mislabeled text response, not a media container.
     */
    private fun looksLikeTextPayload(header: ByteArray): Boolean {
        val n = header.size.coerceAtMost(TEXT_SNIFF_BYTES)
        if (n == 0) return true
        var printable = 0
        for (i in 0 until n) {
            if (isPrintableAsciiByte(header[i].toInt() and UNSIGNED_BYTE_MASK)) printable++
        }
        return printable * PERCENT_BASE / n >= TEXT_PAYLOAD_PRINTABLE_PERCENT
    }

    private fun isPrintableAsciiByte(b: Int): Boolean = b == ASCII_TAB ||
        b == ASCII_LF ||
        b == ASCII_CR ||
        b in ASCII_PRINTABLE_MIN..ASCII_PRINTABLE_MAX

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
        if (header.size < FTYP_HEADER_MIN_BYTES) return false
        if (!startsWithAt(header, FTYP_ATOM_OFFSET, FTYP_ATOM)) return false
        val brand = String(header, FTYP_BRAND_OFFSET, FOURCC_LENGTH, Charsets.US_ASCII)
        return brands.any { brand.startsWith(it, ignoreCase = true) || brand.equals(it, ignoreCase = true) }
    }

    private fun startsWithAt(header: ByteArray, offset: Int, magic: ByteArray): Boolean {
        if (header.size < offset + magic.size) return false
        for (i in magic.indices) {
            if (header[offset + i] != magic[i]) return false
        }
        return true
    }

    private fun asciiPrefix(header: ByteArray, max: Int): String {
        val n = header.size.coerceAtMost(max)
        return String(header, 0, n, Charsets.US_ASCII)
    }
}
