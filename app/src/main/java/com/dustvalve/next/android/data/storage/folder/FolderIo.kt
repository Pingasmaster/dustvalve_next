package com.dustvalve.next.android.data.storage.folder

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import java.io.IOException

/**
 * Low-level helpers for reading and writing JSON files to a SAF tree. Always
 * run on [Dispatchers.IO]; throws [IOException] on stream failure and
 * [SerializationException] on decoding failure.
 */
object FolderIo {

    suspend fun <T> readJson(
        context: Context,
        treeUri: Uri,
        name: String,
        serializer: KSerializer<T>,
    ): T? = withContext(Dispatchers.IO) {
        val file = DedicatedFolderPaths.find(context, treeUri, name) ?: return@withContext null
        if (!file.isFile) return@withContext null
        val text = context.contentResolver.openInputStream(file.uri)?.use {
            it.bufferedReader().readText()
        } ?: return@withContext null
        if (text.isBlank()) return@withContext null
        FolderSnapshotSerializer.json.decodeFromString(serializer, text)
    }

    suspend fun <T> writeJson(
        context: Context,
        treeUri: Uri,
        name: String,
        serializer: KSerializer<T>,
        value: T,
    ) = withContext(Dispatchers.IO) {
        val root = DedicatedFolderPaths.dustvalveRoot(context, treeUri)
            ?: throw IOException("Dustvalve folder not accessible")
        val text = FolderSnapshotSerializer.json.encodeToString(serializer, value)
        // Write via a .tmp sibling then rename for atomicity on normal SAF
        // providers. If the provider doesn't support rename we fall through
        // to overwriting the target directly.
        val tmpName = "$name.tmp"
        root.findFile(tmpName)?.delete()
        val tmp = root.createFile(DedicatedFolderPaths.JSON_MIME, tmpName)
            ?: throw IOException("Failed to create $tmpName")
        context.contentResolver.openOutputStream(tmp.uri, "wt")?.use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
        } ?: throw IOException("Failed to open output stream for $tmpName")

        val existing = root.findFile(name)
        val renamed = try {
            tmp.renameTo(name)
        } catch (_: Exception) {
            false
        }
        if (!renamed) {
            // Fallback: copy tmp contents into the canonical file, then drop
            // tmp. Keeps partial writes out of the target until we've
            // successfully produced bytes.
            val target = existing ?: root.createFile(DedicatedFolderPaths.JSON_MIME, name)
                ?: throw IOException("Failed to create $name")
            context.contentResolver.openOutputStream(target.uri, "wt")?.use { out ->
                out.write(text.toByteArray(Charsets.UTF_8))
            } ?: throw IOException("Failed to open output stream for $name")
            tmp.delete()
        } else if (existing != null && existing.uri != tmp.uri) {
            // Rename may leave the old target file intact on some providers.
            try { existing.delete() } catch (_: Exception) {}
        }
    }

    suspend fun deleteJson(context: Context, treeUri: Uri, name: String) = withContext(Dispatchers.IO) {
        DedicatedFolderPaths.find(context, treeUri, name)?.delete()
        Unit
    }

    suspend fun deleteIfExists(file: DocumentFile?) = withContext(Dispatchers.IO) {
        if (file != null && file.exists()) {
            try { file.delete() } catch (_: Exception) {}
        }
    }
}
