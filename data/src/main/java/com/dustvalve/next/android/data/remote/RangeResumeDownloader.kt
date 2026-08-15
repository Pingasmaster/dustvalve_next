package com.dustvalve.next.android.data.remote

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.OutputStream
import kotlin.coroutines.coroutineContext

/**
 * Streams an HTTP resource into an [OutputStream] using a `Range: bytes=0-`
 * header on the first request and `Range: bytes=<bytesWritten>-` on retries.
 *
 * Resumes from the last-written offset on mid-stream [IOException] up to
 * [maxRetries] times with exponential backoff. Pure OkHttp + coroutines;
 * extracted from [DownloadRepositoryImpl] so the resume logic is reachable
 * by a MockWebServer-backed unit test.
 *
 * Rationale for Range on the first request: googlevideo CDN edges stall
 * (the socket stays open but no bytes flow for minutes) when the client
 * issues a plain `GET` without Range on a media URL. Playback works with the
 * same URLs because ExoPlayer always sends `Range: bytes=0-`.
 *
 * The server MAY answer with 200 (ignoring Range) on the first attempt; the
 * helper accepts that and streams from 0. Only a 206 Partial Content response
 * enables resume-on-retry - if the server ignored Range mid-flight, appending
 * would corrupt the file, so the helper surfaces the failure instead.
 */
object RangeResumeDownloader {

    /** Max number of resume attempts after the initial request. */
    const val DEFAULT_MAX_RETRIES = 3

    private const val INITIAL_BACKOFF_MS = 500L
    private const val BACKOFF_SHIFT_CAP = 4
    private const val MIN_BYTES_WITHOUT_CONTENT_LENGTH = 1024L
    private const val SNIFF_BYTES = 64

    /** 64 KiB matches typical OkHttp/okio segment size; 8 KiB syscall-starves long YouTube transfers. */
    private const val COPY_BUFFER_BYTES = 64 * 1024

    /**
     * The server's 206 response no longer lines up with the partial content
     * already written: the Content-Range start offset differs from the bytes
     * we hold, or the advertised total differs from the one recorded when the
     * partial was created. Appending would splice two different payloads into
     * one corrupt file, so this is surfaced **non-retryably** - callers should
     * discard the partial and restart from zero.
     */
    class ResumeMismatchException(message: String) : IOException(message)

    /**
     * Result of a successful [stream]: byte count plus the file extension
     * inferred from Content-Type / magic bytes so callers can rename the
     * committed file when the stream format's default extension was wrong.
     */
    data class StreamResult(val bytesWritten: Long, val suggestedExtension: String?)

    /**
     * @param client OkHttpClient to use. Caller provides one with appropriate
     *   timeouts / protocol config; this helper doesn't tune the client.
     * @param url Resource URL.
     * @param sink Destination. Must be positioned at [startOffset]; the helper
     *   appends bytes as they arrive and never seeks. When resuming
     *   ([startOffset] > 0) the caller must have opened the sink in append mode
     *   over the existing partial bytes.
     * @param trackId Tag for error messages only.
     * @param startOffset Byte count already present in [sink] from a prior
     *   (paused) transfer. The first request asks for `Range: bytes=<offset>-`;
     *   a server that answers 200 (ignoring Range) is surfaced as a hard
     *   failure since appending to a from-scratch body would corrupt the file.
     * @param maxRetries Resume attempts after the initial request fails mid-flight.
     * @param expectedTotalBytes Total resource size recorded by a previous
     *   (paused) attempt, e.g. from a resume sidecar. When set, the first
     *   response's total (Content-Range total, or Content-Length +
     *   [startOffset]) must match or the helper throws
     *   [ResumeMismatchException] - the URL now serves a different payload
     *   than the one the partial came from.
     * @param backoffMillis Function returning the delay between attempt N and N+1.
     * @param onProgress Optional callback invoked after every successful write,
     *   carrying (bytesWritten, expectedTotal). `bytesWritten` includes
     *   [startOffset]. `expectedTotal` is null until the first response headers
     *   are parsed.
     * @return [StreamResult] with total bytes written (including [startOffset])
     *   and a suggested file extension from MIME / magic sniffing.
     */
    @Suppress("LongParameterList")
    suspend fun stream(
        client: OkHttpClient,
        url: String,
        sink: OutputStream,
        trackId: String,
        startOffset: Long = 0L,
        maxRetries: Int = DEFAULT_MAX_RETRIES,
        expectedTotalBytes: Long? = null,
        backoffMillis: (attempt: Int) -> Long = { attempt ->
            INITIAL_BACKOFF_MS * (1L shl (attempt - 1).coerceAtMost(BACKOFF_SHIFT_CAP))
        },
        onProgress: ((bytesWritten: Long, expectedTotal: Long?) -> Unit)? = null,
    ): StreamResult {
        var bytesWritten = startOffset
        var expectedTotal: Long? = null
        var serverHonorsRange = false
        var attempt = 0
        var suggestedExtension: String? = null
        // MIME / magic are only authoritative on a from-zero body. Resumes
        // append mid-file so the first chunk is not a container header.
        val validatePayload = startOffset == 0L

        while (true) {
            val call = client.newCall(rangeRequest(url, bytesWritten))
            try {
                // A completion-time invokeOnCompletion cannot abort a stalled
                // transfer: the handler only fires once the job reaches its
                // final state, which can't happen while this thread is blocked
                // inside execute()/read(). A watcher coroutine resumes on
                // another dispatcher thread the moment the caller is cancelled
                // (pause/cancel) and aborts the call immediately instead of
                // waiting out the 90s read timeout. Cancelling a call that
                // already completed or failed is a no-op, so the watcher's
                // finally is safe on every exit path; scoping it per attempt
                // keeps handlers from accumulating across retries.
                coroutineScope {
                    val canceller = launch {
                        try {
                            awaitCancellation()
                        } finally {
                            call.cancel()
                        }
                    }
                    try {
                        call.execute().use { response ->
                            val gotPartial = response.code == 206
                            if (gotPartial) serverHonorsRange = true
                            checkResponseStatus(response, trackId)
                            checkResumeAlignment(response, bytesWritten, gotPartial, trackId)
                            val contentType = response.header("Content-Type")
                            if (validatePayload && bytesWritten == 0L) {
                                DownloadPayloadValidator.assertAcceptableContentType(contentType, trackId)
                                suggestedExtension = DownloadPayloadValidator.extensionForMime(contentType)
                            }
                            if (expectedTotal == null) {
                                expectedTotal = resolveExpectedTotal(response, bytesWritten, expectedTotalBytes, trackId)
                            }
                            // bytesWritten is updated per chunk (not just on return) so a
                            // mid-stream failure resumes from the real offset on retry.
                            copyResponseBody(
                                response = response,
                                sink = sink,
                                startBytes = bytesWritten,
                                sniffPayload = validatePayload && bytesWritten == 0L,
                                trackId = trackId,
                                onSniffedExtension = { sniffed ->
                                    if (suggestedExtension == null) suggestedExtension = sniffed
                                },
                            ) { written ->
                                bytesWritten = written
                                onProgress?.invoke(written, expectedTotal)
                            }
                        }
                    } finally {
                        canceller.cancel()
                    }
                }
                requireStreamComplete(bytesWritten, expectedTotal, trackId)
                break
            } catch (e: IOException) {
                // A pause/cancel surfaces here as a plain IOException from the
                // aborted socket. Rethrow the job's CancellationException (the
                // PausedDownloadException carrier) instead of misclassifying
                // the pause as a failed download - callers keep the partial on
                // pause and only delete it on real failures.
                coroutineContext.ensureActive()
                // Non-retryable restart-from-zero signal: retrying would hit
                // the same mismatched payload again. Propagate unwrapped so
                // callers can discard the partial and start over.
                if (isNonRetryableDownloadFailure(e)) throw e
                attempt++
                val canRetry = attempt <= maxRetries &&
                    (bytesWritten == 0L || serverHonorsRange)
                if (!canRetry) {
                    throw IOException(
                        "Download failed after $attempt attempt(s) at " +
                            "$bytesWritten/${expectedTotal ?: -1} bytes for track $trackId: ${e.message}",
                        e,
                    )
                }
                delay(backoffMillis(attempt))
            }
        }

        requireNonEmpty(bytesWritten, trackId)
        requireExpectedSize(bytesWritten, expectedTotal, trackId)
        return StreamResult(bytesWritten, suggestedExtension)
    }

    private fun isNonRetryableDownloadFailure(e: IOException): Boolean =
        e is ResumeMismatchException || e is DownloadPayloadValidator.InvalidPayloadException

    private fun rangeRequest(url: String, bytesWritten: Long): Request = Request.Builder()
        .url(url)
        .header("Range", "bytes=$bytesWritten-")
        .build()

    /** A non-2xx, non-206 status can never carry a usable body. */
    private fun checkResponseStatus(response: Response, trackId: String) {
        if (!response.isSuccessful && response.code != 206) {
            throw IOException("HTTP ${response.code} for track: $trackId")
        }
    }

    /**
     * Guards resume safety: a mid-flight 200 (Range ignored) would restart the
     * body at byte 0, and a 206 whose Content-Range start does not equal the
     * bytes already held would splice two payloads. Both corrupt the file.
     */
    private fun checkResumeAlignment(response: Response, bytesWritten: Long, gotPartial: Boolean, trackId: String) {
        if (bytesWritten > 0L && !gotPartial) {
            throw IOException(
                "CDN ignored Range on resume (HTTP 200); " +
                    "cannot append $bytesWritten of partial content",
            )
        }
        if (gotPartial && bytesWritten > 0L) {
            val start = response.header("Content-Range")
                ?.substringAfter("bytes ", "")
                ?.substringBefore('-')
                ?.trim()
                ?.toLongOrNull()
            if (start != bytesWritten) {
                throw ResumeMismatchException(
                    "Resume offset mismatch for track $trackId: " +
                        "requested $bytesWritten, Content-Range starts at ${start ?: "unknown"}",
                )
            }
        }
    }

    /**
     * Resolves the expected total size from the first response's headers,
     * throwing [ResumeMismatchException] when it contradicts a previously
     * recorded total. On a 206 Content-Length is the *remaining* length, so the
     * Content-Range total is preferred; a resume without Content-Range cannot
     * trust Content-Length as the total and returns null.
     */
    private fun resolveExpectedTotal(response: Response, bytesWritten: Long, expectedTotalBytes: Long?, trackId: String): Long? {
        val cr = response.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull()
        val cl = response.header("Content-Length")?.toLongOrNull()
        val knownTotal = expectedTotalBytes
        if (knownTotal != null && knownTotal > 0L) {
            val responseTotal = cr ?: cl?.let { it + bytesWritten }
            if (responseTotal != null && responseTotal != knownTotal) {
                throw ResumeMismatchException(
                    "Resume total mismatch for track $trackId: " +
                        "partial was cut from $knownTotal bytes, server now reports $responseTotal",
                )
            }
        }
        return cr ?: if (bytesWritten == 0L) cl else null
    }

    /**
     * Streams the response body into [sink], reporting the running total via
     * [onWritten] after every write. When [sniffPayload] is true the first
     * chunk is checked for HTML/JSON/M3U / known audio magic before any
     * subsequent writes continue.
     */
    private suspend fun copyResponseBody(
        response: Response,
        sink: OutputStream,
        startBytes: Long,
        sniffPayload: Boolean,
        trackId: String,
        onSniffedExtension: (String?) -> Unit,
        onWritten: (Long) -> Unit,
    ) {
        var written = startBytes
        var sniffed = !sniffPayload
        response.body.byteStream().use { input ->
            val buffer = ByteArray(COPY_BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                coroutineContext.ensureActive()
                if (!sniffed) {
                    val header = if (read >= SNIFF_BYTES) {
                        buffer.copyOf(SNIFF_BYTES)
                    } else {
                        buffer.copyOf(read)
                    }
                    onSniffedExtension(
                        DownloadPayloadValidator.sniffExtensionOrReject(header, trackId),
                    )
                    sniffed = true
                }
                sink.write(buffer, 0, read)
                written += read
                onWritten(written)
            }
        }
    }

    /** The CDN closed the stream before the whole body arrived. */
    private fun requireStreamComplete(bytesWritten: Long, expectedTotal: Long?, trackId: String) {
        if (expectedTotal != null && expectedTotal > 0L && bytesWritten < expectedTotal) {
            throw IOException("Stream ended at $bytesWritten / $expectedTotal bytes for track: $trackId")
        }
    }

    private fun requireNonEmpty(bytesWritten: Long, trackId: String) {
        if (bytesWritten == 0L) throw IOException("Empty download for track: $trackId")
    }

    /** Final size sanity: exact-match a known total, else reject an implausibly small totalless body. */
    private fun requireExpectedSize(bytesWritten: Long, expectedTotal: Long?, trackId: String) {
        if (expectedTotal != null && expectedTotal > 0L && bytesWritten != expectedTotal) {
            throw IOException("Size mismatch: expected $expectedTotal but wrote $bytesWritten for track: $trackId")
        }
        if (expectedTotal == null && bytesWritten < MIN_BYTES_WITHOUT_CONTENT_LENGTH) {
            throw IOException(
                "Download suspiciously small ($bytesWritten bytes) without " +
                    "Content-Length header for track: $trackId",
            )
        }
    }
}
