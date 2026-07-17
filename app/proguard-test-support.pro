# Extra keep rules applied ONLY when instrumentation runs against the
# minified release APK (-PtestReleaseBuild, see app/build.gradle.kts).
# The androidTest APK is compiled against unobfuscated symbols, so every
# app class the tests reference directly (constructors, methods) must keep
# its name in that lane. The shipped release APK never includes these.

# AndroidJUnitRunner calls androidx.tracing.Trace in onCreate. The library
# is already in the APP's dependency graph (WorkManager pulls it), so AGP
# EXCLUDES it from the test APK as app-provided - which means the app's R8
# must not strip or rename it, or the runner dies with NoClassDefFoundError
# before a single test runs ('Starting 0 tests').
-keep class androidx.tracing.** { *; }

# Tests construct SettingsDataStore(context) directly to read/write prefs.
-keep class com.dustvalve.next.android.data.local.datastore.SettingsDataStore { *; }

# Tests bind a MediaController to the real PlaybackService session.
-keep class com.dustvalve.next.android.player.PlaybackService { *; }

# Deep-link driving via ViewModelProvider from tests.
-keep class com.dustvalve.next.android.ui.navigation.NavigationViewModel { *; }
-keep class com.dustvalve.next.android.ui.navigation.NavDestination { *; }
-keep class com.dustvalve.next.android.ui.navigation.NavDestination$* { *; }

# Compose test framework matches semantics by resource-independent test
# tags; tags are string constants (inlined), no extra keeps needed.
