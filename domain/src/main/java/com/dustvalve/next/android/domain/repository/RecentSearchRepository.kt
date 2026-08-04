package com.dustvalve.next.android.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Recent-search history, keyed by provider source string ("bandcamp",
 * "youtube", "soundcloud", "local"). The searchHistoryEnabled gate stays in
 * the ViewModels; this repository only persists.
 */
interface RecentSearchRepository {

    /** Newest-first query strings for one source, capped at [limit]. */
    fun getRecent(source: String, limit: Int = 8): Flow<List<String>>

    /**
     * Stores [query] verbatim (callers own whitespace trimming - the
     * Bandcamp/YouTube/Local VMs pass `query.trim()`, SoundCloud passes the
     * raw query) and, when [trim], caps the source's history at 20 entries.
     * `trim = false` exists ONLY to preserve SoundCloudViewModel's historical
     * uncapped behavior (that screen never called deleteOld).
     */
    suspend fun add(query: String, source: String, trim: Boolean = true)

    suspend fun remove(query: String, source: String)

    suspend fun clear(source: String)

    /** Clears every known source: bandcamp, youtube, soundcloud, local (SettingsViewModel's loop, relocated). */
    suspend fun clearAllSources()
}
