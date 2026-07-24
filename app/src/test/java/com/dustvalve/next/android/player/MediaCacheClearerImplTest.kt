@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.dustvalve.next.android.player

import androidx.media3.datasource.cache.SimpleCache
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test

class MediaCacheClearerImplTest {

    @After fun tearDown() {
        unmockkAll()
    }

    private fun clearer(cache: SimpleCache) = MediaCacheClearerImpl(
        simpleCache = dagger.Lazy<SimpleCache> { cache },
        ioDispatcher = UnconfinedTestDispatcher(),
    )

    @Test fun `clearAll removes every cached resource through the cache API`() = runTest {
        val cache = mockk<SimpleCache>()
        every { cache.keys } returns mutableSetOf("res_a", "res_b")
        every { cache.removeResource(any()) } just Runs

        clearer(cache).clearAll()

        verify(exactly = 1) { cache.removeResource("res_a") }
        verify(exactly = 1) { cache.removeResource("res_b") }
    }

    @Test fun `clearAll keeps going when removing one resource fails`() = runTest {
        val cache = mockk<SimpleCache>()
        every { cache.keys } returns mutableSetOf("res_a", "res_b")
        every { cache.removeResource("res_a") } throws IllegalStateException("index desync")
        every { cache.removeResource("res_b") } just Runs

        clearer(cache).clearAll() // must not throw

        verify(exactly = 1) { cache.removeResource("res_b") }
    }
}
