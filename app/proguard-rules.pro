# kotlinx-serialization, Jsoup, and Room ship their own consumer ProGuard rules
# in their AAR/JAR (META-INF/proguard/*.pro and META-INF/com.android.tools/r8/*.pro)
# which AGP applies automatically. Keep this file minimal; only add app-specific
# rules R8 can't infer.

# OkHttp 5.x has no consumer rules; suppress its optional-platform reflection notes.
-dontwarn okhttp3.internal.platform.**
