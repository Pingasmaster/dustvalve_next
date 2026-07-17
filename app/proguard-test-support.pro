# Extra keep rules applied ONLY when instrumentation runs against the
# minified release APK (-PtestReleaseBuild, see app/build.gradle.kts).
# The androidTest APK is compiled against unobfuscated symbols, so every
# app class the tests reference directly (constructors, methods) must keep
# its name in that lane. The shipped release APK never includes these.

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
