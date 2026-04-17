package com.dustvalve.next.android.cache

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class DiskCacheManagerTest {

    private lateinit var manager: DiskCacheManager
    private lateinit var cacheDir: File
    private lateinit var filesDir: File

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        cacheDir = ctx.cacheDir
        filesDir = ctx.filesDir
        // Clean up anything from a prior run
        File(cacheDir, "cache").deleteRecursively()
        File(filesDir, "downloads").deleteRecursively()
        manager = DiskCacheManager(ctx)
    }

    @Test fun `writeAudioCache writes bytes and returns file`() {
        val data = byteArrayOf(1, 2, 3, 4)
        val file = manager.writeAudioCache("track1", data)
        assertThat(file.exists()).isTrue()
        assertThat(file.readBytes()).isEqualTo(data)
        assertThat(file.name).isEqualTo("track1.mp3")
    }

    @Test fun `writeAudioCache sanitizes unsafe key`() {
        val file = manager.writeAudioCache("foo/bar baz", byteArrayOf(1))
        assertThat(file.name).isEqualTo("foo_bar_baz.mp3")
    }

    @Test fun `writeImageCache uses webp extension`() {
        val file = manager.writeImageCache("img1", byteArrayOf(9))
        assertThat(file.name).endsWith(".webp")
    }

    @Test fun `readFile inside cacheDir returns file`() {
        val f = manager.writeAudioCache("x", byteArrayOf(1))
        assertThat(manager.readFile(f.absolutePath)).isNotNull()
    }

    @Test fun `readFile outside cacheDir rejected`() {
        val outside = File("/etc/passwd")
        assertThat(manager.readFile(outside.absolutePath)).isNull()
    }

    @Test fun `deleteFile inside cacheDir removes`() {
        val f = manager.writeAudioCache("x", byteArrayOf(1))
        assertThat(manager.deleteFile(f.absolutePath)).isTrue()
        assertThat(f.exists()).isFalse()
    }

    @Test fun `deleteFile missing returns true`() {
        // Missing file inside cacheDir is still reported as success
        val path = File(cacheDir, "cache/audio/missing.mp3").absolutePath
        assertThat(manager.deleteFile(path)).isTrue()
    }

    @Test fun `deleteFile outside cacheDir rejected`() {
        assertThat(manager.deleteFile("/etc/shadow")).isFalse()
    }

    @Test fun `calculateDirSize sums recursively`() {
        manager.writeAudioCache("a", ByteArray(10))
        manager.writeAudioCache("b", ByteArray(25))
        manager.writeImageCache("c", ByteArray(7))
        val size = manager.calculateDirSize(manager.getCacheDir())
        assertThat(size).isEqualTo(42L)
    }

    @Test fun `calculateDirSize on missing dir is zero`() {
        val missing = File(cacheDir, "does-not-exist")
        assertThat(manager.calculateDirSize(missing)).isEqualTo(0L)
    }

    @Test fun `clearCacheDir wipes contents and recreates subdirs`() {
        manager.writeAudioCache("a", ByteArray(3))
        manager.writeImageCache("b", ByteArray(3))
        // Also create an unrelated file under media_cache to prove it is NOT deleted
        val mediaCache = manager.getMediaCacheDir().apply { mkdirs() }
        val mediaFile = File(mediaCache, "keep.bin").apply { writeBytes(ByteArray(10)) }

        manager.clearCacheDir()

        assertThat(manager.calculateDirSize(manager.getCacheDir())).isEqualTo(0L)
        assertThat(File(cacheDir, "cache/audio").isDirectory).isTrue()
        assertThat(File(cacheDir, "cache/images/album").isDirectory).isTrue()
        assertThat(File(cacheDir, "cache/images/artist").isDirectory).isTrue()
        assertThat(mediaFile.exists()).isTrue()
    }
}
