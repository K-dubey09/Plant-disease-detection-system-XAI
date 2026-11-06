# Keep TensorFlow Lite model interpreter and support classes.
-keep class org.tensorflow.** { *; }
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.support.** { *; }
-keep class org.tensorflow.lite.gpu.** { *; }

# Keep ONNX Runtime (v2.0.0.0 primary inference path) classes + native peer. ORT loads its
# native lib reflectively and the Java API is accessed via Hilt-provided OrtSession; without
# these keeps R8 strips/renames the entry points and inference crashes at runtime.
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# Keep model metadata / label assets loaded reflectively.
-keep class com.leafguard.app.data.local.model.** { *; }
-keep class com.leafguard.app.data.domain.model.** { *; }

# Keep Hilt generated entry points.
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }

# Room generated implementations.
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }

# Suppress R8 warnings for TFLite native peer classes.
-dontwarn org.tensorflow.lite.**

# Tink (pulled in transitively by androidx.security.crypto for the encrypted PIN store)
# references com.google.errorprone.annotations.* -- compile-time-only annotations that are
# intentionally absent from the runtime classpath. R8 full mode (the default for release
# builds since AGP 8) treats the missing classes as a hard build error without this rule,
# which breaks assembleRelease. The annotations have no runtime effect, so suppressing the
# warning is safe.
-dontwarn com.google.errorprone.annotations.**
