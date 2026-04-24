package com.dustvalve.next.android.data.storage.folder

import android.content.Context
import androidx.core.net.toUri
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Checks that the persisted dedicated-folder tree URI is still reachable,
 * still backed by a live permission grant, and contains (or can contain)
 * our `dustvalve/` subdir. Cheap enough to call on every cold start.
 */
@Singleton
class FolderHealthChecker @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore,
) {
    /**
     * Returns true when the feature is on, a tree URI is stored, we still
     * hold persistable permission for it, and the tree's root is reachable.
     * Returns false otherwise; the caller decides whether to block the UI.
     */
    suspend fun check(): Boolean = withContext(Dispatchers.IO) {
        val enabled = settingsDataStore.getDedicatedFolderEnabledSync()
        if (!enabled) return@withContext true
        val uriStr = settingsDataStore.getDedicatedFolderTreeUriSync() ?: return@withContext false
        val uri = try { uriStr.toUri() } catch (_: Exception) { return@withContext false }

        val granted = context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission && it.isWritePermission
        }
        if (!granted) return@withContext false

        val root = DedicatedFolderPaths.treeRoot(context, uri) ?: return@withContext false
        root.exists() && root.canRead() && root.canWrite()
    }
}
