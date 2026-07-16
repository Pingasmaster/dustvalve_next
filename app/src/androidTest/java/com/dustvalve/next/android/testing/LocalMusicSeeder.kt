package com.dustvalve.next.android.testing

import android.content.ContentUris
import android.content.ContentValues
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry

/**
 * Seeds the device MediaStore with the small MP3 tones bundled in
 * androidTest/assets/audio. Instrumentation shares the app UID, so the
 * inserts are app-owned and visible to MediaStoreScanner after the app's
 * own enable + scan flow runs. Idempotent; [cleanup] deletes by the
 * DISPLAY_NAME prefix.
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
