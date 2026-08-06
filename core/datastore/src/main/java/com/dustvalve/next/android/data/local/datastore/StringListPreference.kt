package com.dustvalve.next.android.data.local.datastore

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * JSON-encoded `List<String>` preference values.
 *
 * [decode] treats a malformed value as empty instead of throwing a
 * SerializationException on every read forever - a garbage value written by an
 * older build would otherwise make the key permanently unreadable. The next
 * successful set() overwrites the bad value.
 */
object StringListPreference {

    fun decode(json: String?): List<String> {
        if (json == null) return emptyList()
        return try {
            Json.decodeFromString<List<String>>(json)
        } catch (_: SerializationException) {
            emptyList()
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
    }
}
