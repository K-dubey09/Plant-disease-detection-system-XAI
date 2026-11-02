# Keep TensorFlow Lite model interpreter and support classes.
-keep class org.tensorflow.** { *; }
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.support.** { *; }
-keep class org.tensorflow.lite.gpu.** { *; }

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
