-keepattributes Signature
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable

# Moshi reflection
-keepclasseswithmembers class * {
    @com.squareup.moshi.* <methods>;
}
-keep class com.squareup.moshi.** { *; }
-keep @com.squareup.moshi.JsonClass class *
-keepclassmembers class ** {
    @com.squareup.moshi.FromJson *;
    @com.squareup.moshi.ToJson *;
}

# Room
-keep class androidx.room.** { *; }
-keep class com.buzzingmountain.dingclock.db.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Kotlin
-dontwarn kotlin.**

# Timber
-dontwarn org.jetbrains.annotations.**

# Our data classes (keep for Moshi)
-keep class com.buzzingmountain.dingclock.data.** { *; }

# AccessibilityService
-keep class * extends android.accessibilityservice.AccessibilityService

# BroadcastReceivers referenced from manifest
-keep class * extends android.content.BroadcastReceiver
