package com.dustvalve.next.android.testing

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import kotlinx.coroutines.runBlocking

/**
 * Instrumentation runner for the smoke + E2E suites. Identical to
 * AndroidJUnitRunner except that it turns OFF the silent cold-start update
 * check BEFORE Application.onCreate fires it: once a newer GitHub release
 * exists, the real check pops the "Update available" dialog mid-test and
 * covers whatever the test is asserting on (this broke the live E2E suite
 * the moment v0.5.1 was published). A @Before can't do this - it runs after
 * Application.onCreate has already launched the check coroutine, so the
 * write races the read. Writing the DataStore preference here is
 * deterministic: the check reads autoUpdateCheckEnabled == false and skips.
 */
class DustvalveTestRunner : AndroidJUnitRunner() {

    override fun callApplicationOnCreate(app: Application) {
        val target: Context = targetContext.applicationContext
        runBlocking { SettingsDataStore(target).setAutoUpdateCheckEnabled(false) }
        super.callApplicationOnCreate(app)
    }
}
