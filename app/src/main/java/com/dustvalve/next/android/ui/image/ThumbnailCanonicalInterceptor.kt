package com.dustvalve.next.android.ui.image

import android.net.Uri
import coil3.intercept.Interceptor
import coil3.request.ImageResult
import com.dustvalve.next.android.util.ThumbnailUrls

/**
 * Rewrites remote artwork URLs to their canonical full-quality form before
 * Coil keys / fetches them. Search rows, queue tiles, album heroes, and the
 * player therefore share one disk-cache entry and never re-download the same
 * cover at a different size token (Bandcamp `_N`, YouTube ladders / query
 * strings, SoundCloud `-large` vs `-t500x500`).
 */
class ThumbnailCanonicalInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val data = chain.request.data
        val url = when (data) {
            is String -> data
            is Uri -> data.toString()
            else -> null
        } ?: return chain.proceed()

        if (!url.startsWith("https://")) return chain.proceed()

        val canonical = ThumbnailUrls.canonicalize(url)
        if (canonical == url && data is String) return chain.proceed()

        return chain.withRequest(
            chain.request.newBuilder().data(canonical).build(),
        ).proceed()
    }
}
