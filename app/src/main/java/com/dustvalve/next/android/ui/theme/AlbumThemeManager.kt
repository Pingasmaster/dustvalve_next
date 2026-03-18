package com.dustvalve.next.android.ui.theme

import android.content.Context
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.player.QueueManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlbumThemeManager @Inject constructor(
    private val queueManager: QueueManager,
    private val settingsDataStore: SettingsDataStore,
    @param:ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val seedCache = LruCache<String, Int>(20)

    val albumSeedColor: StateFlow<Color?> = combine(
        queueManager.currentTrack,
        settingsDataStore.albumArtTheme,
    ) { track, enabled ->
        if (!enabled || track == null || track.artUrl.isBlank()) {
            null
        } else {
            extractSeedColor(track.artUrl)
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    private suspend fun extractSeedColor(artUrl: String): Color? {
        seedCache.get(artUrl)?.let { return Color(it) }

        return withContext(Dispatchers.IO) {
            try {
                val imageLoader = SingletonImageLoader.get(context)
                val request = ImageRequest.Builder(context)
                    .data(artUrl)
                    .size(Size(128, 128))
                    .allowHardware(false)
                    .build()
                val result = imageLoader.execute(request)
                if (result !is SuccessResult) return@withContext null

                val bitmap = result.image.toBitmap()
                val palette = Palette.from(bitmap).generate()
                val swatch = palette.vibrantSwatch
                    ?: palette.dominantSwatch
                    ?: palette.mutedSwatch
                    ?: return@withContext null

                seedCache.put(artUrl, swatch.rgb)
                Color(swatch.rgb)
            } catch (_: Exception) {
                null
            }
        }
    }
}
