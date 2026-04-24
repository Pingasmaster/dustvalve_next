package com.dustvalve.next.android.update

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * Regression coverage for the cold-start silent-check + shared-state flow.
 *
 * The Settings row and the MainActivity startup dialog share one
 * [AppUpdateController] so both see the same state and can't race on the
 * download coroutine.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppUpdateControllerTest {

    private val dispatcher = StandardTestDispatcher()
    private val service = mockk<AppUpdateService>(relaxed = true)

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `checkSilently moves state to Available when service returns an update`() = runTest(dispatcher) {
        coEvery { service.checkForUpdate() } returns AppUpdateService.AvailableUpdate(
            versionName = "9.9.9",
            apkDownloadUrl = "https://releases/9.9.9/app-release.apk",
        )
        val controller = AppUpdateController(service).also { it.scope = this }

        controller.checkSilently()
        advanceUntilIdle()

        val state = controller.state.value
        assertThat(state).isInstanceOf(UpdateUiState.Available::class.java)
        assertThat((state as UpdateUiState.Available).versionName).isEqualTo("9.9.9")
    }

    @Test fun `checkSilently keeps state Idle when no update is available`() = runTest(dispatcher) {
        coEvery { service.checkForUpdate() } returns null
        val controller = AppUpdateController(service).also { it.scope = this }

        controller.checkSilently()
        advanceUntilIdle()

        assertThat(controller.state.value).isEqualTo(UpdateUiState.Idle)
    }

    @Test fun `checkSilently swallows failures - no message, no state change`() = runTest(dispatcher) {
        coEvery { service.checkForUpdate() } throws IOException("network dead")
        val controller = AppUpdateController(service).also { it.scope = this }
        val messagesSeen = mutableListOf<Any>()
        backgroundScope.launch {
            controller.messages.collect { messagesSeen += it }
        }
        advanceUntilIdle()

        controller.checkSilently()
        advanceUntilIdle()

        assertThat(controller.state.value).isEqualTo(UpdateUiState.Idle)
        assertThat(messagesSeen).isEmpty()  // silent = no user-visible toast
    }

    @Test fun `checkSilently is idempotent per process - second call is a no-op`() = runTest(dispatcher) {
        coEvery { service.checkForUpdate() } returns AppUpdateService.AvailableUpdate(
            versionName = "9.9.9", apkDownloadUrl = "https://x/app-release.apk",
        )
        val controller = AppUpdateController(service).also { it.scope = this }

        controller.checkSilently()
        controller.checkSilently()
        controller.checkSilently()
        advanceUntilIdle()

        coVerify(exactly = 1) { service.checkForUpdate() }
    }

    @Test fun `checkSilently does not clobber an in-flight Downloading state`() = runTest(dispatcher) {
        // checkSilently is a "first writer wins" style: if a manual flow has
        // already moved to Downloading, the silent success must NOT reset it
        // to Available.
        coEvery { service.checkForUpdate() } returns AppUpdateService.AvailableUpdate(
            versionName = "9.9.9", apkDownloadUrl = "https://x/app-release.apk",
        )
        val controller = AppUpdateController(service).also { it.scope = this }
        // Simulate an in-flight download started by the manual path.
        controller::class.java.getDeclaredField("_state").apply {
            isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val flow = get(controller) as kotlinx.coroutines.flow.MutableStateFlow<UpdateUiState>
            flow.value = UpdateUiState.Downloading("9.9.9", 0.25f)
        }

        controller.checkSilently()
        advanceUntilIdle()

        assertThat(controller.state.value).isInstanceOf(UpdateUiState.Downloading::class.java)
    }

    @Test fun `checkManually emits no-update message on null`() = runTest(dispatcher) {
        coEvery { service.checkForUpdate() } returns null
        val controller = AppUpdateController(service).also { it.scope = this }

        controller.messages.test {
            controller.checkManually()
            advanceUntilIdle()
            // Some kind of UiText — specific copy tested in SettingsScreen-level tests.
            assertThat(awaitItem()).isNotNull()
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(controller.state.value).isEqualTo(UpdateUiState.Idle)
    }

    @Test fun `checkManually emits check-failed message on exception`() = runTest(dispatcher) {
        coEvery { service.checkForUpdate() } throws IOException("boom")
        val controller = AppUpdateController(service).also { it.scope = this }

        controller.messages.test {
            controller.checkManually()
            advanceUntilIdle()
            assertThat(awaitItem()).isNotNull()
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(controller.state.value).isEqualTo(UpdateUiState.Idle)
    }

    @Test fun `dismiss moves Available state back to Idle`() = runTest(dispatcher) {
        coEvery { service.checkForUpdate() } returns AppUpdateService.AvailableUpdate(
            versionName = "9.9.9", apkDownloadUrl = "https://x/app-release.apk",
        )
        val controller = AppUpdateController(service).also { it.scope = this }
        controller.checkSilently()
        advanceUntilIdle()
        assertThat(controller.state.value).isInstanceOf(UpdateUiState.Available::class.java)

        controller.dismiss()

        assertThat(controller.state.value).isEqualTo(UpdateUiState.Idle)
    }

    @Test fun `dismiss is a no-op while Downloading (user can't abort silently)`() = runTest(dispatcher) {
        val controller = AppUpdateController(service).also { it.scope = this }
        controller::class.java.getDeclaredField("_state").apply {
            isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val flow = get(controller) as kotlinx.coroutines.flow.MutableStateFlow<UpdateUiState>
            flow.value = UpdateUiState.Downloading("9.9.9", 0.5f)
        }

        controller.dismiss()

        assertThat(controller.state.value).isInstanceOf(UpdateUiState.Downloading::class.java)
    }
}
