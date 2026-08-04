# kotlinx-serialization, Jsoup, and Room ship their own consumer ProGuard rules
# in their AAR/JAR (META-INF/proguard/*.pro and META-INF/com.android.tools/r8/*.pro)
# which AGP applies automatically. Keep this file minimal; only add app-specific
# rules R8 can't infer.

# OkHttp 5.x has no consumer rules; suppress its optional-platform reflection notes.
-dontwarn okhttp3.internal.platform.**

# Strip Compose composition-tracing calls (and their per-composable trace
# strings) from release APKs. The Compose compiler emits traceEventStart/End
# into every restartable composable; without this rule both release flavors
# pay a small per-recomposition cost and carry every composable's trace
# string in the DEX. Tracing stays available in debug builds.
-assumenosideeffects class androidx.compose.runtime.ComposerKt {
    boolean isTraceInProgress();
    void traceEventStart(int, int, int, java.lang.String);
    void traceEventStart(int, java.lang.String);
    void traceEventEnd();
}
