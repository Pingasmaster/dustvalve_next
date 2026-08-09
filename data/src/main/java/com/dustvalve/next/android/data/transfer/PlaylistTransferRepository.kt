package com.dustvalve.next.android.data.transfer

import android.content.Context
import android.net.Uri
import android.os.storage.StorageManager
import androidx.core.net.toUri
import androidx.room.withTransaction
import com.dustvalve.next.android.data.asset.StoragePaths
import com.dustvalve.next.android.data.local.DatabaseGateway
import com.dustvalve.next.android.data.local.db.DustvalveNextDatabase
import com.dustvalve.next.android.data.local.db.dao.DownloadDao
import com.dustvalve.next.android.data.local.db.dao.TrackDao
import com.dustvalve.next.android.data.local.db.dao.getByIds
import com.dustvalve.next.android.data.local.db.entity.DownloadEntity
import com.dustvalve.next.android.data.local.db.entity.TrackEntity
import com.dustvalve.next.android.di.qualifiers.AppDispatchers
import com.dustvalve.next.android.di.qualifiers.Dispatcher
import com.dustvalve.next.android.domain.model.AudioFormat
import com.dustvalve.next.android.domain.model.Playlist
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.repository.DownloadRepository
import com.dustvalve.next.android.domain.repository.PlaylistRepository
import com.dustvalve.next.android.util.NetworkUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Exports a playlist to / imports it from a single `.dvplaylist` ZIP. An **offline** bundle
 * downloads every track + cover so it plays with no network after import; a **lightweight**
 * bundle stores metadata only and re-streams online. Reuses the existing download pipeline,
 * storage layout, and snapshot DTOs.
 */
@Singleton
class PlaylistTransferRepository(
    private val context: Context,
    private val playlistRepository: PlaylistRepository,
    private val downloadRepository: DownloadRepository,
    private val database: DustvalveNextDatabase,
    private val trackDao: TrackDao,
    private val downloadDao: DownloadDao,
    private val client: OkHttpClient,
    private val ioDispatcher: CoroutineDispatcher,
) {

    @Inject constructor(
        @ApplicationContext context: Context,
        playlistRepository: PlaylistRepository,
        downloadRepository: DownloadRepository,
        gateway: DatabaseGateway,
        client: OkHttpClient,
        @Dispatcher(AppDispatchers.IO) ioDispatcher: CoroutineDispatcher,
    ) : this(
        context,
        playlistRepository,
        downloadRepository,
        gateway.database,
        gateway.trackDao,
        gateway.downloadDao,
        client,
        ioDispatcher,
    )
    private val json = PlaylistBundleSerializer.json

    /** Write [playlistId] to [out] as a `.dvplaylist` ZIP. [onProgress] reports (done, total). */
    suspend fun export(
        playlistId: String,
        offline: Boolean,
        out: OutputStream,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ) = withContext(ioDispatcher) {
        val playlist = playlistRepository.getPlaylistByIdSync(playlistId)
            ?: throw IllegalStateException("Playlist not found: $playlistId")
        val tracks = playlistRepository.getTracksInPlaylistSync(playlistId)
        val total = tracks.size

        ZipOutputStream(out.buffered()).use { zip ->
            val coverPaths = HashMap<String, String>() // albumId -> cover entry name (dedupe)
            val entries = ArrayList<BundleEntry>(tracks.size)

            tracks.forEachIndexed { index, track ->
                coroutineContext.ensureActive()
                if (offline) {
                    var info = downloadRepository.getDownloadInfo(track.id)
                    if (info == null) {
                        // Safety net: download paths throw IOException (and
                        // subtypes). Log and export the track without local
                        // audio. CancellationException is rethrown so coroutine
                        // cancellation propagates.
                        try {
                            downloadRepository.downloadTrack(track)
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (e: IOException) {
                            android.util.Log.w("PlaylistTransfer", "downloadTrack failed", e)
                        }
                        info = downloadRepository.getDownloadInfo(track.id)
                    }
                    var audioFile: String? = null
                    var formatKey: String? = null
                    if (info != null) {
                        val ext = info.format.extension
                        audioFile = "audio/${NetworkUtils.sanitizeFileName(track.id)}.$ext"
                        formatKey = info.format.key
                        zip.putNextEntry(ZipEntry(audioFile))
                        openDownload(info.filePath).use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                    val coverFile = if (track.artUrl.isNotBlank()) {
                        coverPaths.getOrPut(track.albumId) {
                            val name = "covers/${NetworkUtils.sanitizeFileName(track.albumId)}.jpg"

                            // Safety net: network fetches throw IOException
                            // (and subtypes). Failure means we export the row
                            // without artwork.
                            val bytes = try {
                                fetchBytes(track.artUrl)
                            } catch (ce: CancellationException) {
                                throw ce
                            } catch (_: IOException) {
                                null
                            }
                            if (bytes != null) {
                                zip.putNextEntry(ZipEntry(name))
                                zip.write(bytes)
                                zip.closeEntry()
                                name
                            } else {
                                "" // mark attempted-but-missing so we don't refetch
                            }
                        }.takeIf { it.isNotEmpty() }
                    } else {
                        null
                    }
                    entries.add(BundleEntry(track.toSnapshot(), audioFile, coverFile, formatKey))
                } else {
                    entries.add(BundleEntry(track.toSnapshot()))
                }
                onProgress(index + 1, total)
            }

            val manifest = PlaylistBundleManifest(
                offline = offline,
                playlist = playlist.toSnapshot(),
                entries = entries,
            )
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(json.encodeToString(manifest).toByteArray())
            zip.closeEntry()
        }
    }

    /**
     * Read a `.dvplaylist` ZIP from [inp] and create a new playlist. Returns the created playlist.
     *
     * Streams the archive entry-by-entry: only `manifest.json` and small
     * metadata/cover entries are buffered in memory (each capped at
     * [MAX_METADATA_ENTRY_BYTES]); audio entries are spilled straight to a
     * temp directory and moved into place after the manifest is parsed, so an
     * offline bundle of any size imports without OOM.
     *
     * Playlist + tracks + membership (+ download rows) land in one Room
     * transaction. Filesystem writes happen first; on DB failure the playlist
     * is rolled back by the transaction and newly written media is cleaned
     * best-effort.
     */
    suspend fun import(inp: InputStream, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): Playlist = withContext(ioDispatcher) {
        var tempDir: File? = null
        fun tempDirOrCreate(): File = tempDir ?: File(context.cacheDir, "playlist_import_${System.nanoTime()}")
            .also {
                if (!it.mkdirs() && !it.isDirectory) {
                    throw IllegalStateException("Cannot create import temp dir: ${it.absolutePath}")
                }
                tempDir = it
            }

        val writtenFiles = ArrayList<File>()
        try {
            val smallFiles = HashMap<String, ByteArray>() // covers + misc small metadata
            val audioFiles = HashMap<String, File>() // zip entry name -> spilled temp file
            var manifestJson: String? = null
            // .dvplaylist is a share-with-strangers format: bound the total
            // entry count, the aggregate in-memory metadata bytes, and the
            // audio bytes spilled to cache so a hostile archive can neither
            // OOM the app nor fill the disk. Each violation throws so the
            // finally-block temp cleanup runs.
            val budget = ImportBudget(
                maxSpillBytes = (spillCeilingBytes() - SPILL_FREE_MARGIN_BYTES).coerceAtLeast(0L),
            )
            ZipInputStream(inp.buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    coroutineContext.ensureActive()
                    budget.entries++
                    if (budget.entries > MAX_BUNDLE_ENTRIES) {
                        throw IllegalStateException("Bundle has more than $MAX_BUNDLE_ENTRIES entries; refusing to import")
                    }
                    readBundleEntry(zip, entry, smallFiles, audioFiles, ::tempDirOrCreate, budget) { manifestJson = it }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            val manifest = json.decodeFromString<PlaylistBundleManifest>(
                manifestJson ?: throw IllegalStateException("Bundle missing manifest.json"),
            )
            if (manifest.version > PlaylistBundleManifest.SUPPORTED_VERSION) {
                throw IllegalStateException(
                    "Bundle format version ${manifest.version} is newer than this app supports " +
                        "(<= ${PlaylistBundleManifest.SUPPORTED_VERSION}); update the app to import it",
                )
            }

            val total = manifest.entries.size
            val materialized = ArrayList<MaterializedEntry>(total)
            manifest.entries.forEachIndexed { index, bundleEntry ->
                coroutineContext.ensureActive()
                materialized.add(
                    materializeEntry(manifest.offline, bundleEntry, audioFiles, smallFiles, writtenFiles),
                )
                onProgress(index + 1, total)
            }

            val sanitizedIcon = sanitizeRemoteOrBlankUrl(manifest.playlist.iconUrl)
            try {
                database.withTransaction {
                    val playlist = playlistRepository.createPlaylist(
                        name = manifest.playlist.name,
                        shapeKey = manifest.playlist.shapeKey,
                        iconUrl = sanitizedIcon,
                    )
                    val incoming = materialized.map { it.track.toEntity() }
                    trackDao.insertAll(mergeTracksForImport(incoming))
                    playlistRepository.addTracksToPlaylist(playlist.id, manifest.entries.map { it.track.id })
                    for (entry in materialized) {
                        val download = entry.download ?: continue
                        downloadDao.insert(download)
                    }
                    playlist
                }
            } catch (ce: CancellationException) {
                cleanupWrittenFiles(writtenFiles)
                throw ce
            } catch (e: Exception) {
                cleanupWrittenFiles(writtenFiles)
                throw e
            }
        } finally {
            try {
                tempDir?.deleteRecursively()
            } catch (_: SecurityException) {
                // Leftover temp files are reclaimed by the OS cache cleaner.
            }
        }
    }

    /**
     * Soft upsert: keep an existing non-blank streamUrl/artUrl when the bundle
     * would wipe them with blanks. Bundle non-blank values always win (offline
     * covers repoint artUrl at a local file).
     */
    private suspend fun mergeTracksForImport(incoming: List<TrackEntity>): List<TrackEntity> {
        if (incoming.isEmpty()) return incoming
        val existingById = trackDao.getByIds(incoming.map { it.id }).associateBy { it.id }
        return incoming.map { row ->
            val existing = existingById[row.id] ?: return@map row
            row.copy(
                streamUrl = row.streamUrl?.takeIf { it.isNotBlank() } ?: existing.streamUrl,
                artUrl = row.artUrl.takeIf { it.isNotBlank() } ?: existing.artUrl,
            )
        }
    }

    private fun cleanupWrittenFiles(files: List<File>) {
        for (file in files) {
            try {
                if (file.exists()) file.delete()
            } catch (_: SecurityException) {
                // Best-effort filesystem rollback after a failed import txn.
            }
        }
    }

    private data class MaterializedEntry(
        val track: TrackSnapshot,
        val download: DownloadEntity?,
    )

    /**
     * Dispatches one ZIP entry: records the manifest text via [onManifest],
     * spills audio payloads to index-named temp files under [tempDirOrCreate],
     * keeps small metadata in memory, and skips oversized metadata entries.
     */
    private fun readBundleEntry(
        zip: ZipInputStream,
        entry: ZipEntry,
        smallFiles: MutableMap<String, ByteArray>,
        audioFiles: MutableMap<String, File>,
        tempDirOrCreate: () -> File,
        budget: ImportBudget,
        onManifest: (String) -> Unit,
    ) {
        val name = entry.name
        when {
            entry.isDirectory -> Unit

            name == "manifest.json" -> onManifest(
                readEntryCapped(zip)?.decodeToString()
                    ?: throw IllegalStateException("manifest.json exceeds $MAX_METADATA_ENTRY_BYTES bytes"),
            )

            name.startsWith("audio/") -> {
                // Temp names are index-based - zip entry names are used only as
                // map keys, never as filesystem paths.
                val spilled = File(tempDirOrCreate(), "audio_${audioFiles.size}")
                spilled.outputStream().use { out ->
                    budget.spilledBytes += copyCounted(zip, out, budget.maxSpillBytes - budget.spilledBytes)
                }
                audioFiles[name] = spilled
            }

            else -> {
                val bytes = readEntryCapped(zip)
                if (bytes != null) {
                    // The per-entry cap alone would let thousands of
                    // highly-compressible 8 MB entries inflate to multi-GB of
                    // retained ByteArrays; bound the aggregate too.
                    budget.metadataBytes += bytes.size
                    if (budget.metadataBytes > MAX_METADATA_TOTAL_BYTES) {
                        throw IllegalStateException(
                            "Bundle metadata exceeds $MAX_METADATA_TOTAL_BYTES bytes total; refusing to import",
                        )
                    }
                    smallFiles[name] = bytes
                } else {
                    android.util.Log.w("PlaylistTransfer", "Skipping oversized metadata entry: $name")
                }
            }
        }
    }

    /**
     * Copies [input] to [out], throwing once more than [remaining] bytes have
     * been written - the uncompressed size is attacker-controlled, so the disk
     * budget must be enforced on actual bytes, never on ZipEntry.size.
     */
    private fun copyCounted(input: InputStream, out: OutputStream, remaining: Long): Long {
        if (remaining <= 0L) {
            throw IllegalStateException("Bundle audio exceeds the available cache space; refusing to import")
        }
        val buffer = ByteArray(DEFAULT_COPY_BUFFER_BYTES)
        var copied = 0L
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            copied += read
            if (copied > remaining) {
                throw IllegalStateException("Bundle audio exceeds the available cache space; refusing to import")
            }
            out.write(buffer, 0, read)
        }
        return copied
    }

    /**
     * Space available for spilling audio entries into the cache dir. Consults
     * StorageManager.getAllocatableBytes alongside File.usableSpace (per lint's
     * UsableSpace guidance) but takes the MINIMUM of the two: this budget is a
     * hostile-archive bound, and we never call allocateBytes to actually clear
     * other apps' caches, so the conservative measure is the honest one.
     */
    private fun spillCeilingBytes(): Long {
        val usable = context.cacheDir.usableSpace
        return try {
            val storageManager = context.getSystemService(StorageManager::class.java) ?: return usable
            minOf(usable, storageManager.getAllocatableBytes(storageManager.getUuidForPath(context.cacheDir)))
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: IOException) {
            usable
        } catch (_: RuntimeException) {
            // Best-effort probe: Robolectric/mocks can ClassCast getSystemService
            // results or NPE inside getUuidForPath when volumes are unset.
            usable
        }
    }

    /** Mutable running totals for one import; see the caps in the companion. */
    private class ImportBudget(val maxSpillBytes: Long) {
        var entries = 0
        var metadataBytes = 0L
        var spilledBytes = 0L
    }

    /**
     * Turns one bundle entry into the [TrackSnapshot] to persist. For offline
     * bundles it moves the spilled audio into the downloads layout (registering
     * a pinned [DownloadEntity] unless a pinned download already exists) and
     * writes the cover locally, repointing artUrl at it. Lightweight bundles
     * return the snapshot unchanged.
     */
    private suspend fun materializeEntry(
        offline: Boolean,
        bundleEntry: BundleEntry,
        audioFiles: Map<String, File>,
        smallFiles: Map<String, ByteArray>,
        writtenFiles: MutableList<File>,
    ): MaterializedEntry {
        // Never persist a local-scheme artUrl from a foreign bundle: a hostile
        // file:// value would later be dereferenced by export's fetchBytes()
        // and embed an app-private file into a shared archive. Local-scheme
        // values from the exporting device are dangling here anyway; offline
        // bundles get artUrl repointed at the locally-written cover below.
        var snap = bundleEntry.track.let {
            if (isRemoteOrBlankUrl(it.artUrl)) it else it.copy(artUrl = "")
        }
        if (!offline) return MaterializedEntry(snap, download = null)

        var download: DownloadEntity? = null
        val audioTemp = bundleEntry.audioFile?.let { audioFiles[it] }
        if (audioTemp != null && audioTemp.isFile) {
            val existing = downloadDao.getByTrackId(snap.id)
            if (existing?.pinned == true) {
                // Keep the user's pinned download; do not REPLACE the row or
                // overwrite the audio file (old path would be orphaned).
            } else {
                val format = bundleEntry.format?.let { AudioFormat.fromKey(it) } ?: AudioFormat.MP3_128
                val safeAlbum = NetworkUtils.sanitizeFileName(snap.albumId)
                val safeTrack = NetworkUtils.sanitizeFileName(snap.id)
                val dir = File(StoragePaths.downloadsDir(context), safeAlbum).also { it.mkdirs() }
                val audio = File(dir, "$safeTrack.${format.extension}")
                moveFile(audioTemp, audio)
                writtenFiles.add(audio)
                download = DownloadEntity(
                    trackId = snap.id,
                    albumId = snap.albumId,
                    filePath = audio.absolutePath,
                    sizeBytes = audio.length(),
                    format = format.key,
                    pinned = true,
                )
            }
        }
        // Persist the cover locally and point artUrl at it so covers show offline.
        val coverBytes = bundleEntry.coverFile?.let { smallFiles[it] }
        if (coverBytes != null) {
            val cover = File(StoragePaths.imagesDir(context), "${NetworkUtils.sanitizeFileName(snap.albumId)}.jpg")
            cover.writeBytes(coverBytes)
            writtenFiles.add(cover)
            snap = snap.copy(artUrl = Uri.fromFile(cover).toString())
        }
        return MaterializedEntry(snap, download)
    }

    /**
     * Reads the current ZIP entry fully into memory, or returns null once it
     * exceeds [MAX_METADATA_ENTRY_BYTES] (the caller then skips it; ZipInputStream
     * discards the remainder on closeEntry). Never trusts ZipEntry.size - it
     * is attacker-controlled and often -1 for streamed archives.
     */
    private fun readEntryCapped(zip: ZipInputStream): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_COPY_BUFFER_BYTES)
        while (true) {
            val read = zip.read(buffer)
            if (read == -1) break
            if (out.size() + read > MAX_METADATA_ENTRY_BYTES) return null
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun moveFile(src: File, dest: File) {
        if (dest.exists()) dest.delete()
        if (!src.renameTo(dest)) {
            src.copyTo(dest, overwrite = true)
            src.delete()
        }
    }

    /** Downloads always live in app-private storage, so the path is a plain file. */
    private fun openDownload(filePath: String): InputStream = File(filePath).inputStream()

    private fun fetchBytes(url: String): ByteArray {
        if (url.startsWith("content://") || url.startsWith("file://")) {
            return readLocalBytes(url)
        }
        val request = Request.Builder().url(url).build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body.bytes()
        }
    }

    private fun readLocalBytes(url: String): ByteArray {
        val uri = url.toUri()
        val stream = if (url.startsWith("file://")) {
            // Defense in depth against a poisoned DB row: only files the app
            // itself wrote (downloads tree, which contains imagesDir) may be
            // embedded into an exported bundle.
            val file = File(requireNotNull(uri.path) { "Bad file URI: $url" }).canonicalFile
            val downloadsRoot = StoragePaths.downloadsDir(context).canonicalFile
            require(file.path.startsWith(downloadsRoot.path + File.separator)) {
                "Refusing to embed $url: outside the app's downloads tree"
            }
            file.inputStream()
        } else {
            requireNotNull(context.contentResolver.openInputStream(uri)) { "Cannot open $url" }
        }
        return stream.use { it.readBytes() }
    }

    /** True for http(s) or blank artUrl values; local schemes are rejected on import. */
    private fun isRemoteOrBlankUrl(url: String): Boolean = url.isBlank() || url.startsWith("http://") || url.startsWith("https://")

    /**
     * Same remote/blank policy as artUrl for playlist iconUrl: reject local
     * schemes from a foreign bundle (null out); keep http(s) or blank-as-null.
     */
    private fun sanitizeRemoteOrBlankUrl(url: String?): String? {
        if (url == null) return null
        if (!isRemoteOrBlankUrl(url)) return null
        return url.takeIf { it.isNotBlank() }
    }

    private companion object {
        /**
         * Per-entry in-memory cap for manifest/cover/metadata entries. Audio
         * never counts against this - it streams to disk. 8 MB comfortably
         * fits any real manifest or cover while bounding a hostile bundle.
         */
        const val MAX_METADATA_ENTRY_BYTES = 8 * 1024 * 1024

        /** Aggregate cap for all in-memory metadata entries of one bundle. */
        const val MAX_METADATA_TOTAL_BYTES = 64L * 1024 * 1024

        /** Total zip entries a bundle may contain (tracks + covers + manifest). */
        const val MAX_BUNDLE_ENTRIES = 10_000

        /** Cache headroom that spilled audio must leave untouched. */
        const val SPILL_FREE_MARGIN_BYTES = 200L * 1024 * 1024

        const val DEFAULT_COPY_BUFFER_BYTES = 8192
    }
}

private fun Track.toSnapshot() = TrackSnapshot(
    id = id,
    albumId = albumId,
    title = title,
    artist = artist,
    artistUrl = artistUrl,
    trackNumber = trackNumber,
    duration = duration,
    streamUrl = streamUrl,
    artUrl = artUrl,
    albumTitle = albumTitle,
    source = source.key,
    folderUri = folderUri,
    dateAdded = dateAdded,
    year = year,
    albumUrl = albumUrl,
    bandcampTrackUrl = bandcampTrackUrl,
)

private fun Playlist.toSnapshot() = PlaylistSnapshot(
    id = id,
    name = name,
    iconUrl = iconUrl,
    shapeKey = shapeKey,
    isSystem = false,
    systemType = null,
    isPinned = false,
    sortOrder = sortOrder,
    trackCount = trackCount,
    autoDownload = autoDownload,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
