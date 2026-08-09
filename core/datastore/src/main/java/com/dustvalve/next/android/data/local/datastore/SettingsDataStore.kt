package com.dustvalve.next.android.data.local.datastore

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Facade over domain settings slices that share one preferences DataStore.
 * Call sites keep injecting [SettingsDataStore]; the TooManyFunctions surface
 * lives in the per-domain stores below.
 */
private class SettingsStoreBundles(prefs: SettingsPreferences) {
    val appearance: AppearanceSettingsStore = AppearanceSettingsStoreImpl(prefs)
    val download: DownloadStorageSettingsStore = DownloadStorageSettingsStoreImpl(prefs)
    val localMusic: LocalMusicPrefsStore = LocalMusicPrefsStoreImpl(prefs)
    val sources: SourcesSearchPlayerPrefsStore = SourcesSearchPlayerPrefsStoreImpl(prefs)
}

@Singleton
class SettingsDataStore private constructor(
    appearance: AppearanceSettingsStore,
    download: DownloadStorageSettingsStore,
    localMusic: LocalMusicPrefsStore,
    sources: SourcesSearchPlayerPrefsStore,
) : AppearanceSettingsStore by appearance,
    DownloadStorageSettingsStore by download,
    LocalMusicPrefsStore by localMusic,
    SourcesSearchPlayerPrefsStore by sources {

    @Inject
    constructor(@ApplicationContext context: Context) : this(SettingsStoreBundles(SettingsPreferences(context)))

    private constructor(bundles: SettingsStoreBundles) : this(
        appearance = bundles.appearance,
        download = bundles.download,
        localMusic = bundles.localMusic,
        sources = bundles.sources,
    )

    companion object {
        const val DEFAULT_STORAGE_LIMIT = 2L * 1024 * 1024 * 1024 // 2 GB
    }
}
