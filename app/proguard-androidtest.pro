# R8 rules for the ANDROIDTEST APK when it instruments the minified release
# build (-PtestReleaseBuild lane). The test APK's size is irrelevant - keep
# the whole test harness intact instead of chasing individual reflective
# entry points. First observed failure without these: AndroidJUnitRunner
# crashing with NoClassDefFoundError: androidx.tracing.Trace in onCreate,
# aborting the run with 'Starting 0 tests'.

-keep class androidx.tracing.** { *; }
-keep class androidx.test.** { *; }
-keep class androidx.compose.ui.test.** { *; }
-keep class org.junit.** { *; }
-keep class junit.** { *; }
-keep class com.dustvalve.next.** { *; }
-keep class com.google.common.truth.** { *; }
-keep class kotlinx.coroutines.test.** { *; }
-dontwarn **
