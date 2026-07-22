package com.dustvalve.next.android.data.storage.folder

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import java.io.IOException

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

    /**
     * Best-effort quarantine of a corrupt snapshot: renames `dustvalve/[name]`
     * to `[name].corrupt` (replacing any earlier quarantine) so the bad bytes
     * stay around for inspection while readers see the file as absent and the
     * mirror's next flush starts fresh. Returns false when the file is gone
     * or the provider refuses the rename.
     */
    suspend fun quarantine(context: Context, treeUri: Uri, name: String, dispatcher: CoroutineDispatcher): Boolean =
        withContext(dispatcher) {
            val file = DedicatedFolderPaths.find(context, treeUri, name) ?: return@withContext false
            try {
                DedicatedFolderPaths.find(context, treeUri, "$name.corrupt")?.delete()
                file.renameTo("$name.corrupt")
            } catch (e: Exception) {
                if (e is kotlin.coroutines.cancellation.CancellationException) throw e
                false
            }
        }

    private fun <T> writeJsonBlocking(context: Context, treeUri: Uri, name: String, serializer: KSerializer<T>, value: T) {
        val root = DedicatedFolderPaths.dustvalveRoot(context, treeUri)
            ?: throw IOException("Dustvalve folder not accessible")
        val bytes = FolderSnapshotSerializer.json.encodeToString(serializer, value).toByteArray(Charsets.UTF_8)
        // Write via a tmp sibling then rename for atomicity on normal SAF
        // providers. The tmp name KEEPS the .json extension ("x.tmp.json"):
        // providers append an extension when the display name doesn't match
        // the declared MIME type, which used to turn "x.json.tmp" into
        // "x.json.tmp.json" and break every later findFile(tmpName) lookup,
        // leaking tmp files and skipping the rename. We also only ever
        // operate on the DocumentFile returned by createFile - never re-find
        // it by display name.
        val tmpName = tmpNameFor(name)
        try {
            root.findFile(tmpName)?.delete()
        } catch (e: Exception) {
            if (e is kotlin.coroutines.cancellation.CancellationException) throw e
        }
        val tmp = root.createFile(DedicatedFolderPaths.JSON_MIME, tmpName)
            ?: throw IOException("Failed to create $tmpName")
        var renamed = false
        try {
            context.contentResolver.openOutputStream(tmp.uri, "wt")?.use { it.write(bytes) }
                ?: throw IOException("Failed to open output stream for $tmpName")

            // Swap: drop the old target, then rename the fully-written tmp
            // over it. A crash in the gap leaves the target absent (treated
            // as no snapshot) with the complete tmp on disk - never a
            // half-truncated canonical file.
            try {
                root.findFile(name)?.delete()
            } catch (e: Exception) {
                if (e is kotlin.coroutines.cancellation.CancellationException) throw e
            }
            renamed = try {
                tmp.renameTo(name)
            } catch (e: Exception) {
                if (e is kotlin.coroutines.cancellation.CancellationException) throw e
                false
            }
            if (!renamed) {
                // Provider genuinely doesn't support rename: create the
                // target and write the fully-buffered bytes directly.
                writeBytesToTarget(context, root, root.findFile(name), name, bytes)
            }
        } finally {
            // On successful rename tmp IS the target now; otherwise clean it
            // up on every path (fallback write, exception, ...).
            if (!renamed) {
                try {
                    tmp.delete()
                } catch (e: Exception) {
                    if (e is kotlin.coroutines.cancellation.CancellationException) throw e
                }
            }
        }
    }

    /** "albums.json" -> "albums.tmp.json": tmp sibling name whose extension matches [DedicatedFolderPaths.JSON_MIME]. */
    private fun tmpNameFor(name: String): String = "${name.removeSuffix(".json")}.tmp.json"

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
