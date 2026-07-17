# Extra keep rules applied ONLY when instrumentation runs against the
# minified release APK (-PtestReleaseBuild, see app/build.gradle.kts).
# The androidTest APK is compiled against unobfuscated symbols, so every
# app class the tests reference directly (constructors, methods) must keep
# its name in that lane. The shipped release APK never includes these.

# The test APK links against library classes the APP already provides; AGP
# excludes app-provided classes from the test APK, so the app's R8 must not
# strip or rename anything the test harness touches, or instrumentation
# dies with NoClassDefFoundError before a single test runs ('Starting 0
# tests'; observed for androidx.tracing.Trace, then kotlin.LazyKt).
# FIDELITY NOTE: these keeps freeze LIBRARY namespaces only - the app's own
# code (com.dustvalve.**) stays fully minified/obfuscated, which is where
# release-only breakage lives. Never shipped: test lane only.
-keep class androidx.tracing.** { *; }
-keep class androidx.collection.** { *; }
-keep class androidx.concurrent.futures.** { *; }
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keep class androidx.compose.** { *; }
-keep class androidx.lifecycle.** { *; }
-keep class androidx.activity.** { *; }
-keep class androidx.core.** { *; }
-keep class androidx.media3.session.** { *; }
-keep class androidx.media3.common.** { *; }

# Tests construct SettingsDataStore(context) directly to read/write prefs.
-keep class com.dustvalve.next.android.data.local.datastore.SettingsDataStore { *; }

# The androidTest Flows helpers call TestTags functions at runtime; R8 full
# mode had removed the whole object from the app (R8$$REMOVED$$CLASS$$)
# while the test APK still referenced it by name.
-keep class com.dustvalve.next.android.ui.TestTags { *; }

# Tests bind a MediaController to the real PlaybackService session.
-keep class com.dustvalve.next.android.player.PlaybackService { *; }

# Deep-link driving via ViewModelProvider from tests.
-keep class com.dustvalve.next.android.ui.navigation.NavigationViewModel { *; }
-keep class com.dustvalve.next.android.ui.navigation.NavDestination { *; }
-keep class com.dustvalve.next.android.ui.navigation.NavDestination$* { *; }

# Compose test framework matches semantics by resource-independent test
# tags; tags are string constants (inlined), no extra keeps needed.
