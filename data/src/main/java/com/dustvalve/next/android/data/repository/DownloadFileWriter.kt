package com.dustvalve.next.android.data.repository

import com.dustvalve.next.android.data.remote.DownloadPayloadValidator
import com.dustvalve.next.android.data.remote.RangeResumeDownloader
import com.dustvalve.next.android.domain.repository.DownloadProgressReporter
import com.dustvalve.next.android.download.isPauseCancellation
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Writes a resolved download URL into app-private storage via temp + rename,
 * with HTTP Range resume. Extracted from [DownloadRepositoryImpl] so that
 * class stays under the TooManyFunctions ceiling.
 */
internal class DownloadFileWriter(
    private val context: android.content.Context,
    private val downloadClient: OkHttpClient,
    private val notificationCenter: DownloadProgressReporter,
) {
    private val metaJson = Json { ignoreUnknownKeys = true }

    suspend fun write(safeAlbumId: String, provisionalFileName: String, downloadUrl: String, trackId: String): Pair<String, Long> {
        val downloadDir = requireDownloadDir(safeAlbumId)
        // Temp / meta keys use the provisional name so a paused transfer can
        // still resume; the final committed name may swap extension after
        // MIME/magic validation.
        val tempFile = File(downloadDir, "$provisionalFileName.tmp")
        val metaFile = File(downloadDir, "$provisionalFileName.tmp.meta")
        val identity = resumeSourceIdentity(downloadUrl)
        var suggestedExtension: String? = null

        // A leftover .tmp means a prior transfer was paused - resume from its
        // current length via an HTTP Range request (append mode) instead of
        // restarting from 0. Only append when the sidecar proves the partial
        // came from the same source variant; a freshly re-resolved URL (e.g.
        // a different YouTube itag) must restart from zero.
        val resume = prepareResumeState(tempFile, metaFile, identity)

        suspend fun transfer(offset: Long, expectedTotal: Long?) {
            var metaPersisted = false
            FileOutputStream(tempFile, offset > 0L).use { out ->
                val result = RangeResumeDownloader.stream(
                    client = downloadClient,
                    url = downloadUrl,
                    sink = out,
                    trackId = trackId,
                    startOffset = offset,
                    expectedTotalBytes = expectedTotal,
                    onProgress = { written, total ->
                        if (!metaPersisted && total != null && total > 0L) {
                            metaPersisted = true
                            writeResumeMeta(metaFile, DownloadRepositoryImpl.ResumeMeta(total, identity))
                        }
                        notificationCenter.trackProgress(trackId, written, total)
                    },
                )
                if (result.suggestedExtension != null) {
                    suggestedExtension = result.suggestedExtension
                }
            }
        }

        try {
            try {
                transfer(resume.resumeFrom, resume.knownTotal)
            } catch (e: RangeResumeDownloader.ResumeMismatchException) {
                // The server no longer serves the payload the partial came
                // from (offset or total drifted). Discard and restart clean -
                // once; a second mismatch propagates as a real failure.
                android.util.Log.w(
                    "DownloadRepo",
                    "Resume mismatch for $trackId; discarding partial and restarting from zero: ${e.message}",
                )
                tempFile.delete()
                metaFile.delete()
                transfer(0L, null)
            }
        } catch (e: CancellationException) {
            // Keep the partial (and its sidecar) on pause so resume can
            // continue; delete both on any real failure or cancel.
            if (!e.isPauseCancellation()) {
                tempFile.delete()
                metaFile.delete()
            }
            throw e
        } catch (e: IOException) {
            tempFile.delete()
            metaFile.delete()
            throw e
        }

        val fileName = resolveCommittedFileName(
            tempFile,
            metaFile,
            provisionalFileName,
            trackId,
            suggestedExtension,
        )
        val targetFile = File(downloadDir, fileName)
        commitTempToTarget(tempFile, metaFile, targetFile)
        return targetFile.absolutePath to targetFile.length()
    }

    private fun requireDownloadDir(safeAlbumId: String): File {
        val downloadDir = File(context.filesDir, "downloads/$safeAlbumId")
        if (!downloadDir.mkdirs() && !downloadDir.exists()) {
            throw IOException("Failed to create download directory: ${downloadDir.absolutePath}")
        }
        return downloadDir
    }

    private data class ResumeState(val resumeFrom: Long, val knownTotal: Long?)

    private fun prepareResumeState(tempFile: File, metaFile: File, identity: String): ResumeState {
        var resumeFrom = if (tempFile.exists()) tempFile.length() else 0L
        var knownTotal: Long? = null
        if (resumeFrom > 0L) {
            val meta = readResumeMeta(metaFile)
            if (meta == null || meta.sourceIdentity != identity) {
                tempFile.delete()
                metaFile.delete()
                resumeFrom = 0L
            } else {
                knownTotal = meta.expectedTotalBytes
            }
        }
        return ResumeState(resumeFrom, knownTotal)
    }

    private fun resolveCommittedFileName(
        tempFile: File,
        metaFile: File,
        provisionalFileName: String,
        trackId: String,
        suggestedExtension: String?,
    ): String {
        // Final sniff on the completed temp: catches resume paths that skipped
        // the from-zero header check, and confirms MIME-suggested extensions.
        val sniffBuf = ByteArray(SNIFF_HEADER_BYTES)
        val sniffed = tempFile.inputStream().use { input ->
            val n = input.read(sniffBuf)
            if (n <= 0) {
                tempFile.delete()
                metaFile.delete()
                throw DownloadPayloadValidator.InvalidPayloadException("Empty download for track: $trackId")
            }
            DownloadPayloadValidator.sniffExtensionOrReject(sniffBuf.copyOf(n), trackId)
        }
        val finalExtension = suggestedExtension ?: sniffed
            ?: provisionalFileName.substringAfterLast('.', missingDelimiterValue = "mp3")
        val baseName = provisionalFileName.substringBeforeLast('.', provisionalFileName)
        return "$baseName.$finalExtension"
    }

    private fun commitTempToTarget(tempFile: File, metaFile: File, targetFile: File) {
        metaFile.delete()
        if (!tempFile.renameTo(targetFile)) {
            try {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            } catch (e: IOException) {
                targetFile.delete()
                tempFile.delete()
                throw IOException("Failed to copy download to target: ${e.message}", e)
            }
        }
        if (!targetFile.exists() || targetFile.length() == 0L) {
            throw IOException("Failed to write download file: ${targetFile.absolutePath}")
        }
    }

    /**
     * Stable identity of the *content* behind a download URL, persisted in the
     * resume sidecar. googlevideo URLs rotate host/expiry/signature params on
     * every resolve but keep serving the same bytes for a given `itag`; for
     * everything else the URL minus its query is the best stable handle.
     */
    private fun resumeSourceIdentity(url: String): String {
        val httpUrl = url.toHttpUrlOrNull() ?: return url.substringBefore('?')
        val host = httpUrl.host
        return if (host == "googlevideo.com" || host.endsWith(".googlevideo.com")) {
            "itag=" + (httpUrl.queryParameter("itag") ?: "")
        } else {
            httpUrl.newBuilder().query(null).build().toString()
        }
    }

    private fun readResumeMeta(metaFile: File): DownloadRepositoryImpl.ResumeMeta? = try {
        if (metaFile.exists()) {
            metaJson.decodeFromString(DownloadRepositoryImpl.ResumeMeta.serializer(), metaFile.readText())
        } else {
            null
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: IOException) {
        null
    } catch (_: SerializationException) {
        null
    }

    private fun writeResumeMeta(metaFile: File, meta: DownloadRepositoryImpl.ResumeMeta) {
        try {
            metaFile.writeText(metaJson.encodeToString(DownloadRepositoryImpl.ResumeMeta.serializer(), meta))
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            // Best-effort: without a sidecar the next attempt simply restarts
            // from zero rather than risking a corrupt append.
        }
    }

    private companion object {
        const val SNIFF_HEADER_BYTES = 64
    }
}
