import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.leafguard.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.leafguard.app"
        // minSdk 26 (Android 8.0): every 6GB+ RAM device ships with Android 8+. Keeps the
        // adaptive-icon + splash-screen pipeline clean and avoids legacy raster fallbacks.
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "0.4.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    // Pinned debug keystore (committed at app/keystores/debug.keystore, standard Android debug
    // credentials) so every CI/local debug build shares ONE signer. Without this, GitHub-hosted
    // runners regenerate ~/.android/debug.keystore each run, so successive debug APKs are signed
    // with different keys and Android refuses to install a new build over an old one ("there was
    // a problem parsing the package"). Debug keys are non-secret by convention.
    //
    // Release signing: the keystore is NOT committed (release keys are secret). Credentials come
    // from environment variables, set in CI from GitHub repository secrets:
    //   KEYSTORE_FILE       - path to the decoded release keystore on the runner
    //   KEYSTORE_PASSWORD   - keystore password
    //   KEY_ALIAS           - key alias inside the keystore
    //   KEY_PASSWORD        - password for that key
    // When KEYSTORE_FILE is absent (e.g. a local build), the release config stays empty and the
    // release buildType below does NOT attach a signer, so Gradle emits an unsigned release APK
    // with a warning rather than silently shipping an uninstallable one.
    signingConfigs {
        getByName("debug") {
            storeFile = file("keystores/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("release") {
            System.getenv("KEYSTORE_FILE")?.takeIf { it.isNotEmpty() }?.let { storeFile = file(it) }
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
            keyAlias = System.getenv("KEY_ALIAS") ?: ""
            keyPassword = System.getenv("KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Attach the release signer only when KEYSTORE_FILE is set (CI). Without it the build
            // is unsigned and Gradle warns -- which is the correct behaviour for a local dev build
            // and the direct fix for the "problem parsing the package" install error on releases.
            if (!System.getenv("KEYSTORE_FILE").isNullOrEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE*"
            excludes += "META-INF/NOTICE*"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.2")

    // AndroidX core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.splashscreen)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.video)

    // Security + storage
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.datastore.preferences)

    // TensorFlow Lite (on-device inference + Grad-CAM XAI)
    implementation(libs.tflite)
    implementation(libs.tflite.support)
    implementation(libs.tflite.gpu)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Image loading (scan thumbnails, history gallery)
    implementation(libs.coil.compose)

    // Permissions
    implementation(libs.accompanist.permissions)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.google.truth)
    testImplementation(libs.androidx.arch.core.testing)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.hilt.android.testing)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.google.truth)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
}

// Bound the forked test JVM so the unit-test runner does not OOM alongside the Gradle daemon in
// memory-constrained environments (the runner only hosts JUnit + Truth/Mockk/Turbine, no app
// framework). Also caps the daemon-side managed metaspace via gradle.properties.
tasks.withType<Test>().configureEach {
    maxHeapSize = "512m"
    jvmArgs("-XX:MaxMetaspaceSize=256m", "-XX:+UseSerialGC")
    // Robolectric is on the classpath but no test uses it; prevent it from downloading Android
    // SDK jars on first run (keeps the test JVM lean and offline).
    systemProperty("robolectric.offline", "true")
}
