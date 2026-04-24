package com.dustvalve.next.android.data.storage.folder

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/**
 * Resolves paths inside the user-picked SAF tree where user data is mirrored
 * when [com.dustvalve.next.android.data.local.datastore.SettingsDataStore.dedicatedFolderEnabled]
 * is true. All canonical files live under a single `dustvalve/` subdirectory so
 * the rest of the user's folder is undisturbed.
 *
 * Layout:
 *   dustvalve/
 *     settings.json, playlists.json, favorites.json, tracks.json,
 *     albums.json, artists.json, downloads.json, history.json
 *     downloads/<safeAlbumId>/<safeTrackId>.<ext>
 *     cache/metadata.json            (only if metadata-cache sub-toggle on)
 *     cache/images/<sha1(url)>.<ext> (only if image-cache sub-toggle on)
 */
object DedicatedFolderPaths {

    const val ROOT_DIR = "dustvalve"
    const val DOWNLOADS_DIR = "downloads"
    const val CACHE_DIR = "cache"
    const val IMAGES_DIR = "images"

    const val FILE_SETTINGS = "settings.json"
    const val FILE_PLAYLISTS = "playlists.json"
    const val FILE_FAVORITES = "favorites.json"
    const val FILE_TRACKS = "tracks.json"
    const val FILE_ALBUMS = "albums.json"
    const val FILE_ARTISTS = "artists.json"
    const val FILE_DOWNLOADS = "downloads.json"
    const val FILE_HISTORY = "history.json"
    const val FILE_METADATA_CACHE = "metadata.json"

    const val JSON_MIME = "application/json"

    /** Top-level tree `DocumentFile`, or null if the URI is no longer accessible. */
    fun treeRoot(context: Context, treeUri: Uri): DocumentFile? =
        DocumentFile.fromTreeUri(context, treeUri)

    /** `dustvalve/` subdir, creating if missing. */
    fun dustvalveRoot(context: Context, treeUri: Uri): DocumentFile? {
        val root = treeRoot(context, treeUri) ?: return null
        return root.findFile(ROOT_DIR) ?: root.createDirectory(ROOT_DIR)
    }

    fun downloadsDir(context: Context, treeUri: Uri): DocumentFile? {
        val root = dustvalveRoot(context, treeUri) ?: return null
        return root.findFile(DOWNLOADS_DIR) ?: root.createDirectory(DOWNLOADS_DIR)
    }

    fun cacheDir(context: Context, treeUri: Uri): DocumentFile? {
        val root = dustvalveRoot(context, treeUri) ?: return null
        return root.findFile(CACHE_DIR) ?: root.createDirectory(CACHE_DIR)
    }

    fun imageCacheDir(context: Context, treeUri: Uri): DocumentFile? {
        val cache = cacheDir(context, treeUri) ?: return null
        return cache.findFile(IMAGES_DIR) ?: cache.createDirectory(IMAGES_DIR)
    }

    /** Finds `name` under `dustvalve/` without creating it. */
    fun find(context: Context, treeUri: Uri, name: String): DocumentFile? {
        val root = treeRoot(context, treeUri)?.findFile(ROOT_DIR) ?: return null
        return root.findFile(name)
    }

    /** Finds `name` under `dustvalve/cache/` without creating it. */
    fun findInCache(context: Context, treeUri: Uri, name: String): DocumentFile? {
        val cache = treeRoot(context, treeUri)
            ?.findFile(ROOT_DIR)
            ?.findFile(CACHE_DIR) ?: return null
        return cache.findFile(name)
    }
}
