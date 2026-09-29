-keepattributes Signature
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable

-keep class ru.chronicnotebook.data.** { *; }
-keep class ru.chronicnotebook.reminders.** { *; }

-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**

-keepclassmembers class okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
