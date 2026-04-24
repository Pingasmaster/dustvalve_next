package com.dustvalve.next.android.di

import com.dustvalve.next.android.data.remote.CookieStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    /**
     * Matches [com.dustvalve.next.android.data.remote.youtube.innertube.YouTubeClient.ANDROID_VR_NO_AUTH.userAgent].
     * Kept as a duplicate constant (rather than a cross-module import) so this
     * low-level network module has no dependency on the YouTube innertube
     * layer — it only needs the UA string for host-based routing.
     */
    private const val ANDROID_VR_USER_AGENT =
        "com.google.android.apps.youtube.vr.oculus/1.61.48 " +
            "(Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
    private const val RATE_LIMIT_INTERVAL_MS = 500L
    private const val MAX_RETRIES = 3
    private const val INITIAL_BACKOFF_MS = 1000L

    private val rateLimitLock = Any()
    private var lastRequestTime = 0L

    @Provides
    @Singleton
    fun provideOkHttpClient(cookieStore: CookieStore): OkHttpClient {
        return OkHttpClient.Builder()
            .cookieJar(cookieStore)
            .addInterceptor(userAgentInterceptor())
            .addInterceptor(rateLimitInterceptor())
            .addInterceptor(retryInterceptor())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Sets the User-Agent only when the caller hasn't supplied one
     * (Innertube POSTs set their own per-client UA and must pass through
     * untouched) and picks the UA by host for everything else. `googlevideo`
     * + youtube-family hosts get the ANDROID_VR UA so streaming requests
     * align with the `/player` client that issued the URL — avoiding the
     * client/URL-identity mismatch that triggers googlevideo's soft throttle
     * (the "1-2 s play, 1 s stall" pattern).
     */
    private fun userAgentInterceptor(): Interceptor = Interceptor { chain ->
        val request = chain.request()
        if (request.header("User-Agent") != null) {
            return@Interceptor chain.proceed(request)
        }
        val host = request.url.host
        val ua = when {
            host == "googlevideo.com" || host.endsWith(".googlevideo.com") -> ANDROID_VR_USER_AGENT
            host == "youtube.com" || host.endsWith(".youtube.com") || host == "youtu.be" -> ANDROID_VR_USER_AGENT
            else -> USER_AGENT
        }
        chain.proceed(request.newBuilder().header("User-Agent", ua).build())
    }

    private fun rateLimitInterceptor(): Interceptor = Interceptor { chain ->
        val host = chain.request().url.host
        if (host == "bandcamp.com" || host.endsWith(".bandcamp.com")) {
            // Reserve a time slot atomically, then sleep outside the lock
            // so other threads aren't blocked while we wait
            val sleepTime: Long
            synchronized(rateLimitLock) {
                val now = System.currentTimeMillis()
                val elapsed = now - lastRequestTime
                sleepTime = if (elapsed < RATE_LIMIT_INTERVAL_MS) {
                    RATE_LIMIT_INTERVAL_MS - elapsed
                } else {
                    0L
                }
                lastRequestTime = now + sleepTime.coerceAtLeast(0L)
            }
            if (sleepTime > 0) {
                try {
                    Thread.sleep(sleepTime)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        }
        chain.proceed(chain.request())
    }

    private fun retryInterceptor(): Interceptor = Interceptor { chain ->
        val request = chain.request()
        var response: Response = chain.proceed(request)
        var retryCount = 0

        while (response.code == 429 && retryCount < MAX_RETRIES) {
            response.close()
            val backoffMs = INITIAL_BACKOFF_MS * (1L shl retryCount)
            try {
                Thread.sleep(backoffMs)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("Retry interrupted")
            }
            retryCount++
            response = chain.proceed(request)
        }

        response
    }
}
