package com.dustvalve.next.android.data.remote

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Regression tests for the mid-stream "Connection reset" / "download hangs"
 * behaviour that users were seeing on YouTube tracks. The helper must:
 *
 * 1. Always send `Range: bytes=0-` on the first request.
 * 2. Stream the body when the server ignores Range (HTTP 200).
 * 3. Resume from the last-written offset when the server closes mid-stream
 *    and the next request must use `Range: bytes=<N>-`.
 * 4. Surface a clear error rather than corrupt the file when the server
 *    ignores Range on a resume attempt.
 */
class RangeResumeDownloaderTest {

    private lateinit var server: MockWebServer
    // No connection pooling — each request gets a fresh socket. MockWebServer
    // gets confused when a single connection is reused across enqueued
    // responses in our retry-based test shapes.
    private val client = OkHttpClient.Builder()
        .connectionPool(okhttp3.ConnectionPool(0, 1L, java.util.concurrent.TimeUnit.MILLISECONDS))
        .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
        .build()

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `first request always includes Range bytes=0`() = runBlocking {
        val body = "x".repeat(4096).toByteArray()
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes 0-4095/4096")
                .setBody(Buffer().write(body)),
        )
        val out = ByteArrayOutputStream()
        RangeResumeDownloader.stream(client, server.url("/x").toString(), out, "t1")

        val request = server.takeRequest()
        assertThat(request.getHeader("Range")).isEqualTo("bytes=0-")
        assertThat(out.toByteArray()).isEqualTo(body)
    }

    @Test
    fun `accepts HTTP 200 (server ignores Range) and streams full body from zero`() = runBlocking {
        val body = "flac data".toByteArray() + ByteArray(2048) { it.toByte() }
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Length", body.size.toString())
                .setBody(Buffer().write(body)),
        )
        val out = ByteArrayOutputStream()
        val written = RangeResumeDownloader.stream(
            client = client,
            url = server.url("/x").toString(),
            sink = out,
            trackId = "no_range_support",
        )
        assertThat(written.toInt()).isEqualTo(body.size)
        assertThat(out.toByteArray()).isEqualTo(body)
    }

    @Test
    fun `resumes from bytes written when CDN truncates body mid-stream`() = runBlocking {
        // CDN lies: advertises 8000 bytes but closes after sending 3000. This is
        // the shape we see in practice — the socket stays open and data flows,
        // then the CDN closes. Our helper must detect bytesWritten < expected,
        // reconnect with Range: bytes=3000-, and append the remainder.
        val fullBody = ByteArray(8000) { (it and 0xFF).toByte() }
        val firstSlice = fullBody.copyOfRange(0, 3000)
        val secondSlice = fullBody.copyOfRange(3000, fullBody.size)

        // Content-Length matches the actual (truncated) body size so OkHttp
        // doesn't hang waiting for the rest; Content-Range signals the true
        // total, which is how the resume helper detects "body ended early".
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes 0-${fullBody.size - 1}/${fullBody.size}")
                .setBody(Buffer().write(firstSlice)),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader(
                    "Content-Range",
                    "bytes 3000-${fullBody.size - 1}/${fullBody.size}",
                )
                .setBody(Buffer().write(secondSlice)),
        )

        val out = ByteArrayOutputStream()
        val written = RangeResumeDownloader.stream(
            client = client,
            url = server.url("/x").toString(),
            sink = out,
            trackId = "truncated_then_resume",
            backoffMillis = { 0L },
        )

        assertThat(written.toInt()).isEqualTo(fullBody.size)
        assertThat(out.toByteArray()).isEqualTo(fullBody)
        server.takeRequest() // first
        val second = server.takeRequest()
        assertThat(second.getHeader("Range")).isEqualTo("bytes=3000-")
    }

    @Test
    fun `fails cleanly when CDN ignores Range on resume (does NOT corrupt sink)`() = runBlocking {
        // Dispatcher gives us per-request control: first Range=bytes=0- gets
        // the truncated 206; any subsequent retry (Range=bytes=2000-) gets a
        // 200 with the full body, simulating a CDN that ignores Range.
        val partial = ByteArray(2000) { (it and 0xFF).toByte() }
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val range = request.getHeader("Range") ?: ""
                return if (range == "bytes=0-") {
                    MockResponse()
                        .setResponseCode(206)
                        .setHeader("Content-Range", "bytes 0-4999/5000")
                        .setBody(Buffer().write(partial))
                } else {
                    MockResponse()
                        .setResponseCode(200)
                        .setBody(Buffer().write(ByteArray(5000)))
                }
            }
        }

        val out = ByteArrayOutputStream()
        val ex = runCatching {
            RangeResumeDownloader.stream(
                client = client,
                url = server.url("/x").toString(),
                sink = out,
                trackId = "range_ignored_on_retry",
                backoffMillis = { 0L },
            )
        }.exceptionOrNull()

        assertThat(ex).isInstanceOf(IOException::class.java)
        assertThat(ex!!.message).contains("ignored Range on resume")
        // Sink is left with the 2000 partial bytes written before the reset —
        // verifying we did NOT silently append the full 5000-byte body on top,
        // which would have produced a 7000-byte corrupt file.
        assertThat(out.toByteArray().size).isEqualTo(partial.size)
    }

    @Test
    fun `gives up after max retries exhausted`() = runBlocking {
        val tiny = ByteArray(1) { 0x42.toByte() }
        // Five consecutive responses that advertise 10000 bytes but only send 1.
        repeat(5) {
            server.enqueue(
                MockResponse()
                    .setResponseCode(206)
                    .setHeader("Content-Range", "bytes 0-9999/10000")
                    .setBody(Buffer().write(tiny)),
            )
        }

        val out = ByteArrayOutputStream()
        val ex = runCatching {
            RangeResumeDownloader.stream(
                client = client,
                url = server.url("/x").toString(),
                sink = out,
                trackId = "always_resets",
                maxRetries = 2,
                backoffMillis = { 0L },
            )
        }.exceptionOrNull()

        assertThat(ex).isInstanceOf(IOException::class.java)
        assertThat(ex!!.message).contains("Download failed after 3 attempt(s)")
    }

    @Test
    fun `rejects non-2xx (and non-206) HTTP response`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403))
        val out = ByteArrayOutputStream()
        val ex = runCatching {
            RangeResumeDownloader.stream(
                client = client,
                url = server.url("/x").toString(),
                sink = out,
                trackId = "forbidden",
                maxRetries = 0,
            )
        }.exceptionOrNull()
        assertThat(ex).isInstanceOf(IOException::class.java)
        assertThat(ex!!.message).contains("HTTP 403")
    }
}
