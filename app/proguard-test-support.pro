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
#
# Enumerating those namespaces one at a time is a losing game: each CI round
# surfaced exactly one more missing package (androidx.tracing, kotlin,
# androidx.collection, androidx.concurrent.futures, ... then javax.inject,
# which espresso-core needs and which the app happens to provide via Hilt,
# so AGP deduplicated it out of the test APK while the app's R8 renamed it).
# The set is unknowable up front - it is the transitive closure of every
# library the test APK touches that the app also ships. So keep the whole
# non-app universe instead, and let the negated filter carve out app code.
#
# FIDELITY NOTE: this freezes LIBRARY namespaces only - the app's own code
# (com.dustvalve.**) stays fully minified/obfuscated, which is where
# release-only breakage lives. Never shipped: test lane only, and the test
# APK's size/method count is irrelevant on a CI emulator.
-keep class !com.dustvalve.next.**,** { *; }

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
