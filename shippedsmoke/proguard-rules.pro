# R8 rules for the :shippedsmoke TEST APK.
#
# AGP refuses to instrument a minified app from a non-minified test module
# (checkTestedAppObfuscation*: it needs shrinking enabled here to apply the
# app's mapping file), so minification must be ON. Nothing about this APK
# should actually be shrunk though - it is a two-test harness whose size is
# irrelevant, and shrinking it can only remove something the runner reflects
# on. Same trade as app/proguard-androidtest.pro: fidelity, not size.
-dontshrink
-dontoptimize
-dontobfuscate

# JUnit and the runner discover tests purely by reflection, so every
# reflective attribute has to survive.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable

# This module never links app classes - it drives the app through UiAutomator
# by resource id - so there is nothing here to translate through the app's
# mapping, and no keep rules for app symbols are needed.
-dontwarn **
