# AndroidX Test carries compile-time-only Error Prone annotations in its signatures. They are not
# used by the instrumentation runtime and are intentionally absent from Android devices.
-dontwarn com.google.errorprone.annotations.**

# The instrumentation runner uses reflection and calls AndroidX tracing from its bootstrap path.
# Test APK size is irrelevant; retain the runner surface so release-only tests cannot be optimized
# into a zero-test or startup-crash false result.
-keep class androidx.test.** { *; }
-keep class androidx.tracing.** { *; }
-keep class org.junit.** { *; }
