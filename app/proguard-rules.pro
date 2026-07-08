# Image to PDF - ProGuard / R8 rules for release builds.
# The app has no reflection-heavy libraries (no networking, no JSON parsing,
# no dependency injection), so the default AGP/R8 optimizations are sufficient.
# Keep rules below are conservative safety nets.

# Keep view binding generated classes (referenced only by generated code paths).
-keep class com.example.imagetopdf.databinding.** { *; }

# Keep Parcelable / Serializable implementations if any are ever added.
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Preserve line numbers for readable stack traces in crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
