package com.dustvalve.next.android.ui.navigation

import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras

/**
 * A [ViewModelStoreOwner] scoped to a single detail destination (album /
 * artist / playlist / collection page). Historically detail screens used
 * `hiltViewModel(key = url)` against MainActivity's ViewModelStore, so every
 * visited detail URL leaked a ViewModel (with live collectors) for the whole
 * Activity lifetime. Owning a private [ViewModelStore] per destination lets
 * [DetailVmStoreRegistry.retainOnly] clear it the moment the destination
 * leaves every tab's back stack.
 *
 * Implements [HasDefaultViewModelProviderFactory] by delegating to the host
 * Activity's factory + creation extras, so `hiltViewModel()` keeps resolving
 * `@HiltViewModel` classes exactly as before.
 */
internal class DetailViewModelStoreOwner(private val hostOwner: ViewModelStoreOwner) :
    ViewModelStoreOwner,
    HasDefaultViewModelProviderFactory {

    override val viewModelStore: ViewModelStore = ViewModelStore()

    override val defaultViewModelProviderFactory: ViewModelProvider.Factory
        get() = (hostOwner as HasDefaultViewModelProviderFactory).defaultViewModelProviderFactory

    override val defaultViewModelCreationExtras: CreationExtras
        get() = (hostOwner as? HasDefaultViewModelProviderFactory)
            ?.defaultViewModelCreationExtras
            ?: CreationExtras.Empty
}

/**
 * Holder for per-destination [DetailViewModelStoreOwner]s. Remembered once in
 * AppNavigation; entries are created lazily per detail key and cleared when
 * the key is no longer reachable from ANY tab's back stack.
 */
internal class DetailVmStoreRegistry(private val hostOwner: ViewModelStoreOwner) {

    private val owners = mutableMapOf<String, DetailViewModelStoreOwner>()

    fun owner(key: String): DetailViewModelStoreOwner = owners.getOrPut(key) { DetailViewModelStoreOwner(hostOwner) }

    /** Clear + drop every owner whose key is not in [liveKeys]. */
    fun retainOnly(liveKeys: Set<String>) {
        val iterator = owners.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key !in liveKeys) {
                entry.value.viewModelStore.clear()
                iterator.remove()
            }
        }
    }

    /** Clear everything (host composition is going away, e.g. Activity destroy). */
    fun clearAll() {
        owners.values.forEach { it.viewModelStore.clear() }
        owners.clear()
    }
}

/** Stable per-destination scoping key; null for non-detail destinations. */
internal fun detailStoreKey(dest: NavDestination): String? = when (dest) {
    is NavDestination.AlbumDetail -> "album|${dest.url}"
    is NavDestination.ArtistDetail -> "artist|${dest.sourceId}|${dest.url}"
    is NavDestination.PlaylistDetail -> "playlist|${dest.playlistId}"
    is NavDestination.CollectionDetail -> "collection|${dest.sourceId}|${dest.url}"
    else -> null
}
