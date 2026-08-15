@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.dustvalve.next.android.data.remote

import com.dustvalve.next.android.data.remote.youtube.innertube.YouTubeInnertubeClient
import com.dustvalve.next.android.data.remote.youtube.innertube.YouTubePlayerParser
import com.dustvalve.next.android.data.remote.youtube.innertube.YouTubeVisitorDataFetcher
import com.dustvalve.next.android.data.remote.youtubemusic.YouTubeMusicArtistParser
import com.dustvalve.next.android.data.remote.youtubemusic.YouTubeMusicInnertubeClient
import com.dustvalve.next.android.data.remote.youtubemusic.YouTubeMusicSearchParser
import com.dustvalve.next.android.data.remote.youtubemusic.YouTubeMusicVisitorDataFetcher
import com.dustvalve.next.android.domain.model.SearchResultType
import com.dustvalve.next.android.util.ThumbnailUrls
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Live checks that the production download / artwork request shapes are not
 * slower than a plain HTTP/1.1 Range GET, and that YTM artist avatars
 * (blackbear-style googleusercontent originals) finish at the canonical size.
 *
 * Gated on `DUSTVALVE_LIVE_NET=1` (picked up by `./build.sh --live-net`).
 */
class LiveDownloadThroughputAndArtTest {

    private lateinit var okHttp: OkHttpClient
    private lateinit var io: CoroutineDispatcher
    private lateinit var ytClient: YouTubeInnertubeClient
    private lateinit var ytmClient: YouTubeMusicInnertubeClient
    private lateinit var playerParser: YouTubePlayerParser
    private lateinit var searchParser: YouTubeMusicSearchParser
    private lateinit var artistParser: YouTubeMusicArtistParser

    @Before fun setUp() {
        assumeTrue(
            "Set DUSTVALVE_LIVE_NET=1 to run live-network smoke tests",
            System.getenv("DUSTVALVE_LIVE_NET") == "1",
        )
        io = UnconfinedTestDispatcher()
        okHttp = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .addInterceptor(
                Interceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .header(
                                "User-Agent",
                                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                                    "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
                            )
                            .build(),
                    )
                },
            )
            .build()
        ytClient = YouTubeInnertubeClient(okHttp, YouTubeVisitorDataFetcher(okHttp, io), io)
        ytmClient = YouTubeMusicInnertubeClient(
            okHttp,
            YouTubeMusicVisitorDataFetcher(okHttp, io),
            io,
        )
        playerParser = YouTubePlayerParser()
        searchParser = YouTubeMusicSearchParser()
        artistParser = YouTubeMusicArtistParser()
    }

    @Test fun `blackbear YTM artist avatar completes at canonical size`() = runTest {
        val search = ytmClient.search(
            query = "blackbear",
            params = YTM_ARTISTS_PARAMS,
        )
        val artist = searchParser.parse(search).firstOrNull {
            it.type == SearchResultType.YOUTUBE_ARTIST &&
                it.name.contains("blackbear", ignoreCase = true)
        }
        assumeTrue("YTM search did not return a blackbear artist", artist != null)
        val channelId = artist!!.url.substringAfterLast('/')
        val page = artistParser.parse(ytmClient.browse(channelId), channelId)
        val avatar = page.avatarUrl
        assumeTrue("blackbear artist page has no avatarUrl", !avatar.isNullOrBlank())
        println("[LIVE] blackbear channel=$channelId avatar=$avatar")

        val canonical = ThumbnailUrls.canonicalize(avatar!!)
        assertThat(canonical).isEqualTo(avatar)
        assertThat(canonical.contains("=s0") || canonical.contains("=w0-h0")).isFalse()

        val imageClient = OkHttpClient.Builder()
            .callTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .build()
        val started = System.nanoTime()
        val body = imageClient.newCall(
            Request.Builder().url(canonical).header("Accept", "image/*").build(),
        ).execute().use { response ->
            assertThat(response.isSuccessful).isTrue()
            response.body.bytes()
        }
        val elapsedMs = (System.nanoTime() - started) / 1_000_000L
        println("[LIVE] blackbear canonical fetch ${body.size} B in ${elapsedMs}ms")
        assertThat(body.size).isAtLeast(MIN_IMAGE_BYTES)
        assertThat(isImageMagic(body)).isTrue()
        assertThat(elapsedMs).isLessThan(IMAGE_DEADLINE_MS)
    }

    @Test fun `YouTube Range download matches plain HTTP1 throughput on first megabytes`() = runTest {
        val videoId = firstPlayableVideoId()
        assumeTrue("No playable YouTube audio stream", videoId != null)
        val info = playerParser.parsePlayerStreamInfo(ytClient.player(videoId!!))
        println(
            "[LIVE] yt download video=$videoId format=${info.format} " +
                "bitrate=${info.bitrate} mime=${info.mimeType}",
        )

        val appClient = downloadShapedClient()
        val baselineClient = OkHttpClient.Builder()
            .protocols(listOf(Protocol.HTTP_1_1))
            .readTimeout(90, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .build()

        val app = timedPrefix(appClient, info.streamUrl, PREFIX_BYTES, androidVr = true)
        val baseline = timedPrefix(baselineClient, info.streamUrl, PREFIX_BYTES, androidVr = true)
        println(
            "[LIVE] yt prefix app=${app.bytes}@${"%.2f".format(app.mbps)}Mbps " +
                "baseline=${baseline.bytes}@${"%.2f".format(baseline.mbps)}Mbps",
        )
        assertThat(app.bytes).isAtLeast(MIN_TRANSFER_BYTES)
        assertThat(baseline.bytes).isAtLeast(MIN_TRANSFER_BYTES)
        assertThroughputNotStalled(app, baseline)
    }

    @Test fun `Bandcamp mp3 Range download matches plain HTTP1 throughput`() = runTest {
        val album = DustvalveAlbumScraper(okHttp, io)
            .scrapeAlbum("https://c418.bandcamp.com/album/minecraft-volume-alpha")
        val url = album.tracks.firstOrNull { !it.streamUrl.isNullOrBlank() }?.streamUrl
        assumeTrue("Bandcamp album had no streamUrl", !url.isNullOrBlank())
        println("[LIVE] bandcamp stream host=${url!!.substringAfter("://").substringBefore('/')}")

        val app = timedPrefix(downloadShapedClient(), url, PREFIX_BYTES / 2, androidVr = false)
        val baseline = timedPrefix(
            OkHttpClient.Builder()
                .protocols(listOf(Protocol.HTTP_1_1))
                .readTimeout(90, TimeUnit.SECONDS)
                .build(),
            url,
            PREFIX_BYTES / 2,
            androidVr = false,
        )
        println(
            "[LIVE] bc prefix app=${app.bytes}@${"%.2f".format(app.mbps)}Mbps " +
                "baseline=${baseline.bytes}@${"%.2f".format(baseline.mbps)}Mbps",
        )
        assertThat(app.bytes).isAtLeast(MIN_TRANSFER_BYTES)
        assertThroughputNotStalled(app, baseline)
    }

    private suspend fun firstPlayableVideoId(): String? {
        val ids = listOf("jfKfPfyJRdk", "jNQXAC9IVRw")
        for (id in ids) {
            val parsed = runCatching { playerParser.parsePlayerStreamInfo(ytClient.player(id)) }
            if (parsed.isSuccess) return id
        }
        return null
    }

    private fun downloadShapedClient(): OkHttpClient = OkHttpClient.Builder()
        .protocols(listOf(Protocol.HTTP_1_1))
        .cookieJar(okhttp3.CookieJar.NO_COOKIES)
        .cache(null)
        .readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .build()

    private data class TimedCopy(val bytes: Int, val mbps: Double)

    private fun timedPrefix(
        client: OkHttpClient,
        url: String,
        maxBytes: Int,
        androidVr: Boolean,
    ): TimedCopy {
        val builder = Request.Builder()
            .url(url)
            .header("Range", "bytes=0-")
            .header("Accept-Encoding", "identity")
        if (androidVr) {
            builder.header(
                "User-Agent",
                "com.google.android.apps.youtube.vr.oculus/1.61.48 " +
                    "(Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
            )
        }
        val started = System.nanoTime()
        val bytes = client.newCall(builder.build()).execute().use { response ->
            assertThat(response.code == 200 || response.code == 206).isTrue()
            val buf = ByteArray(COPY_BUFFER_BYTES)
            var total = 0
            response.body.byteStream().use { input ->
                while (total < maxBytes) {
                    val n = input.read(buf, 0, minOf(buf.size, maxBytes - total))
                    if (n <= 0) break
                    total += n
                }
            }
            total
        }
        val seconds = (System.nanoTime() - started) / 1_000_000_000.0
        val mbps = if (seconds > 0.0) (bytes * 8.0 / 1_000_000.0) / seconds else 0.0
        return TimedCopy(bytes, mbps)
    }

    /**
     * Sub-megabyte bodies are RTT-dominated (a 300 KB googlevideo file can
     * swing 40 vs 70 Mbps run-to-run). Require a real pipe (>= 1 Mbps) always;
     * only apply the ratio floor when both sides copied enough bytes for
     * throughput to mean something.
     */
    private fun assertThroughputNotStalled(app: TimedCopy, baseline: TimedCopy) {
        assertThat(app.mbps).isAtLeast(MIN_MBPS)
        if (app.bytes >= THROUGHPUT_COMPARE_BYTES && baseline.bytes >= THROUGHPUT_COMPARE_BYTES) {
            assertThat(app.mbps).isAtLeast(baseline.mbps * THROUGHPUT_FLOOR)
        }
    }

    private fun isImageMagic(body: ByteArray): Boolean {
        if (body.size < 12) return false
        val jpeg = body[0] == 0xFF.toByte() && body[1] == 0xD8.toByte()
        val png = body[0] == 0x89.toByte() && body[1] == 0x50.toByte()
        val gif = body[0] == 0x47.toByte() && body[1] == 0x49.toByte()
        val webp = body[0] == 0x52.toByte() && body[8] == 0x57.toByte()
        val riff = body[0] == 0x52.toByte() && body[1] == 0x49.toByte()
        return jpeg || png || gif || webp || riff
    }

    private companion object {
        const val PREFIX_BYTES = 4 * 1024 * 1024
        const val COPY_BUFFER_BYTES = 64 * 1024
        const val MIN_IMAGE_BYTES = 2_000
        const val MIN_TRANSFER_BYTES = 64 * 1024
        const val IMAGE_DEADLINE_MS = 15_000L
        const val MIN_MBPS = 1.0
        const val THROUGHPUT_COMPARE_BYTES = 1_000_000

        /** Allow 40% jitter vs the uncapped baseline (CDN variance). */
        const val THROUGHPUT_FLOOR = 0.60
        const val YTM_ARTISTS_PARAMS = "EgWKAQIgAWoMEA4QChADEAQQCRAF"
    }
}
