# JNI entry points are referenced from Kotlin and must retain their names.
-keep class tech.g24.feresaslicer.slicer.NativeSlicer { *; }

# The packaged OrcaSlicer Mobile library resolves these classes and callbacks by their exact
# binary names in JNI_OnLoad/model_slice. Debug builds do not run R8, so an on-device debug slice
# cannot detect an accidental rename or removal here. Keep the small headless bridge package
# intact and verify the minified release APK in the pre-Play gate.
-keep class ru.ytkab0bp.slicebeam.slic3r.** { *; }
