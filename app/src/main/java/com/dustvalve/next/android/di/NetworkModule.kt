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

    private fun userAgentInterceptor(): Interceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .header("User-Agent", USER_AGENT)
            .build()
        chain.proceed(request)
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
