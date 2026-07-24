package com.dustvalve.next.android.data.local.datastore

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Regression for the missing ReplaceFileCorruptionHandler: a corrupt
 * settings.preferences_pb used to make every read throw CorruptionException
 * into the flow AND every write rethrow it, so the store was bricked forever
 * (CorruptionException extends IOException, which the read guard silently
 * swallowed into defaults, hiding the bricked writes).
 *
 * Kept in its own class: the preferencesDataStore delegate caches its
 * DataStore in static state, and the corrupt file must be on disk before the
 * FIRST read ever happens in this sandbox.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsDataStoreCorruptionTest {

    private val dataStoreFile: File
        get() {
            val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
            return File(ctx.filesDir, "datastore/settings.preferences_pb")
        }

    @Before fun plantCorruptFile() {
        dataStoreFile.parentFile?.mkdirs()
        // Not a valid preferences protobuf.
        dataStoreFile.writeBytes("garbage, definitely not a protobuf".toByteArray())
    }

    @After fun tearDown() {
        dataStoreFile.delete()
    }

    @Test fun `corrupt preferences file self-heals - writes and reads work`() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = SettingsDataStore(ctx)

        // Without the corruption handler this write threw CorruptionException.
        store.setThemeMode("dark")
        assertThat(store.themeMode.first()).isEqualTo("dark")
    }
}
