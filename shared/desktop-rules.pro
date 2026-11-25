# ProGuard rules for the desktop release build (see compose.desktop in build.gradle.kts).

# logback's optional email, servlet and xz features; the app doesn't use them.
-dontwarn jakarta.**
-dontwarn org.tukaani.xz.**

# Found at run time by name (ServiceLoader, logback.xml, JNI), not by direct calls.
-keep class ch.qos.logback.** { *; }
-keep class org.slf4j.** { *; }
-keep class io.ktor.client.engine.** { *; }
-keep class androidx.sqlite.** { *; }
