package com.dustvalve.next.android.data.storage.folder

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.io.OutputStream

/**
 * Low-level helpers for reading and writing JSON files to a SAF tree. Always
 * run on the caller-provided [CoroutineDispatcher] (typically the injected
 * @Dispatcher(AppDispatchers.IO)); throws [IOException] on stream failure and
 * [SerializationException] on decoding failure.
 */
object FolderIo {

    suspend fun <T> readJson(
        context: Context,
        treeUri: Uri,
        name: String,
        serializer: KSerializer<T>,
        dispatcher: CoroutineDispatcher,
    ): T? = withContext(dispatcher) {
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
        dispatcher: CoroutineDispatcher,
    ) {
        withContext(dispatcher) { writeJsonBlocking(context, treeUri, name, serializer, value) }
    }

    private fun <T> writeJsonBlocking(context: Context, treeUri: Uri, name: String, serializer: KSerializer<T>, value: T) {
        val root = DedicatedFolderPaths.dustvalveRoot(context, treeUri)
            ?: throw IOException("Dustvalve folder not accessible")
        val bytes = FolderSnapshotSerializer.json.encodeToString(serializer, value).toByteArray(Charsets.UTF_8)
        // Write via a .tmp sibling then rename for atomicity on normal SAF
        // providers. If the provider doesn't support rename we fall through
        // to overwriting the target directly.
        val tmpName = "$name.tmp"
        root.findFile(tmpName)?.delete()
        openWritable(context, root, tmpName).use { it.write(bytes) }

        val existing = root.findFile(name)
        val renamed = try {
            root.findFile(tmpName)?.renameTo(name) ?: false
        } catch (_: Exception) {
            false
        }
        if (!renamed) {
            // Fallback: copy tmp contents into the canonical file, then drop
            // tmp. Keeps partial writes out of the target until we've
            // successfully produced bytes.
            writeBytesToTarget(context, root, existing, name, bytes)
            root.findFile(tmpName)?.delete()
        } else if (existing != null && existing.uri != root.findFile(name)?.uri) {
            // Rename may leave the old target file intact on some providers.
            try {
                existing.delete()
            } catch (_: Exception) {}
        }
    }

    /** Create [name] under [parent] and open a writable [OutputStream], or throw [IOException]. */
    private fun openWritable(context: Context, parent: DocumentFile, name: String): OutputStream {
        val file = parent.createFile(DedicatedFolderPaths.JSON_MIME, name)
            ?: throw IOException("Failed to create $name")
        return context.contentResolver.openOutputStream(file.uri, "wt")
            ?: throw IOException("Failed to open output stream for $name")
    }

    /**
     * Overwrite [name] with [bytes], reusing [existing] if present, otherwise creating it.
     * Throws [IOException] on stream failure.
     */
    private fun writeBytesToTarget(context: Context, parent: DocumentFile, existing: DocumentFile?, name: String, bytes: ByteArray) {
        val target = existing ?: parent.createFile(DedicatedFolderPaths.JSON_MIME, name)
            ?: throw IOException("Failed to create $name")
        context.contentResolver.openOutputStream(target.uri, "wt")?.use { it.write(bytes) }
            ?: throw IOException("Failed to open output stream for $name")
    }
}
