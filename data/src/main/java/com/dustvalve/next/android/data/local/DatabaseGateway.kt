package com.dustvalve.next.android.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.dustvalve.next.android.data.local.db.DustvalveNextDatabase
import com.dustvalve.next.data.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sole owner of the Room database inside :data. The database and its DAOs are
 * an implementation detail of this module: repositories obtain DAOs through
 * this gateway's internal accessors, so no DAO/DB type ever appears in an
 * injectable signature that Hilt's :app-generated component must reference.
 *
 * The public primary constructor exists for tests (in-memory databases built
 * via DbTestBase); production wiring uses the @Inject secondary constructor,
 * which builds the one real database instance. This class replaced the old
 * :app DatabaseModule in the same commit that deleted it - the two must never
 * coexist in the Hilt graph, or two Room instances would open
 * "dustvalve_database" with no multi-instance invalidation.
 */
@Singleton
class DatabaseGateway(internal val database: DustvalveNextDatabase) {

    @Inject constructor(@ApplicationContext context: Context) : this(buildDatabase(context))

    internal val albumDao get() = database.albumDao()
    internal val trackDao get() = database.trackDao()
    internal val artistDao get() = database.artistDao()
    internal val favoriteDao get() = database.favoriteDao()
    internal val recentTrackDao get() = database.recentTrackDao()
    internal val downloadDao get() = database.downloadDao()
    internal val playlistDao get() = database.playlistDao()
    internal val recentSearchDao get() = database.recentSearchDao()
    internal val youtubeVideoCacheDao get() = database.youtubeVideoCacheDao()
    internal val youtubePlaylistCacheDao get() = database.youtubePlaylistCacheDao()
    internal val youtubeMusicHomeCacheDao get() = database.youtubeMusicHomeCacheDao()

    companion object {
        internal fun buildDatabase(context: Context): DustvalveNextDatabase = Room.databaseBuilder(
            context,
            DustvalveNextDatabase::class.java,
            "dustvalve_database",
        )
            // Migration policy: destructive fallback is a DEBUG-only convenience
            // so schema churn during development never blocks on a hand-written
            // migration. Release builds deliberately omit it - shipping a schema
            // bump without a Migration must fail loudly on open
            // (IllegalStateException) instead of silently dropping every user
            // table (playlists, favorites, downloads). Exported schema JSON for
            // writing those migrations lives in core/database/schemas/.
            // (:data's library debug build type pairs with app debug, so
            // BuildConfig.DEBUG here matches the old :app check.)
            .apply { if (BuildConfig.DEBUG) fallbackToDestructiveMigration(dropAllTables = true) }
            // Write-Ahead Logging: readers and writers don't block each other,
            // which matters because DownloadService / DownloadController mutate
            // rows from a different dispatcher than UI queries. Default is
            // TRUNCATE on Room 2.7+ which serialises everything.
            //
            // We deliberately do NOT call enableMultiInstanceInvalidation() - it
            // binds a ServiceConnection that Robolectric can't satisfy, breaking
            // the Compose-test harness (TracksHeaderLabelTest et al.), and the
            // app is single-process anyway (one Application, no :remote Process
            // IPC). The trade-off is that writes from a hypothetical future
            // process boundary wouldn't invalidate this Room instance, but
            // that's not a path we use today.
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build()
    }
}
