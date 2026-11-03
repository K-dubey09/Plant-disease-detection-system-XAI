# LeafGuard — Explainable Plant Disease Detection for Android

LeafGuard is a production-targeted Android app that diagnoses plant leaf diseases **on-device**
with a TensorFlow Lite model and explains every prediction with an **occlusion-sensitivity**
saliency heatmap.
The user registers once with a 6-digit PIN, then tracks individual plants, scans leaves in real
time, and builds a per-plant disease history with actionable treatment suggestions.

> Status: **Phase 6 of 8 — Dashboard + History (in progress).** Building on Phase 5's persisted
> scans, the Dashboard now shows live stats (total scans, plants tracked, most-recent diagnosis +
> confidence) and a History feed lists every past diagnosis with per-plant filter chips, each
> tapping into the addressable `result/{scanId}` route. Records (CSV/PDF export) + Images (gallery)
> remain in Phase 6. **85 JVM unit tests pass.** The signed release APK (v1 + v2 + v3 signing) is
> published to GitHub Releases as a raw `.apk` — download and tap to install on Android 8.0+.
> See [`docs/ROADMAP.md`](docs/ROADMAP.md) for the full phased plan and the PDF
> `LeafGuard_Project_Plan.pdf` (in the repo root after Phase 1) for the design rationale.

---

## Why this design

| Constraint | Decision |
| --- | --- |
| Android, 6 GB+ RAM target | Native Kotlin + Jetpack Compose; `minSdk = 26` (Android 8.0 covers every 6 GB+ device) |
| Model runs offline, 6 GB+ RAM target | Swin-Tiny transformer (patch4-window7-224, 38 PlantVillage classes, **98.59% validation accuracy**) converted PyTorch→ONNX→TFLite (~112 MB FP32). Source: HuggingFace `surgeonwz/plant-village` (apache-2.0); the trained `.tflite` is pulled from a private Kaggle dataset by CI on every build |
| Explainable AI | Occlusion-sensitivity heatmap (model-agnostic — TFLite's `Interpreter` exposes no conv activations/gradients, so true Grad-CAM is unavailable on-device): an 8×8 grid is greyed cell-by-cell and the predicted class's confidence drop per cell becomes the jet-colormap overlay on the captured leaf |
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

## Install the release APK

The signed release APK is published as a **GitHub Release asset** — a raw `.apk` with no
artifact-ZIP to extract — on every green build of `main`:

1. Open the repository's **Releases** page and pick the latest `v0.4.0` release.
2. Download `LeafGuard-v0.4.0-release.apk` (~137 MB) directly.
3. On the device (Android 8.0+), tap the downloaded `.apk` and allow **Install unknown apps** for
   your browser/files app when prompted, then confirm the install.
4. First launch opens the Register screen.

The APK is signed with **v1 + v2 + v3** APK signature schemes. The v1 (JAR) signature is the one
every Android installer understands — AGP 8.x omits v1 by default once `minSdk ≥ 24`, leaving a
v2-only APK that some OEM package installers reject with "There was a problem parsing the package";
`build.gradle.kts` forces all three schemes, and the CI `Verify release APK is signed` step asserts
v1 + v2 are present. `minSdk = 26` (Android 8.0) is intentional — the trained Swin-Tiny model
targets 6 GB+ RAM devices, all of which ship with Android 8+.

If you are testing a pull-request build (no Release asset), download the `leafguard-release-apk`
artifact from the Actions run and **extract the `.zip`** before tapping the `.apk` inside — the
Actions artifact is ZIP-wrapped, the Release asset is not.

---

## Model provenance (verified, not synthetic)

The on-device model is a real, trained artifact — not a demo head.

- **Architecture:** Swin-Tiny (Swin Transformer, patch4-window7-224), 38-class PlantVillage head.
- **Source:** `https://huggingface.co/surgeonwz/plant-village` (apache-2.0) — the PyTorch
  checkpoint used for conversion.
- **Conversion pipeline:** PyTorch → ONNX → TFLite, via `models/conversion/convert.py`
  (`dynamo=False` to bypass an onnx2tf Loop-op blocker). ImageNet mean/std normalization and the
  final Softmax are **baked into the graph** as ONNX `Sub(mean)`/`Div(std)` nodes, so the app
  feeds raw `[0,1]` pixels and reads real probabilities directly.
- **Artifact:** `plant_disease.tflite` — **~112 MB** (FP32), input `[1,224,224,3] float32`,
  output `[1,38] float32` (softmax). Verified off-device against its ONNX reference to a mean
  max-abs diff of 4.42×10⁻⁸ with 8/8 argmax agreement.
- **Dataset:** PlantVillage (Augmented), ~87,000 images, 38 classes, 14 crop species.
- **Accuracy:** **98.59%** on the PlantVillage validation split.
- **Distribution:** the trained `.tflite` is pulled from the private Kaggle dataset
  `kushagradubey09/plant-disease-xai-models` by CI on every build (a small untrained demo model
  is committed under `app/src/main/assets/models/` as a fallback for offline/fork builds).
- **Targeted at 6 GB+ RAM devices** because of the ~120–180 MB resident interpreter footprint.

> The earlier MobileNetV2 candidate (~12 MB, 78.6% accuracy) was replaced by Swin-Tiny for the
> accuracy gain; the larger footprint is the trade-off, accepted because the app targets
> 6 GB+ RAM devices.

---

## Explainability — occlusion sensitivity

For an image classification prediction, a saliency heatmap shows which spatial regions of the
input most raised the predicted class score, so the user can verify the model "looked at" the
lesion and not the background. LeafGuard computes this on-device with **occlusion sensitivity**
rather than Grad-CAM, because TFLite's `Interpreter` exposes neither the intermediate convolutional
activations nor gradients — the two things Grad-CAM needs. Occlusion sensitivity is model-agnostic
and needs only the forward pass:

1. Run a forward pass to obtain the predicted class index and its baseline confidence.
2. Lay a coarse **8×8 grid** over the 224×224 input.
3. For each cell, grey-out (zero) that cell's pixels and re-run inference; record the predicted
   class's **confidence drop** relative to the baseline.
4. The per-cell drops form an 8×8 saliency map, upsampled to 224×224, colour-mapped
   (blue→green→red), and composited over the captured photo.

Cells whose occlusion caused the largest confidence drop are the regions the model was relying on
for the prediction. The overlay is shown beside the confidence so the diagnosis is auditable — the
core of trustworthy on-device disease detection. The trade-off vs. Grad-CAM is 64 extra forward
passes per scan (acceptable on a 6 GB+ device; cached for the result screen).

---

## Testing strategy

- **Unit (JVM):** pure logic — navigation route helpers, PIN validation, label parsing,
  suggestion mapping, occlusion-rect computation, pixel normalization, repository mappers, the
  Scan repository (persist, per-plant filter, observe-then-null), and the Dashboard + History
  ViewModels (live stats, plant-name join, filter). (Truth assertions, Mockk for collaborators,
  Turbine for Flow). **85 tests pass.** See `app/src/test/`.
- **Instrumented:** Room DAOs against an in-memory database, Hilt-injected repositories, and
  Compose UI tests for the auth flow and scan→result navigation. See `app/src/androidTest/`.
- **CI:** GitHub Actions runs `./gradlew assembleDebug assembleRelease test lint` on every push,
  pulls the trained Swin-Tiny model from Kaggle, verifies the release APK is **v1 + v2 signed**,
  and publishes the signed `.apk` to **GitHub Releases**.
- **Manual validation plan:** a small held-out leaf set per crop is captured on a reference
  device to confirm top-1 accuracy and occlusion-heatmap locality before each release tag.

---

## Roadmap summary

| Phase | Deliverable | Status |
| --- | --- | --- |
| 1 | Project foundation, Gradle/Compose/Hilt setup, navigation skeleton, theme, docs | ✅ |
| 2 | PIN authentication (register/login), EncryptedSharedPreferences + Keystore, session gating | ✅ |
| 3 | Room schema (plants, scans, history, records), DAOs, repositories, Plants CRUD | ✅ |
| 4 | Trained Swin-Tiny TFLite model + occlusion-sensitivity XAI engine; signed release APK (v1+v2+v3) | ✅ |
| 5 | CameraX live scan, capture → inference → result flow, persist ScanEntity + attach to plant | ✅ |
| 6 | Dashboard stats, History feed, Images gallery, Records + CSV/PDF export | 🔜 Next (Dashboard + History done) |
| 7 | Treatment suggestions engine + per-disease knowledge base | ⏳ |
| 8 | Polish, full test pass, R8/release build, store readiness | ⏳ |

See [`docs/ROADMAP.md`](docs/ROADMAP.md) for the per-phase detail.

---

## Contributing / development notes

- The PIN and any derived auth material are **never** backed up (see `res/xml/backup_rules.xml`).
- `local.properties` (SDK path) and signing keystrokes are git-ignored; never commit them.
- Every screen that is not yet implemented shows a `PhasePlaceholder` stating the phase number —
  there is no fake "loading" UI pretending features exist.
