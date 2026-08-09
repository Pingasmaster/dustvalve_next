package com.dustvalve.next.android.data.remote

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class DownloadPayloadValidatorTest {

    @Test fun `rejects HTML JSON XML and HLS content types`() {
        listOf(
            "text/html",
            "text/html; charset=utf-8",
            "application/json",
            "application/xml",
            "text/xml",
            "application/soap+xml",
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

    @Test fun `sniffs known audio magic and rejects HTML XML JSON text`() {
        assertThat(DownloadPayloadValidator.sniffExtensionOrReject("ID3....".toByteArray(), "t1"))
            .isEqualTo("mp3")
        assertThat(DownloadPayloadValidator.sniffExtensionOrReject("fLaC....".toByteArray(), "t1"))
            .isEqualTo("flac")
        assertThrows(DownloadPayloadValidator.InvalidPayloadException::class.java) {
            DownloadPayloadValidator.sniffExtensionOrReject("<!DOCTYPE html>".toByteArray(), "t1")
        }
        assertThrows(DownloadPayloadValidator.InvalidPayloadException::class.java) {
            DownloadPayloadValidator.sniffExtensionOrReject("<?xml version=\"1.0\"?><error/>".toByteArray(), "t1")
        }
        assertThrows(DownloadPayloadValidator.InvalidPayloadException::class.java) {
            DownloadPayloadValidator.sniffExtensionOrReject("#EXTM3U\n".toByteArray(), "t1")
        }
        assertThrows(DownloadPayloadValidator.InvalidPayloadException::class.java) {
            DownloadPayloadValidator.sniffExtensionOrReject("""{"error":1}""".toByteArray(), "t1")
        }
        assertThrows(DownloadPayloadValidator.InvalidPayloadException::class.java) {
            DownloadPayloadValidator.sniffExtensionOrReject("Access denied by CDN gateway".toByteArray(), "t1")
        }
    }

    @Test fun `binary unknown magic is allowed through for extension fallback`() {
        // High-entropy / non-text bytes with no known container magic: null
        // (caller may still use the stream format's default extension).
        val binary = byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x7F, 0x80.toByte(), 0xFE.toByte(), 0xFF.toByte())
        assertThat(DownloadPayloadValidator.sniffExtensionOrReject(binary, "t1")).isNull()
    }
}
