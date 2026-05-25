package com.dustvalve.next.android.data.transfer

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.dustvalve.next.android.data.asset.StoragePaths
import com.dustvalve.next.android.data.local.db.dao.DownloadDao
import com.dustvalve.next.android.data.local.db.dao.TrackDao
import com.dustvalve.next.android.data.local.db.entity.DownloadEntity
import com.dustvalve.next.android.data.storage.folder.FolderSnapshotSerializer
import com.dustvalve.next.android.data.storage.folder.PlaylistSnapshot
import com.dustvalve.next.android.data.storage.folder.TrackSnapshot
import com.dustvalve.next.android.data.storage.folder.toEntity
import com.dustvalve.next.android.domain.model.AudioFormat
import com.dustvalve.next.android.domain.model.Playlist
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.repository.DownloadRepository
import com.dustvalve.next.android.domain.repository.PlaylistRepository
import com.dustvalve.next.android.util.NetworkUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
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
class PlaylistTransferRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val playlistRepository: PlaylistRepository,
    private val downloadRepository: DownloadRepository,
    private val trackDao: TrackDao,
    private val downloadDao: DownloadDao,
    private val client: OkHttpClient,
) {
    private val json = FolderSnapshotSerializer.json

    /** Write [playlistId] to [out] as a `.dvplaylist` ZIP. [onProgress] reports (done, total). */
    suspend fun export(
        playlistId: String,
        offline: Boolean,
        out: OutputStream,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ) = withContext(Dispatchers.IO) {
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
                        // Safety net: download paths can throw IOException,
                        // SQLiteException, HttpException, or wrapped variants —
                        // any failure is logged and the track is exported
                        // without local audio. CancellationException is
                        // rethrown so coroutine cancellation propagates.
                        @Suppress("TooGenericExceptionCaught")
                        try {
                            downloadRepository.downloadTrack(track)
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (t: Throwable) {
                            android.util.Log.w("PlaylistTransfer", "downloadTrack failed", t)
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

                            // Safety net: network fetches throw IOException,
                            // SocketTimeout, HttpException, etc. — any failure
                            // means we export the row without artwork.
                            @Suppress("TooGenericExceptionCaught")
                            val bytes = try {
                                fetchBytes(track.artUrl)
                            } catch (ce: CancellationException) {
                                throw ce
                            } catch (_: Throwable) {
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

    /** Read a `.dvplaylist` ZIP from [inp] and create a new playlist. Returns the created playlist. */
    suspend fun import(inp: InputStream, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): Playlist =
        withContext(Dispatchers.IO) {
            val files = HashMap<String, ByteArray>()
            var manifestJson: String? = null
            ZipInputStream(inp.buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val bytes = zip.readBytes()
                    if (entry.name == "manifest.json") {
                        manifestJson = bytes.decodeToString()
                    } else {
                        files[entry.name] = bytes
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            val manifest = json.decodeFromString<PlaylistBundleManifest>(
                manifestJson ?: throw IllegalStateException("Bundle missing manifest.json"),
            )

            val playlist = playlistRepository.createPlaylist(
                name = manifest.playlist.name,
                shapeKey = manifest.playlist.shapeKey,
                iconUrl = manifest.playlist.iconUrl,
            )
            val total = manifest.entries.size
            val trackEntities = ArrayList<com.dustvalve.next.android.data.local.db.entity.TrackEntity>(total)

            manifest.entries.forEachIndexed { index, bundleEntry ->
                coroutineContext.ensureActive()
                var snap = bundleEntry.track
                if (manifest.offline) {
                    val audioBytes = bundleEntry.audioFile?.let { files[it] }
                    if (audioBytes != null) {
                        val format = bundleEntry.format?.let { AudioFormat.fromKey(it) } ?: AudioFormat.MP3_128
                        val safeAlbum = NetworkUtils.sanitizeFileName(snap.albumId)
                        val safeTrack = NetworkUtils.sanitizeFileName(snap.id)
                        val dir = File(StoragePaths.downloadsDir(context), safeAlbum).also { it.mkdirs() }
                        val audio = File(dir, "$safeTrack.${format.extension}")
                        audio.writeBytes(audioBytes)
                        downloadDao.insert(
                            DownloadEntity(
                                trackId = snap.id,
                                albumId = snap.albumId,
                                filePath = audio.absolutePath,
                                sizeBytes = audio.length(),
                                format = format.key,
                                pinned = true,
                            ),
                        )
                    }
                    // Persist the cover locally and point artUrl at it so covers show offline.
                    val coverBytes = bundleEntry.coverFile?.let { files[it] }
                    if (coverBytes != null) {
                        val cover = File(StoragePaths.imagesDir(context), "${NetworkUtils.sanitizeFileName(snap.albumId)}.jpg")
                        cover.writeBytes(coverBytes)
                        snap = snap.copy(artUrl = Uri.fromFile(cover).toString())
                    }
                }
                trackEntities.add(snap.toEntity())
                onProgress(index + 1, total)
            }

            trackDao.insertAll(trackEntities)
            playlistRepository.addTracksToPlaylist(playlist.id, manifest.entries.map { it.track.id })
            playlist
        }

    private fun openDownload(filePath: String): InputStream = if (filePath.startsWith("content://")) {
        context.contentResolver.openInputStream(filePath.toUri())
            ?: throw IllegalStateException("Cannot open $filePath")
    } else {
        File(filePath).inputStream()
    }

    private fun fetchBytes(url: String): ByteArray {
        val request = Request.Builder().url(url).build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
            response.body.bytes()
        }
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
