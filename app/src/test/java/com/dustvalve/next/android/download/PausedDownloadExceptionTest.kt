package com.dustvalve.next.android.download

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Guards the cause-chain detection that decides whether a cancelled transfer
 * keeps its partial `.tmp` (pause) or deletes it (real cancel/failure).
 * Cancelling a parent coroutine wraps the original [PausedDownloadException]
 * in another exception for child coroutines, so a direct `is` check is not
 * enough - [isPauseCancellation] must walk the cause chain.
 */
class PausedDownloadExceptionTest {

    @Test
    fun `detects a direct PausedDownloadException`() {
        assertThat(PausedDownloadException().isPauseCancellation()).isTrue()
    }

    @Test
    fun `detects a wrapped PausedDownloadException`() {
        val wrapped = CancellationException("parent cancelled").apply { initCause(PausedDownloadException()) }
        assertThat(wrapped.isPauseCancellation()).isTrue()
    }

    @Test
    fun `does not flag a plain cancellation`() {
        assertThat(CancellationException("cancelled").isPauseCancellation()).isFalse()
    }

    @Test
    fun `does not flag a real IO failure`() {
        assertThat(IOException("network down").isPauseCancellation()).isFalse()
    }
}
