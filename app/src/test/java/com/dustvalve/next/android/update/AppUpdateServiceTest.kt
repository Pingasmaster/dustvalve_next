package com.dustvalve.next.android.update

import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Regression coverage for the self-update check.
 *
 * Specifically locks in the pre-alpha requirement: every CI build ships as a
 * GitHub *pre-release*, so the check MUST NOT filter them out. The pre-fix
 * code did — and the user reported it "never detects newer versions".
 */
class AppUpdateServiceTest {

    private lateinit var server: MockWebServer
    private val context = mockk<Context>(relaxed = true)
    private val client = OkHttpClient()

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After fun tearDown() {
        server.shutdown()
    }

    @Test fun `detects a newer PRE-RELEASE (regression - pre-alpha ships everything as prerelease)`() = runTest {
        server.enqueue(MockResponse().setBody(releasesJson(release(tag = "v0.3.50", prerelease = true))))
        val svc = testService(installed = "0.3.46")

        val update = svc.checkForUpdate()
        assertThat(update).isNotNull()
        assertThat(update!!.versionName).isEqualTo("0.3.50")
        assertThat(update.apkDownloadUrl).endsWith("app-release.apk")
    }

    @Test fun `detects a newer stable release too`() = runTest {
        server.enqueue(MockResponse().setBody(releasesJson(release(tag = "v1.0.0", prerelease = false))))
        val svc = testService(installed = "0.9.9")

        assertThat(svc.checkForUpdate()?.versionName).isEqualTo("1.0.0")
    }

    @Test fun `returns null when the only release is older than installed`() = runTest {
        server.enqueue(MockResponse().setBody(releasesJson(release(tag = "v0.3.40", prerelease = true))))
        val svc = testService(installed = "0.3.46")

        assertThat(svc.checkForUpdate()).isNull()
    }

    @Test fun `returns null when latest matches installed exactly`() = runTest {
        server.enqueue(MockResponse().setBody(releasesJson(release(tag = "v0.3.46", prerelease = true))))
        val svc = testService(installed = "0.3.46")

        assertThat(svc.checkForUpdate()).isNull()
    }

    @Test fun `picks the first (newest) entry and ignores older ones even if out of version order`() = runTest {
        // GitHub returns releases newest-first by publish date. Our code relies on that — the version
        // comparison gate catches the edge case where the first entry is older than the installed
        // build (which happens when we've locally moved past the latest CI release).
        server.enqueue(
            MockResponse().setBody(
                releasesJson(
                    release(tag = "v0.3.50", prerelease = true),
                    release(tag = "v0.3.49", prerelease = true),
                    release(tag = "v0.3.48", prerelease = true),
                ),
            ),
        )
        val svc = testService(installed = "0.3.46")

        assertThat(svc.checkForUpdate()?.versionName).isEqualTo("0.3.50")
    }

    @Test fun `skips drafts`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                releasesJson(
                    release(tag = "v0.3.60", prerelease = true, draft = true),
                    release(tag = "v0.3.55", prerelease = true),
                ),
            ),
        )
        val svc = testService(installed = "0.3.46")

        // Draft at top is skipped; we fall through to the v0.3.55 prerelease.
        assertThat(svc.checkForUpdate()?.versionName).isEqualTo("0.3.55")
    }

    @Test fun `skips releases with no apk asset`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                releasesJson(
                    release(tag = "v0.3.60", prerelease = true, assetName = "source.zip"),
                    release(tag = "v0.3.55", prerelease = true, assetName = "app-release.apk"),
                ),
            ),
        )
        val svc = testService(installed = "0.3.46")

        assertThat(svc.checkForUpdate()?.versionName).isEqualTo("0.3.55")
    }

    @Test fun `returns null on empty releases list`() = runTest {
        server.enqueue(MockResponse().setBody("[]"))
        val svc = testService(installed = "0.3.46")

        assertThat(svc.checkForUpdate()).isNull()
    }

    @Test fun `throws on non-2xx response`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        val svc = testService(installed = "0.3.46")

        val ex = runCatching { svc.checkForUpdate() }.exceptionOrNull()
        assertThat(ex?.message).contains("HTTP 503")
    }

    @Test fun `strips leading v from tag name`() = runTest {
        server.enqueue(MockResponse().setBody(releasesJson(release(tag = "v2.0.0", prerelease = true))))
        val svc = testService(installed = "1.0.0")

        assertThat(svc.checkForUpdate()?.versionName).isEqualTo("2.0.0")
    }

    @Test fun `isNewer compares dotted-int versions correctly`() {
        with(AppUpdateService) {
            // Strictly greater — patch bump
            assertThat(isNewer("0.3.47", "0.3.46")).isTrue()
            // Minor bump, not just a patch increment (user raised this explicitly)
            assertThat(isNewer("0.4.0", "0.3.99")).isTrue()
            assertThat(isNewer("0.4.0", "0.3.46")).isTrue()
            // Major bump
            assertThat(isNewer("1.0.0", "0.99.99")).isTrue()
            assertThat(isNewer("1.0.0", "0.3.46")).isTrue()
            // Multi-digit components (don't lex-compare)
            assertThat(isNewer("0.10.0", "0.9.99")).isTrue()
            assertThat(isNewer("0.3.100", "0.3.99")).isTrue()
            // Equal
            assertThat(isNewer("0.3.46", "0.3.46")).isFalse()
            // Strictly less
            assertThat(isNewer("0.3.45", "0.3.46")).isFalse()
            assertThat(isNewer("0.2.99", "0.3.0")).isFalse()
            // Mismatched length — shorter implies .0
            assertThat(isNewer("0.4", "0.3.99")).isTrue()
            assertThat(isNewer("0.3", "0.3.0")).isFalse()
            assertThat(isNewer("1", "0.99.99")).isTrue()
        }
    }

    @Test fun `end-to-end minor bump (0-4-0) is detected from prerelease`() = runTest {
        server.enqueue(
            MockResponse().setBody(releasesJson(release(tag = "v0.4.0", prerelease = true))),
        )
        val svc = testService(installed = "0.3.46")

        assertThat(svc.checkForUpdate()?.versionName).isEqualTo("0.4.0")
    }

    @Test fun `end-to-end major bump (1-0-0) is detected from prerelease`() = runTest {
        server.enqueue(
            MockResponse().setBody(releasesJson(release(tag = "v1.0.0", prerelease = true))),
        )
        val svc = testService(installed = "0.99.99")

        assertThat(svc.checkForUpdate()?.versionName).isEqualTo("1.0.0")
    }

    @Test fun `falls through when the top release has no asset yet (CI still uploading)`() = runTest {
        // Shape: newest tag was just published and its APK hasn't finished
        // uploading (assets=0), while the next-newest is a prerelease that DOES
        // have an APK. The buggy pre-fix code filtered the top one out (no
        // APK) AND the next one (prerelease) and returned null; the fix falls
        // through to the assetful prerelease.
        server.enqueue(
            MockResponse().setBody(
                releasesJson(
                    release(tag = "v9.9.9", prerelease = false, assetName = null),
                    release(tag = "v9.9.8", prerelease = true, assetName = "app-release.apk"),
                    release(tag = "v9.9.7", prerelease = true, assetName = "app-release.apk"),
                ),
            ),
        )
        val svc = testService(installed = "9.9.0")

        val update = svc.checkForUpdate()
        assertThat(update?.versionName).isEqualTo("9.9.8")
    }

    // --- helpers ------------------------------------------------------------

    private fun testService(installed: String): AppUpdateService = object : AppUpdateService(client, context) {
        override val releasesUrl: String = server.url("/releases").toString()
        override val installedVersion: String = installed
    }

    private fun release(
        tag: String,
        prerelease: Boolean = false,
        draft: Boolean = false,
        assetName: String? = "app-release.apk",
    ): String {
        val assetsJson = if (assetName == null) {
            "[]"
        } else {
            """[{"name": "$assetName", "browser_download_url": "https://releases.example/$tag/$assetName"}]"""
        }
        return """
            {
              "tag_name": "$tag",
              "name": "$tag",
              "body": "notes",
              "prerelease": $prerelease,
              "draft": $draft,
              "assets": $assetsJson
            }
        """.trimIndent()
    }

    private fun releasesJson(vararg entries: String): String = entries.joinToString(prefix = "[", postfix = "]")
}
