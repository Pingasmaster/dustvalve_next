package com.dustvalve.next.android.util

import com.dustvalve.next.android.R
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class UiFailuresTest {

    @Test
    fun `runCatchingUi returns success`() = runBlocking {
        val result = runCatchingUi(R.string.common_search_failed) { 42 }
        assertThat(result).isEqualTo(UiResult.Success(42))
    }

    @Test
    fun `runCatchingUi maps exception to UiText`() = runBlocking {
        val result = runCatchingUi(R.string.common_search_failed) {
            throw IOException("offline")
        }
        val failure = result as UiResult.Failure
        assertThat(failure.error).isEqualTo(UiText.DynamicString("offline"))
        assertThat(failure.cause).isInstanceOf(IOException::class.java)
    }

    @Test
    fun `runCatchingUi uses fallback when message null`() = runBlocking {
        val result = runCatchingUi(R.string.common_search_failed) {
            throw IOException()
        }
        val failure = result as UiResult.Failure
        assertThat(failure.error).isInstanceOf(UiText.StringResource::class.java)
        assertThat((failure.error as UiText.StringResource).resId)
            .isEqualTo(R.string.common_search_failed)
    }

    @Test
    fun `runCatchingUi rethrows CancellationException`() {
        assertThrows(CancellationException::class.java) {
            runBlocking {
                runCatchingUi(R.string.common_search_failed) {
                    throw CancellationException("cancelled")
                }
            }
        }
    }

    @Test
    fun `runCatchingUiIgnore swallows non-cancellation`() = runBlocking {
        var failed = false
        runCatchingUiIgnore(onFailure = { failed = true }) {
            throw IOException("x")
        }
        assertThat(failed).isTrue()
    }

    @Test
    fun `runCatchingUiIgnore rethrows CancellationException`() {
        assertThrows(CancellationException::class.java) {
            runBlocking {
                runCatchingUiIgnore {
                    throw CancellationException("cancelled")
                }
            }
        }
    }

    @Test
    fun `runCatchingUiOrNullSync returns null on failure`() {
        assertThat(runCatchingUiOrNullSync<String> { throw IOException("x") }).isNull()
        assertThat(runCatchingUiOrNullSync { "ok" }).isEqualTo("ok")
    }

    @Test
    fun `Throwable toUiText maps message`() {
        assertThat(IOException("boom").toUiText(R.string.common_search_failed))
            .isEqualTo(UiText.DynamicString("boom"))
    }
}
