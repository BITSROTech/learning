# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
# https://developers.kakao.com/docs/latest/en/getting-started/sdk-android#configure-for-shrinking-and-obfuscation-(optional)
# --- Moshi reflection (DTO 보존)
-keep class com.example.ailearningapp.data.remote.** { *; }
-keepclassmembers class com.example.ailearningapp.data.remote.** { *; }

# Kotlin metadata (리플렉션)
-keep class kotlin.Metadata { *; }

# Okio / Moshi / Retrofit 일반 권장
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-dontwarn com.squareup.moshi.**

# Kakao SDK (모델/인터페이스 보존 권장)
-keep class com.kakao.** { *; }
-dontwarn com.kakao.**

# Google Play Services Auth
-dontwarn com.google.android.gms.**