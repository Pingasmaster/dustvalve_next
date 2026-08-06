package com.dustvalve.next.android.data.local.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before
import java.util.concurrent.Executor

/**
 * Shared scaffolding for DAO tests: creates an in-memory Room database and closes it after each test.
 */
abstract class DbTestBase {

    protected lateinit var db: DustvalveNextDatabase

    @Before fun openDb() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, DustvalveNextDatabase::class.java)
            .allowMainThreadQueries()
            // Run queries, transactions and - the reason this is here - the
            // InvalidationTracker refresh inline on the calling thread.
            //
            // Room otherwise hands invalidation to its own background executor,
            // so a `@Query` Flow re-emits on a real thread with real wall-clock
            // timing, outside the virtual clock of runTest. A collector then
            // waits on a wall-clock timeout (Turbine's is 3s) for work no test
            // dispatcher governs, and a loaded machine loses that race: the
            // favoriteIds test failed intermittently with "No value produced".
            // Inline, the emission is already published by the time the
            // suspending write returns, so there is no race left to lose.
            .setQueryExecutor(DIRECT_EXECUTOR)
            .setTransactionExecutor(DIRECT_EXECUTOR)
            .build()
    }

    @After fun closeDb() {
        db.close()
    }

    private companion object {
        /** Runs the task on the caller's thread, so nothing is deferred. */
        val DIRECT_EXECUTOR = Executor { command -> command.run() }
    }
}
