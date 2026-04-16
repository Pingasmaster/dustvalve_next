# Jsoup
-keeppackagenames org.jsoup.nodes
-dontwarn com.google.re2j.Matcher
-dontwarn com.google.re2j.Pattern

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.dustvalve.next.android.**$$serializer { *; }
-keepclassmembers class com.dustvalve.next.android.** {
    *** Companion;
}
-keepclasseswithmembers class com.dustvalve.next.android.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# OkHttp
-dontwarn okhttp3.internal.platform.**

# NewPipe Extractor
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.extractor.**
-dontwarn org.mozilla.javascript.**
-dontwarn com.grack.nanojson.**
# Rhino (pulled by NewPipeExtractor) ships a service file referencing javax.script,
# which is unavailable on Android. Silences R8's "missing service class" warning so
# it doesn't bail out of optimizing the Rhino/NewPipe code path.
-dontwarn javax.script.**
