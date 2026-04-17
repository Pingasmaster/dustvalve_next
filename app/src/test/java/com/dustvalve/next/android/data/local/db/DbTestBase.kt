package com.dustvalve.next.android.data.local.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before

/**
 * Shared scaffolding for DAO tests: creates an in-memory Room database and closes it after each test.
 */
abstract class DbTestBase {

    protected lateinit var db: DustvalveNextDatabase

    @Before fun openDb() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, DustvalveNextDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After fun closeDb() {
        db.close()
    }
}
