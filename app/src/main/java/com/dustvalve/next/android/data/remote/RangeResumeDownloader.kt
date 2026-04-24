package com.dustvalve.next.android.data.remote

import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.OutputStream

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
 * enables resume-on-retry — if the server ignored Range mid-flight, appending
 * would corrupt the file, so the helper surfaces the failure instead.
 */
object RangeResumeDownloader {

    /** Max number of resume attempts after the initial request. */
    const val DEFAULT_MAX_RETRIES = 3

    /**
     * @param client OkHttpClient to use. Caller provides one with appropriate
     *   timeouts / protocol config; this helper doesn't tune the client.
     * @param url Resource URL.
     * @param sink Destination. Must be positioned at its start; the helper
     *   appends bytes as they arrive and never seeks.
     * @param trackId Tag for error messages only.
     * @return Total bytes written.
     */
    suspend fun stream(
        client: OkHttpClient,
        url: String,
        sink: OutputStream,
        trackId: String,
        maxRetries: Int = DEFAULT_MAX_RETRIES,
        backoffMillis: (attempt: Int) -> Long = { attempt ->
            500L * (1L shl (attempt - 1).coerceAtMost(4))
        },
    ): Long {
        var bytesWritten = 0L
        var expectedTotal: Long? = null
        var serverHonorsRange = false
        var attempt = 0

        while (true) {
            val rangeHeader = "bytes=$bytesWritten-"
            val request = Request.Builder()
                .url(url)
                .header("Range", rangeHeader)
                .build()
            val call = client.newCall(request)
            coroutineContext[kotlinx.coroutines.Job]?.invokeOnCompletion { cause ->
                if (cause != null) call.cancel()
            }
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful && response.code != 206) {
                        throw IOException("HTTP ${response.code} for track: $trackId")
                    }
                    val gotPartial = response.code == 206
                    if (gotPartial) serverHonorsRange = true
                    if (bytesWritten > 0L && !gotPartial) {
                        // Mid-flight retry, server returned 200 (ignored Range).
                        // Sink already holds bytesWritten of valid content; the
                        // response body would restart at byte 0, so appending
                        // would corrupt. Surface as hard failure.
                        throw IOException(
                            "CDN ignored Range on resume (HTTP 200); " +
                                "cannot append $bytesWritten of partial content",
                        )
                    }
                    if (bytesWritten == 0L) {
                        val cr = response.header("Content-Range")
                            ?.substringAfterLast('/')?.toLongOrNull()
                        val cl = response.header("Content-Length")?.toLongOrNull()
                        expectedTotal = cr ?: cl
                    }
                    response.body.byteStream().use { input ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            coroutineContext.ensureActive()
                            sink.write(buffer, 0, read)
                            bytesWritten += read
                        }
                    }
                }
                val expected = expectedTotal
                if (expected != null && expected > 0L && bytesWritten < expected) {
                    // CDN closed the stream before sending the whole body.
                    throw IOException(
                        "Stream ended at $bytesWritten / $expected bytes for track: $trackId",
                    )
                }
                break
            } catch (e: Exception) {
                if (e is CancellationException) throw e
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

        if (bytesWritten == 0L) throw IOException("Empty download for track: $trackId")
        val expected = expectedTotal
        if (expected != null && expected > 0L && bytesWritten != expected) {
            throw IOException(
                "Size mismatch: expected $expected but wrote $bytesWritten for track: $trackId",
            )
        }
        if (expectedTotal == null && bytesWritten < 1024) {
            throw IOException(
                "Download suspiciously small ($bytesWritten bytes) without " +
                    "Content-Length header for track: $trackId",
            )
        }
        return bytesWritten
    }
}
