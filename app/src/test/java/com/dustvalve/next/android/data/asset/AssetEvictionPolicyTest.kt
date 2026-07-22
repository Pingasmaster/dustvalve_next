package com.dustvalve.next.android.data.asset

import androidx.test.core.app.ApplicationProvider
import com.dustvalve.next.android.data.local.db.DbTestBase
import com.dustvalve.next.android.data.local.db.entity.DownloadEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class AssetEvictionPolicyTest : DbTestBase() {

    @get:Rule val tmp = TemporaryFolder()

    private fun policy() = AssetEvictionPolicy(
        ApplicationProvider.getApplicationContext(),
        db,
        db.downloadDao(),
    )

    private suspend fun insert(
        trackId: String,
        sizeBytes: Long,
        pinned: Boolean,
        lastAccessed: Long,
    ): File {
        val file = tmp.newFile("$trackId.mp3")
        db.downloadDao().insert(
            DownloadEntity(
                trackId = trackId,
                albumId = "al",
                filePath = file.absolutePath,
                sizeBytes = sizeBytes,
                downloadedAt = lastAccessed,
                pinned = pinned,
                lastAccessed = lastAccessed,
            ),
        )
        return file
    }

    @Test fun `evicts oldest unpinned entries until the target is freed`() = runTest {
        val cold = insert("cold", sizeBytes = 100, pinned = false, lastAccessed = 1)
        val warm = insert("warm", sizeBytes = 100, pinned = false, lastAccessed = 2)
        val hot = insert("hot", sizeBytes = 100, pinned = false, lastAccessed = 3)

        policy().evict(targetBytes = 150)

        val remaining = db.downloadDao().getAllSync().map { it.trackId }
        assertThat(remaining).containsExactly("hot")
        assertThat(cold.exists()).isFalse()
        assertThat(warm.exists()).isFalse()
        assertThat(hot.exists()).isTrue()
    }

    @Test fun `never evicts pinned downloads even when the target is not met`() = runTest {
        val pinned = insert("pinned", sizeBytes = 1000, pinned = true, lastAccessed = 1)
        val unpinned = insert("unpinned", sizeBytes = 10, pinned = false, lastAccessed = 2)

        policy().evict(targetBytes = 500)

        val remaining = db.downloadDao().getAllSync().map { it.trackId }
        assertThat(remaining).containsExactly("pinned")
        assertThat(pinned.exists()).isTrue()
        assertThat(unpinned.exists()).isFalse()
    }

    @Test fun `zero or negative target is a no-op`() = runTest {
        insert("a", sizeBytes = 100, pinned = false, lastAccessed = 1)
        policy().evict(targetBytes = 0)
        policy().evict(targetBytes = -5)
        assertThat(db.downloadDao().getAllSync()).hasSize(1)
    }

    @Test fun `empty pool is a no-op`() = runTest {
        policy().evict(targetBytes = 100)
        assertThat(db.downloadDao().getAllSync()).isEmpty()
    }

    @Test fun `content uri file path is handled without aborting eviction`() = runTest {
        // Dedicated-folder mode stores content:// URIs in filePath;
        // File(path).delete() is a silent no-op for those, and the
        // DocumentFile path must not crash even when the provider is gone.
        db.downloadDao().insert(
            DownloadEntity(
                trackId = "saf",
                albumId = "al",
                filePath = "content://com.example.provider/tree/root/document/root%2Fsaf.mp3",
                sizeBytes = 100,
                downloadedAt = 1,
                pinned = false,
                lastAccessed = 1,
            ),
        )
        insert("local", sizeBytes = 100, pinned = false, lastAccessed = 2)

        policy().evict(targetBytes = 200)

        assertThat(db.downloadDao().getAllSync()).isEmpty()
    }

    @Test fun `missing file does not abort eviction of remaining entries`() = runTest {
        val ghost = insert("ghost", sizeBytes = 100, pinned = false, lastAccessed = 1)
        ghost.delete()
        insert("real", sizeBytes = 100, pinned = false, lastAccessed = 2)

        policy().evict(targetBytes = 200)

        assertThat(db.downloadDao().getAllSync()).isEmpty()
    }
}
