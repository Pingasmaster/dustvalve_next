package com.dustvalve.next.android.testing

import android.content.ContentUris
import android.content.ContentValues
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry

/**
 * Seeds the device MediaStore with the small MP3 tones bundled in
 * androidTest/assets/audio. Instrumentation shares the app UID, so the
 * inserts are app-owned and visible to MediaStoreScanner after the app's
 * own enable + scan flow runs. Idempotent: seeding repeatedly is safe and
 * keeps the SAME rows, which is the point - see [cleanup].
 */
object LocalMusicSeeder {

    private const val PREFIX = "dv_test_tone_"
    private val ASSETS = listOf("dv_test_tone_1.mp3", "dv_test_tone_2.mp3", "dv_test_tone_3.mp3")

    fun seed(): Int {
        val instr = InstrumentationRegistry.getInstrumentation()
        val resolver = instr.targetContext.contentResolver
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        var seeded = 0
        for (asset in ASSETS) {
            // Skip if already present.
            val exists = resolver.query(
                collection,
                arrayOf(MediaStore.Audio.Media._ID),
                "${MediaStore.Audio.Media.DISPLAY_NAME} = ?",
                arrayOf(asset),
                null,
            )?.use { it.count > 0 } == true
            if (exists) continue

            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, asset)
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
                put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(collection, values) ?: continue
            resolver.openOutputStream(uri)?.use { out ->
                instr.context.assets.open("audio/$asset").use { it.copyTo(out) }
            }
            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            seeded++
        }
        return seeded
    }

    /**
     * Deletes the seeded rows. NOT for use between tests - do not put this in
     * an @After.
     *
     * MediaStore hands out a NEW _ID on every insert, but the app's Room
     * library persists for the whole install and keeps the
     * content://media/external/audio/media/<id> URIs from its last scan.
     * Deleting and re-seeding between tests therefore leaves the app holding
     * URIs that no longer resolve, and the next playback dies with
     * "FileNotFoundException: No item at content://..." - which surfaces as a
     * position stuck at 0:00, i.e. indistinguishable from the exact playback
     * regression this suite exists to catch. That cost a full CI
     * investigation once; it was an @After on three test classes.
     *
     * Seeding is idempotent, so stability is free: seed in @Before, never
     * delete mid-run. CI gets a fresh GMD emulator each time, and on a
     * persistent local device leftover tones are harmless. Kept only for
     * manually resetting a dev device.
     */
    fun cleanup() {
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        resolver.query(
            collection,
            arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME),
            "${MediaStore.Audio.Media.DISPLAY_NAME} LIKE ?",
            arrayOf("$PREFIX%"),
            null,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            while (cursor.moveToNext()) {
                resolver.delete(ContentUris.withAppendedId(collection, cursor.getLong(idCol)), null, null)
            }
        }
    }
}
