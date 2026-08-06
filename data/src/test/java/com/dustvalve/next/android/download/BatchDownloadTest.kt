package com.dustvalve.next.android.download

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * The contract every batch downloader now shares: a failing item is skipped on
 * the spot, re-queued at the very end of the batch for one more attempt, and
 * given up on if that second attempt fails too.
 *
 * The motivating case is an age-restricted YouTube track, whose parser throws
 * [IllegalStateException] ("no audio adaptiveFormats") - that used to escape
 * every batch loop's `catch (e: IOException)` and abort the whole artist.
 */
class BatchDownloadTest {

    @Test
    fun `all succeed - one attempt each, nothing deferred`() = runTest {
        val attempts = mutableListOf<String>()

        val result = downloadEachDeferringFailures(listOf("a", "b", "c")) { attempts += it }

        assertThat(attempts).containsExactly("a", "b", "c").inOrder()
        assertThat(result.attempted).isEqualTo(3)
        assertThat(result.downloaded).isEqualTo(3)
        assertThat(result.unavailable).isEmpty()
        assertThat(result.hasUnavailable).isFalse()
        assertThat(result.allFailed).isFalse()
        assertThat(result.error).isNull()
    }

    @Test
    fun `a failing item is skipped immediately and retried at the very end`() = runTest {
        val attempts = mutableListOf<String>()

        downloadEachDeferringFailures(listOf("a", "bad", "c")) { item ->
            attempts += item
            if (item == "bad" && attempts.count { it == "bad" } == 1) throw IOException("boom")
        }

        // "c" is attempted BEFORE "bad" comes back around: the failure did not
        // stall the tracks queued behind it.
        assertThat(attempts).containsExactly("a", "bad", "c", "bad").inOrder()
    }

    @Test
    fun `an item that succeeds on the deferred retry counts as downloaded and reports no error`() = runTest {
        var badAttempts = 0

        val result = downloadEachDeferringFailures(listOf("a", "bad", "c")) { item ->
            if (item == "bad" && ++badAttempts == 1) throw IOException("transient")
        }

        assertThat(result.downloaded).isEqualTo(3)
        assertThat(result.unavailable).isEmpty()
        // The first-pass error described a track the user ultimately got.
        assertThat(result.error).isNull()
    }

    @Test
    fun `an item that fails twice is given up on and reported as unavailable`() = runTest {
        var badAttempts = 0

        val result = downloadEachDeferringFailures(listOf("a", "bad", "c")) { item ->
            if (item == "bad") {
                badAttempts++
                throw IOException("gone")
            }
        }

        assertThat(badAttempts).isEqualTo(2)
        assertThat(result.attempted).isEqualTo(3)
        assertThat(result.downloaded).isEqualTo(2)
        assertThat(result.unavailable).containsExactly("bad")
        assertThat(result.hasUnavailable).isTrue()
        assertThat(result.allFailed).isFalse()
        assertThat(result.error).hasMessageThat().isEqualTo("gone")
    }

    @Test
    fun `an age-restricted YouTube track does not abort the batch`() = runTest {
        val downloaded = mutableListOf<String>()

        val result = downloadEachDeferringFailures(listOf("t1", "ageRestricted", "t2", "t3")) { item ->
            if (item == "ageRestricted") {
                // Verbatim shape of what YouTubeInnertubeClient.player() throws.
                throw IllegalStateException(
                    "YouTube /player failed for videoId=xyz: no audio adaptiveFormats (playabilityStatus=\"LOGIN_REQUIRED\")",
                )
            }
            downloaded += item
        }

        assertThat(downloaded).containsExactly("t1", "t2", "t3").inOrder()
        assertThat(result.downloaded).isEqualTo(3)
        assertThat(result.unavailable).containsExactly("ageRestricted")
    }

    @Test
    fun `IllegalArgumentException is absorbed like any other download failure`() = runTest {
        val result = downloadEachDeferringFailures(listOf("a", "bad")) { item ->
            if (item == "bad") throw IllegalArgumentException("malformed response")
        }

        assertThat(result.downloaded).isEqualTo(1)
        assertThat(result.unavailable).containsExactly("bad")
    }

    @Test
    fun `every item failing twice reports allFailed`() = runTest {
        val result = downloadEachDeferringFailures(listOf("a", "b")) { throw IOException("offline") }

        assertThat(result.downloaded).isEqualTo(0)
        assertThat(result.unavailable).containsExactly("a", "b").inOrder()
        assertThat(result.allFailed).isTrue()
        assertThat(result.error).hasMessageThat().isEqualTo("offline")
    }

    @Test
    fun `several failures are retried at the end in their original order`() = runTest {
        val attempts = mutableListOf<String>()

        downloadEachDeferringFailures(listOf("a", "x", "b", "y", "c")) { item ->
            attempts += item
            if (item == "x" || item == "y") throw IOException("boom")
        }

        assertThat(attempts).containsExactly("a", "x", "b", "y", "c", "x", "y").inOrder()
    }

    @Test
    fun `cancellation propagates and stops the batch`() = runTest {
        val attempts = mutableListOf<String>()

        val thrown = runCatching {
            downloadEachDeferringFailures(listOf("a", "b", "c")) { item ->
                attempts += item
                if (item == "b") throw CancellationException("cancelled")
            }
        }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(CancellationException::class.java)
        assertThat(attempts).containsExactly("a", "b").inOrder()
    }

    @Test
    fun `a pause cancellation is not absorbed`() = runTest {
        val attempts = mutableListOf<String>()

        val thrown = runCatching {
            downloadEachDeferringFailures(listOf("a", "b", "c")) { item ->
                attempts += item
                if (item == "b") throw PausedDownloadException()
            }
        }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(PausedDownloadException::class.java)
        // "c" was never touched - pause stops the batch where it stands so
        // DownloadController can re-run the item on resume.
        assertThat(attempts).containsExactly("a", "b").inOrder()
    }

    @Test
    fun `an empty batch is a no-op`() = runTest {
        var called = false

        val result = downloadEachDeferringFailures(emptyList<String>()) { called = true }

        assertThat(called).isFalse()
        assertThat(result.attempted).isEqualTo(0)
        assertThat(result.downloaded).isEqualTo(0)
        assertThat(result.hasUnavailable).isFalse()
        // No items means nothing was lost, so this is not a failure.
        assertThat(result.allFailed).isFalse()
    }

    @Test
    fun `onAttemptFailed fires for both passes`() = runTest {
        val seen = mutableListOf<String>()

        downloadEachDeferringFailures(
            items = listOf("a", "bad"),
            onAttemptFailed = { item, e -> seen += "$item:${e.message}" },
        ) { item ->
            if (item == "bad") throw IOException("gone")
        }

        assertThat(seen).containsExactly("bad:gone", "bad:gone")
    }
}
