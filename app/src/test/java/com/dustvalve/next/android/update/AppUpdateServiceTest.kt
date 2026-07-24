@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.dustvalve.next.android.update

import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest

/**
 * Regression coverage for the self-update check.
 *
 * Specifically locks in the pre-alpha requirement: every CI build ships as a
 * GitHub *pre-release*, so the check MUST NOT filter them out. The pre-fix
 * code did - and the user reported it "never detects newer versions".
 */
class AppUpdateServiceTest {

    private lateinit var server: MockWebServer
    private lateinit var cacheDir: File
    private val context = mockk<Context>(relaxed = true)
    private val client = OkHttpClient()
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        cacheDir = Files.createTempDirectory("update-test-cache").toFile()
        every { context.cacheDir } returns cacheDir
    }

    @After fun tearDown() {
        server.shutdown()
        cacheDir.deleteRecursively()
    }

    @Test fun `detects a newer PRE-RELEASE (regression - pre-alpha ships everything as prerelease)`() = runTest {
        server.enqueue(MockResponse().setBody(releasesJson(release(tag = "v0.3.50", prerelease = true))))
        val svc = testService(installed = "0.3.46")

        val update = svc.checkForUpdate()
        assertThat(update).isNotNull()
        assertThat(update!!.versionName).isEqualTo("0.3.50")
        assertThat(update.apkDownloadUrl).endsWith("dustvalve_next.apk")
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
        // GitHub returns releases newest-first by publish date. Our code relies on that - the version
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
                    release(tag = "v0.3.55", prerelease = true, assetName = "dustvalve_next.apk"),
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
            // Strictly greater - patch bump
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
            // Mismatched length - shorter implies .0
            assertThat(isNewer("0.4", "0.3.99")).isTrue()
            assertThat(isNewer("0.3", "0.3.0")).isFalse()
            assertThat(isNewer("1", "0.99.99")).isTrue()
            // A suffixed component compares by its leading digit run, so
            // "47-hotfix" orders as 47 - not as 0 (the pre-fix coercion that
            // made suffixed tags look OLDER and never be offered).
            assertThat(isNewer("0.3.47-hotfix", "0.3.46")).isTrue()
            assertThat(isNewer("0.3.46-hotfix", "0.3.46")).isFalse() // equal, not strictly newer
            assertThat(isNewer("0.3.45-rc1", "0.3.46")).isFalse()
            // The legacy build's "-legacy" versionName suffix parses on the local side too.
            assertThat(isNewer("0.5.2", "0.5.1-legacy")).isTrue()
            assertThat(isNewer("0.5.1", "0.5.1-legacy")).isFalse()
            // A remote component with NO leading digits is unparseable:
            // explicitly not-newer, never "coerced to 0 and compared anyway".
            assertThat(isNewer("0.3.hotfix", "0.3.46")).isFalse()
            assertThat(isNewer("nightly", "0.0.1")).isFalse()
            assertThat(isNewer("v.1.0", "0.0.1")).isFalse()
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

    @Test fun `picks dustvalve_next-apk when release also ships the modern dustvalve_next-future-apk`() = runTest {
        // Each release now ships TWO apks side by side:
        //   dustvalve_next.apk         -> this legacy build (Android 8-16)
        //   dustvalve_next-future.apk  -> the master/Android 17 build
        // Legacy users MUST NOT be auto-downgraded to the future apk, which
        // has minSdk 37 and cannot install on the devices this branch serves.
        val release = """
            {
              "tag_name": "v1.2.3",
              "name": "v1.2.3",
              "body": "notes",
              "prerelease": true,
              "draft": false,
              "assets": [
                {"name": "dustvalve_next-future.apk", "browser_download_url": "https://releases.example/v1.2.3/dustvalve_next-future.apk"},
                {"name": "dustvalve_next.apk", "browser_download_url": "https://releases.example/v1.2.3/dustvalve_next.apk"}
              ]
            }
        """.trimIndent()
        server.enqueue(MockResponse().setBody("[$release]"))
        val svc = testService(installed = "1.0.0")

        val update = svc.checkForUpdate()
        assertThat(update?.apkDownloadUrl).endsWith("dustvalve_next.apk")
    }

    @Test fun `skips release that only has the modern dustvalve_next-future-apk (legacy never installs Android 17 build)`() = runTest {
        val release = """
            {
              "tag_name": "v9.9.9",
              "name": "v9.9.9",
              "body": "notes",
              "prerelease": true,
              "draft": false,
              "assets": [
                {"name": "dustvalve_next-future.apk", "browser_download_url": "https://releases.example/v9.9.9/dustvalve_next-future.apk"}
              ]
            }
        """.trimIndent()
        server.enqueue(MockResponse().setBody("[$release]"))
        val svc = testService(installed = "1.0.0")

        assertThat(svc.checkForUpdate()).isNull()
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
                    release(tag = "v9.9.8", prerelease = true, assetName = "dustvalve_next.apk"),
                    release(tag = "v9.9.7", prerelease = true, assetName = "dustvalve_next.apk"),
                ),
            ),
        )
        val svc = testService(installed = "9.9.0")

        val update = svc.checkForUpdate()
        assertThat(update?.versionName).isEqualTo("9.9.8")
    }

    @Test fun `checkForUpdate surfaces the asset sha256 digest - and null when absent or not sha256`() = runTest {
        val hex = "ab".repeat(32)
        server.enqueue(
            MockResponse().setBody(releasesJson(release(tag = "v9.9.9", prerelease = true, digest = "sha256:$hex"))),
        )
        server.enqueue(
            MockResponse().setBody(releasesJson(release(tag = "v9.9.9", prerelease = true, digest = "md5:abcdef"))),
        )
        server.enqueue(
            MockResponse().setBody(releasesJson(release(tag = "v9.9.9", prerelease = true, digest = null))),
        )
        val svc = testService(installed = "0.1.0")

        assertThat(svc.checkForUpdate()?.apkSha256).isEqualTo(hex)
        assertThat(svc.checkForUpdate()?.apkSha256).isNull() // non-sha256 digest is ignored
        assertThat(svc.checkForUpdate()?.apkSha256).isNull() // no digest field at all
    }

    @Test fun `downloadApk verifies integrity - digest match, Content-Length fallback, mismatch aborts`() = runTest {
        val svc = testService(installed = "0.1.0", trustAllDownloadUrls = true)
        val url = server.url("/update.apk").toString()
        val updates = File(cacheDir, "updates")
        val body = "fake apk bytes for the digest check"
        val bodySha256 = MessageDigest.getInstance("SHA-256")
            .digest(body.toByteArray())
            .joinToString("") { "%02x".format(it) }

        // Matching digest: update.apk lands with the exact bytes.
        server.enqueue(MockResponse().setBody(body))
        svc.downloadApk(url, bodySha256).collect { }
        assertThat(File(updates, "update.apk").readText()).isEqualTo(body)

        // No digest from GitHub: byte-count-vs-Content-Length is the fallback gate.
        server.enqueue(MockResponse().setBody(body))
        svc.downloadApk(url, null).collect { }
        assertThat(File(updates, "update.apk").readText()).isEqualTo(body)

        // Digest mismatch (tampered bytes): abort, delete, nothing installable left.
        server.enqueue(MockResponse().setBody("tampered apk bytes"))
        val ex = runCatching { svc.downloadApk(url, "0".repeat(64)).collect { } }.exceptionOrNull()
        assertThat(ex).isInstanceOf(IOException::class.java)
        assertThat(ex?.message).contains("SHA-256 mismatch")
        assertThat(File(updates, "update.apk").exists()).isFalse()
        assertThat(File(updates, "update.apk.tmp").exists()).isFalse()
    }

    @Test fun `downloadApk refuses untrusted URLs without touching the network`() = runTest {
        // Static allowlist: https + GitHub-owned hosts only.
        with(AppUpdateService) {
            assertThat(isGitHubAssetUrl("https://github.com/Pingasmaster/dustvalve_next/releases/download/v1.0.0/a.apk")).isTrue()
            assertThat(isGitHubAssetUrl("https://api.github.com/repos/x/y/releases/assets/1")).isTrue()
            assertThat(isGitHubAssetUrl("https://objects.githubusercontent.com/some/asset")).isTrue()
            assertThat(isGitHubAssetUrl("https://release-assets.githubusercontent.com/some/asset")).isTrue()
            // http is never OK, not even on the right host.
            assertThat(isGitHubAssetUrl("http://github.com/x.apk")).isFalse()
            // Arbitrary hosts and lookalike suffixes/prefixes.
            assertThat(isGitHubAssetUrl("https://evil.example/update.apk")).isFalse()
            assertThat(isGitHubAssetUrl("https://github.com.evil.example/update.apk")).isFalse()
            assertThat(isGitHubAssetUrl("https://notgithub.com/update.apk")).isFalse()
            assertThat(isGitHubAssetUrl("https://githubusercontent.com/update.apk")).isFalse()
            assertThat(isGitHubAssetUrl("not a url")).isFalse()
        }

        // And the service-level gate: the flow throws before any request is made.
        val svc = testService(installed = "0.1.0") // production URL trust
        val httpEx = runCatching { svc.downloadApk("http://github.com/x/update.apk").collect { } }.exceptionOrNull()
        assertThat(httpEx).isInstanceOf(IOException::class.java)
        assertThat(httpEx?.message).contains("untrusted")
        val hostEx = runCatching { svc.downloadApk("https://evil.example/update.apk").collect { } }.exceptionOrNull()
        assertThat(hostEx).isInstanceOf(IOException::class.java)
        assertThat(hostEx?.message).contains("untrusted")
        assertThat(server.requestCount).isEqualTo(0)
    }

    // --- helpers ------------------------------------------------------------

    private fun testService(
        installed: String,
        trustAllDownloadUrls: Boolean = false,
    ): AppUpdateService = object : AppUpdateService(client, context, testDispatcher) {
        override val releasesUrl: String = server.url("/releases").toString()
        override val installedVersion: String = installed

        // MockWebServer serves from http://localhost, which the production
        // allowlist rightly refuses; download tests opt in to trust it.
        override fun isTrustedDownloadUrl(url: String): Boolean = trustAllDownloadUrls || super.isTrustedDownloadUrl(url)
    }

    private fun release(
        tag: String,
        prerelease: Boolean = false,
        draft: Boolean = false,
        assetName: String? = "dustvalve_next.apk",
        digest: String? = null,
    ): String {
        val assetsJson = if (assetName == null) {
            "[]"
        } else {
            val digestJson = if (digest == null) "" else """, "digest": "$digest""""
            """[{"name": "$assetName", "browser_download_url": "https://releases.example/$tag/$assetName"$digestJson}]"""
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
