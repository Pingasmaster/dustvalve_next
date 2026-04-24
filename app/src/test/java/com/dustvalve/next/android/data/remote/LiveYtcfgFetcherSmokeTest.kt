package com.dustvalve.next.android.data.remote

import com.dustvalve.next.android.data.remote.youtube.innertube.YouTubeVisitorDataFetcher
import com.dustvalve.next.android.data.remote.youtubemusic.YouTubeMusicVisitorDataFetcher
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * End-to-end smoke test against the REAL `music.youtube.com` and
 * `www.youtube.com` landing pages. Skipped unless `DUSTVALVE_LIVE_NET=1` is
 * set in the environment, so CI and regular dev builds stay hermetic.
 *
 * Run manually from a machine with outbound HTTPS:
 *
 *   DUSTVALVE_LIVE_NET=1 ./gradlew :app:testDebugUnitTest --tests \
 *     'com.dustvalve.next.android.data.remote.LiveYtcfgFetcherSmokeTest'
 *
 * Confirms that the hardening actually works against whatever variant
 * Google is serving today. If this passes and the on-device YT Music tab
 * still errors, the bug is downstream of the visitor fetch.
 */
class LiveYtcfgFetcherSmokeTest {

    private lateinit var okHttp: OkHttpClient

    @Before fun setUp() {
        assumeTrue(
            "Set DUSTVALVE_LIVE_NET=1 to run live-network smoke tests",
            System.getenv("DUSTVALVE_LIVE_NET") == "1",
        )
        // Match the production shared client: Chrome-like UA interceptor so
        // the fetcher's newBuilder()-inherited interceptor mirrors reality.
        okHttp = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header(
                            "User-Agent",
                            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
                        )
                        .build(),
                )
            })
            .build()
    }

    @Test fun `live YT Music landing yields visitorData`() = runTest {
        val fetcher = YouTubeMusicVisitorDataFetcher(okHttp)
        val cfg = fetcher.get()
        println("[LIVE] YT Music visitorData=${cfg.visitorData.take(30)}... version=${cfg.clientVersion}")
        assertThat(cfg.visitorData.length).isAtLeast(30)
        assertThat(cfg.clientVersion).startsWith("1.")
    }

    @Test fun `live YT landing yields visitorData`() = runTest {
        val fetcher = YouTubeVisitorDataFetcher(okHttp)
        val cfg = fetcher.get()
        println("[LIVE] YT visitorData=${cfg.visitorData.take(30)}... version=${cfg.clientVersion}")
        assertThat(cfg.visitorData.length).isAtLeast(30)
        assertThat(cfg.clientVersion).startsWith("2.")
    }
}
