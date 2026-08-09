package com.dustvalve.next.android.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 -> v2:
 * - playlists.sourceUrl: durable remote-collection URL <-> playlist id mapping
 * - favorites primary key becomes (id, type) so album/artist/track ids cannot
 *   clobber each other across types
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `playlists` ADD COLUMN `sourceUrl` TEXT")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_playlists_sourceUrl` " +
                "ON `playlists` (`sourceUrl`)",
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `favorites_new` (
                `id` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `addedAt` INTEGER NOT NULL,
                `isPinned` INTEGER NOT NULL,
                `shapeKey` TEXT,
                PRIMARY KEY(`id`, `type`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO `favorites_new` (`id`, `type`, `addedAt`, `isPinned`, `shapeKey`)
            SELECT `id`, `type`, `addedAt`, `isPinned`, `shapeKey` FROM `favorites`
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE `favorites`")
        db.execSQL("ALTER TABLE `favorites_new` RENAME TO `favorites`")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_favorites_type_addedAt` " +
                "ON `favorites` (`type`, `addedAt`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_favorites_isPinned` " +
                "ON `favorites` (`isPinned`)",
        )
    }
}
