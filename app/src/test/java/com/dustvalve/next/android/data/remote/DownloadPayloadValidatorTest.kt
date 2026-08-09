package com.dustvalve.next.android.data.remote

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class DownloadPayloadValidatorTest {

    @Test fun `rejects HTML JSON and HLS content types`() {
        listOf(
            "text/html",
            "text/html; charset=utf-8",
            "application/json",
            "application/vnd.apple.mpegurl",
            "audio/mpegurl",
            "text/plain",
        ).forEach { mime ->
            assertThrows(DownloadPayloadValidator.InvalidPayloadException::class.java) {
                DownloadPayloadValidator.assertAcceptableContentType(mime, "t1")
            }
        }
    }

    @Test fun `accepts audio and common container types`() {
        listOf(
            "audio/mpeg",
            "audio/flac",
            "application/ogg",
            "video/webm",
            "application/octet-stream",
            null,
            "",
        ).forEach { mime ->
            DownloadPayloadValidator.assertAcceptableContentType(mime, "t1")
        }
    }

    @Test fun `maps mime to extension`() {
        assertThat(DownloadPayloadValidator.extensionForMime("audio/mpeg")).isEqualTo("mp3")
        assertThat(DownloadPayloadValidator.extensionForMime("audio/flac; charset=binary")).isEqualTo("flac")
        assertThat(DownloadPayloadValidator.extensionForMime("application/octet-stream")).isNull()
    }

    @Test fun `sniffs known audio magic and rejects HTML`() {
        assertThat(DownloadPayloadValidator.sniffExtensionOrReject("ID3....".toByteArray(), "t1"))
            .isEqualTo("mp3")
        assertThat(DownloadPayloadValidator.sniffExtensionOrReject("fLaC....".toByteArray(), "t1"))
            .isEqualTo("flac")
        assertThrows(DownloadPayloadValidator.InvalidPayloadException::class.java) {
            DownloadPayloadValidator.sniffExtensionOrReject("<!DOCTYPE html>".toByteArray(), "t1")
        }
        assertThrows(DownloadPayloadValidator.InvalidPayloadException::class.java) {
            DownloadPayloadValidator.sniffExtensionOrReject("#EXTM3U\n".toByteArray(), "t1")
        }
        assertThrows(DownloadPayloadValidator.InvalidPayloadException::class.java) {
            DownloadPayloadValidator.sniffExtensionOrReject("""{"error":1}""".toByteArray(), "t1")
        }
    }
}
