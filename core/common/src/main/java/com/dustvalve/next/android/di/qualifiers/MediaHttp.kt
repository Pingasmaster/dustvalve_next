package com.dustvalve.next.android.di.qualifiers

import jakarta.inject.Qualifier
import kotlin.annotation.AnnotationRetention.RUNTIME

/**
 * Hilt qualifier for the OkHttpClient used by long-lived media transfers:
 * ExoPlayer's streaming data source, track downloads, and the self-update APK
 * download.
 *
 * The unqualified client carries a 30s callTimeout, which caps the WHOLE call
 * - from newCall() until the response body is fully consumed and closed.
 * That is correct for scraping/API calls, but fatal for media:
 *
 * - ExoPlayer holds the response body open for the life of a progressive
 *   stream and reads it as the buffer drains, so every streamed track was
 *   force-aborted ~30s in (the v0.5.0 "streams die mid-track" regression).
 * - A track/APK download over a slow connection legitimately takes minutes;
 *   callTimeout aborted any transfer that outlived 30s.
 *
 * The media client disables callTimeout and keeps connectTimeout/readTimeout,
 * which are the correct guards against genuinely stalled transfers (they
 * bound inactivity, not total duration).
 */
@Qualifier
@Retention(RUNTIME)
annotation class MediaHttp
