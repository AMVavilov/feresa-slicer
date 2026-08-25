# The AndroidX runner starts inside the target process before the test APK is fully attached. R8
# may otherwise remove target dependencies used only by that bootstrap. This file is applied only
# to the local `releaseTest` target; it never changes the Play APK/AAB.
-keep class androidx.** { *; }
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }

# The instrumentation APK is compiled against the target APK before R8 renames its classes.
# Keep the slicer boundary stable so the release-like test can invoke the exact production
# pipeline across that APK boundary. This rule is test-only and is not used by the Play bundle.
-keep class tech.g24.feresaslicer.** { *; }
