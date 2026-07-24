package com.dustvalve.next.android.data.asset

import android.content.Context
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import com.dustvalve.next.android.data.local.db.DustvalveNextDatabase
import com.dustvalve.next.android.data.local.db.dao.DownloadDao
import com.dustvalve.next.android.data.local.db.entity.DownloadEntity
import com.dustvalve.next.android.data.util.ignoringStorageFailures
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LRU eviction over the unified downloads pool. Only operates on unpinned
 * (auto-cached) entries; pinned entries (explicit user downloads) are never
 * evicted - exceeding the storage budget surfaces in the UI but does not
 * silently delete user content.
 *
 * Coil's image disk cache and ExoPlayer's SimpleCache manage their own
 * eviction; this policy only governs `downloads` table rows.
 */
@Singleton
class AssetEvictionPolicy @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: DustvalveNextDatabase,
    private val downloadDao: DownloadDao,
) {

    /** Evicts unpinned entries oldest-first until at least [targetBytes] freed. */
    suspend fun evict(targetBytes: Long) {
        if (targetBytes <= 0L) return

        val candidates = downloadDao.getEvictionCandidates()
        if (candidates.isEmpty()) return

        var freed = 0L
        val toEvict = mutableListOf<DownloadEntity>()
        for (entry in candidates) {
            if (freed >= targetBytes) break
            toEvict += entry
            freed += entry.sizeBytes
        }
        if (toEvict.isEmpty()) return

        // Drop DB rows first, then unlink files. A crash between the two
        // leaves orphaned files (wasted disk) which is recoverable;
        // orphaned DB rows would inflate reported sizes.
        database.withTransaction {
            for (entry in toEvict) downloadDao.delete(entry.trackId)
        }
        for (entry in toEvict) {
            deleteByPath(entry.filePath)
        }
    }

    /**
     * Delete helper that handles both local file paths and the content://
     * URIs used in dedicated-folder mode, where File(path).delete() is a
     * silent no-op. Mirrors DownloadRepositoryImpl.deleteByPath.
     */
    private fun deleteByPath(path: String) {
        if (path.isBlank()) return
        if (path.startsWith("content://")) {
            ignoringStorageFailures { DocumentFile.fromSingleUri(context, path.toUri())?.delete() }
        } else {
            ignoringStorageFailures { File(path).delete() }
        }
    }
}
