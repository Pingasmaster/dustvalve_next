# R8 rules for the ANDROIDTEST APK when it instruments the minified release
# build (-PtestReleaseBuild lane). The test APK's size is irrelevant - keep
# the whole test harness intact instead of chasing individual reflective
# entry points. First observed failure without these: AndroidJUnitRunner
# crashing with NoClassDefFoundError: androidx.tracing.Trace in onCreate,
# aborting the run with 'Starting 0 tests'.

# The test APK needs FIDELITY, not size. R8 full-mode optimization removed
# the runner's guarded catch around its optional legacy
# android.test.suitebuilder.annotation.Suppress lookup, turning a benign
# missing-class probe into a fatal NoClassDefFoundError inside
# TestRequestBuilder - runner init died before discovery ('Starting 0
# tests'). Never shrink or optimize the harness; obfuscation stays enabled
# so app-class references keep flowing through the apply-mapping path.
-dontshrink
-dontoptimize

# Keeping the classes is not enough: R8 also strips annotation ATTRIBUTES
# (RuntimeVisibleAnnotations) unless told otherwise, which made the
# -Pandroid.testInstrumentationRunnerArguments.annotation=SmokeTest filter
# match NOTHING - 'Starting 0 tests' reported as green until the
# zero-test guard caught it. JUnit and the runner's annotation filtering
# are pure runtime reflection; keep every reflective attribute.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable

-keep class androidx.tracing.** { *; }
-keep class androidx.test.** { *; }
-keep class androidx.compose.ui.test.** { *; }
-keep class org.junit.** { *; }
-keep class junit.** { *; }
-keep class com.dustvalve.next.** { *; }
-keep class com.google.common.truth.** { *; }
-keep class kotlinx.coroutines.test.** { *; }
-dontwarn **
