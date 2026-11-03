# LeafGuard — Explainable Plant Disease Detection for Android

LeafGuard is a production-targeted Android app that diagnoses plant leaf diseases **on-device**
with a TensorFlow Lite model and explains every prediction with a **Grad-CAM** saliency heatmap.
The user registers once with a 6-digit PIN, then tracks individual plants, scans leaves in real
time, and builds a per-plant disease history with actionable treatment suggestions.

> Status: **Phase 3 of 8 — Room persistence & Plants CRUD.** A Keystore-backed PIN gates the app;
> plants now persist in a Room database with a real list/add/edit/delete UI. 50 JVM unit tests
> pass. The launch-time white screen from Phase 2 (auth-root launcher race) is fixed.
> See [`docs/ROADMAP.md`](docs/ROADMAP.md) for the full phased plan and the PDF
> `LeafGuard_Project_Plan.pdf` (in the repo root after Phase 1) for the design rationale.

---

## Why this design

| Constraint | Decision |
| --- | --- |
| Android, 6 GB+ RAM target | Native Kotlin + Jetpack Compose; `minSdk = 26` (Android 8.0 covers every 6 GB+ device) |
| Model ≤ 50 MB, runs offline | MobileNetV2 fine-tuned on PlantVillage (38 classes, 224×224), converted to TFLite (~12 MB float16 / ~4 MB INT8). Source: `huggingface.co/rarfileexe/Plant-Disease-Detector` (24.0 MB `.keras`, MIT, 98.75% test accuracy) |
| Explainable AI | Grad-CAM over MobileNetV2's final conv block (`Conv_1`/`out_relu`), rendered as a jet-colormap overlay on the captured leaf |
| First-launch register, PIN login after | PIN stored in `EncryptedSharedPreferences` keyed to the Android Keystore; biometric fallback optional |
| Production, not prototype | MVVM + Clean Architecture, Hilt DI, Room persistence, CameraX, type-safe navigation, unit + instrumented tests, R8 minification, CI |

---

## Tech stack

- **Language:** Kotlin 2.0.21
- **UI:** Jetpack Compose (Material 3, dynamic color on Android 12+)
- **Navigation:** Navigation Compose, single-activity
- **DI:** Hilt 2.52
- **Persistence:** Room 2.6.1 (plants, scans, history, records)
- **Camera:** CameraX 1.3.4
- **Security:** `androidx.security:security-crypto` 1.1.0-alpha06 (Keystore-backed PIN)
- **ML:** TensorFlow Lite 2.16.1 (+ GPU delegate + support lib)
- **Image loading:** Coil 2.7.0
- **Async:** Coroutines 1.9.0 + Flow
- **Build:** AGP 8.7.2, Gradle 8.9, version catalog (`gradle/libs.versions.toml`)

---

## Project structure

```
.
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/leafguard/app/
│       │   │   ├── LeafGuardApplication.kt      # @HiltAndroidApp entry point
│       │   │   ├── MainActivity.kt              # single-activity host + splash
│       │   │   ├── ui/
│       │   │   │   ├── theme/                    # Color, Theme, Type (Material 3)
│       │   │   │   ├── navigation/               # Routes, TopDestination, LeafGuardNavHost
│       │   │   │   ├── components/               # LeafScaffold, PhasePlaceholder
│       │   │   │   └── screens/                  # Auth, Dashboard, Plants, PlantDetail,
│       │   │   │                                 #   Scan, Result, History, Images, Records
│       │   │   ├── data/  (Phase 2–3)            # Room entities, DAOs, repositories
│       │   │   ├── domain/ (Phase 3–7)           # use-cases, model abstractions
│       │   │   └── di/ (Phase 2+)               # Hilt modules
│       │   └── res/                              # strings, colors, themes, icons, backup XML
│       ├── test/                                  # JVM unit tests (Truth, Mockk, Turbine)
│       └── androidTest/                           # instrumented tests (Espresso, Compose test)
├── gradle/
│   ├── libs.versions.toml                        # central version catalog
│   └── wrapper/                                  # gradle-wrapper.jar + properties
├── docs/
│   ├── ARCHITECTURE.md
│   └── ROADMAP.md
├── .github/workflows/android.yml                 # CI: build + unit test
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradlew / gradlew.bat
└── .gitignore
```

---

## Build & run

Requires Android Studio Koala/Ladybug (or later) and JDK 17.

1. Clone and open the project root in Android Studio. It will sync the Gradle wrapper
   (Gradle 8.9) and download the AGP/Compose/Hilt plugins.
2. Let Android Studio install **Android SDK Platform 35** and **Build-Tools 35.x** if prompted.
3. Select a device or emulator running **API 26+** (the 6 GB+ RAM target).
4. Run the `app` configuration. Phase 1 boots into the Register screen; the bottom navigation
   between Dashboard / Plants / Scan / History / Records is fully wired, and every screen
   honestly reports the phase in which its real content ships.

Command-line build (once the Android SDK is installed and `local.properties` points at it):

```bash
./gradlew assembleDebug          # build the debug APK
./gradlew test                   # run JVM unit tests
./gradlew connectedAndroidTest   # instrumented tests (device/emulator needed)
./gradlew lint                   # Android Lint
```

---

## Model provenance (verified, not synthetic)

The on-device model is a real, publicly downloadable artifact:

- **Repo:** `https://huggingface.co/rarfileexe/Plant-Disease-Detector`
- **File:** `model_4_mobilenet_finetuned.keras` — **24.0 MB** (25,143,175 bytes), verified via HTTP HEAD.
- **Architecture:** MobileNetV2, transfer-learning + fine-tuning, Functional API.
- **Dataset:** PlantVillage (Augmented), ~87,000 images, 38 classes, 14 crop species.
- **Reported accuracy:** 98.75% on the held-out test set.
- **Input:** 224×224 RGB leaf.
- **License:** MIT.

In Phase 4 this `.keras` file is converted to a quantized `.tflite` (float16 by default, INT8
option) with metadata, the 38-class `labels.txt`, and a `disease_suggestions.json` mapping each
class to treatment steps. Conversion happens in a reproducible Python script checked into the
repo; the model assets land under `app/src/main/assets/models/`.

> The other candidate inspected (`nanocu/plant_disease`, 489 MB `.h5`) exceeds the 50 MB budget
> even after reasonable pruning and was rejected.

---

## Explainability — Grad-CAM

For an image classification prediction, Grad-CAM (Gradient-weighted Class Activation Mapping)
produces a coarse localization heatmap showing which spatial regions of the input most raised the
predicted class score. LeafGuard computes it on-device by:

1. Running a forward pass to obtain the predicted class index.
2. Reading the activations of MobileNetV2's last convolutional output (`Conv_1`).
3. Computing the gradient of the predicted-class logit w.r.t. those activations (a second,
   gradient-enabled TFLite sub-graph or a small TF SavedModel reloaded via the support library).
4. Global-average-pooling the gradients into channel weights, weighting the activations, and
   applying ReLU.
5. Upsampling the 7×7 heatmap to 224×224, colour-mapping (blue→green→red), and compositing over
   the captured photo.

The overlay is shown beside the confidence so the user can verify the model "looked at" the lesion
and not the background — the core of trustworthy on-device diagnosis.

---

## Testing strategy

- **Unit (JVM):** pure logic — navigation route helpers, PIN validation, suggestions mapping,
  Grad-CAM weight math, repository mappers (Truth assertions, Mockk for collaborators, Turbine
  for Flow). See `app/src/test/`.
- **Instrumented:** Room DAOs against an in-memory database, Hilt-injected repositories, and
  Compose UI tests for the auth flow and scan→result navigation. See `app/src/androidTest/`.
- **CI:** GitHub Actions runs `./gradlew assembleDebug test lint` on every push.
- **Manual validation plan:** a small held-out leaf set per crop is captured on a reference
  device to confirm top-1 accuracy and Grad-CAM locality before each release tag.

---

## Roadmap summary

| Phase | Deliverable | Status |
| --- | --- | --- |
| 1 | Project foundation, Gradle/Compose/Hilt setup, navigation skeleton, theme, docs | ✅ This commit |
| 2 | PIN authentication (register/login), EncryptedSharedPreferences + Keystore, session gating | ⏳ Next |
| 3 | Room schema (plants, scans, history, records), DAOs, repositories, Plants CRUD | ⏳ |
| 4 | TFLite model conversion + assets, inference engine, Grad-CAM XAI engine | ⏳ |
| 5 | CameraX live scan, capture → inference → result flow, attach to plant | ⏳ |
| 6 | Dashboard stats, History feed, Images gallery, Records + CSV/PDF export | ⏳ |
| 7 | Treatment suggestions engine + per-disease knowledge base | ⏳ |
| 8 | Polish, full test pass, R8/release build, store readiness | ⏳ |

See [`docs/ROADMAP.md`](docs/ROADMAP.md) for the per-phase detail.

---

## Contributing / development notes

- The PIN and any derived auth material are **never** backed up (see `res/xml/backup_rules.xml`).
- `local.properties` (SDK path) and signing keystrokes are git-ignored; never commit them.
- Every screen that is not yet implemented shows a `PhasePlaceholder` stating the phase number —
  there is no fake "loading" UI pretending features exist.
