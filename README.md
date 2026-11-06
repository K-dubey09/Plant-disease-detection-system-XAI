# LeafGuard — Explainable Plant Disease Detection for Android

LeafGuard diagnoses plant leaf diseases **on-device** with a DINOv2 ViT-S/14 transformer and
explains every prediction with an **occlusion-sensitivity** saliency heatmap. The user registers
once with a 6-digit PIN, tracks individual plants, scans leaves in real time, and builds a
per-plant disease history with actionable, stage-aware treatment suggestions. No leaf ever leaves
the device.

> **Current: v2.5.1.0** — DINOv2 v3.5 primary classifier (86.7% field / 99.3% lab / 96.1% mixed)
> with 3-view test-time augmentation. The signed release APK is on the
> [v2.5.1.0 GitHub Release](https://github.com/K-dubey09/Plant-disease-detection-system-XAI/releases/tag/v2.5.1.0)
> as a raw `.apk` — download and tap to install on Android 8.0+. CI is green on `main`.

---

## Model accuracy across releases

| Release | Primary model | Field | Lab | Mixed | On-device? |
| --- | --- | ---: | ---: | ---: | --- |
| v1.5.0.0 | JK-TK YOLOv11 detector (116 cls) | — | — | — | 228 MB, not bundled |
| v2.0.0.0 | Swin-Tiny ONNX (ImageNet base) via ORT | 67.3% | 99.3% | 94.5% | 107 MB, .gitignore'd (broke fresh builds) |
| **v2.5.0.0** | **DINOv2 ViT-S/14 v3.5 (TFLite, 3-view TTA)** | **86.7%** | **99.3%** | **96.1%** | **86.8 MB, committed** |
| **v2.5.1.0** | DINOv2 v3.5 (unchanged) + result-screen UX | **86.7%** | **99.3%** | **96.1%** | **86.8 MB, committed** |

The v2.5 line closes the PlantVillage→field domain gap that capped earlier models, and ships the
model **committed in the repo** (under GitHub's 100 MB limit) so every checkout builds the real
classifier — no Kaggle pull, no missing-asset landmine.

---

## Why this design

| Constraint | Decision |
| --- | --- |
| Android, 6 GB+ RAM target | Native Kotlin + Jetpack Compose; `minSdk = 26` (Android 8.0 covers every 6 GB+ device) |
| Best field accuracy on-device | DINOv2 ViT-S/14 (self-supervised ImageNet base → fine-tuned on PlantVillage + field-augmented data) exported to TFLite FP32. 3-view TTA (orig + hflip + scale0.875) on the headline path replicates the eval recipe, so on-device field accuracy tracks the validated 86.7%. |
| Trust the model output | ModelIntegrity SHA-256-guards the exact `.tflite` bytes the Interpreter executes; the result screen surfaces a banner if the buffer was modified after signing. A model-info card states which model + TTA produced each diagnosis. |
| Explainable AI | Occlusion-sensitivity heatmap (model-agnostic — TFLite's `Interpreter` exposes no conv activations/gradients, so true Grad-CAM is unavailable on-device): an 8×8 grid is greyed cell-by-cell and the predicted class's confidence drop per cell becomes the jet-colormap overlay on the captured leaf. |
| First-launch register, PIN login after | PIN stored in `EncryptedSharedPreferences` keyed to the Android Keystore |
| Production, not prototype | MVVM + Clean Architecture, Hilt DI, Room persistence, CameraX, type-safe navigation, unit tests, R8 minification, CI |

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

## Features

- **Scan** — CameraX live capture or gallery pick; a single DINOv2 forward pass (3-view TTA) returns
  the diagnosis immediately; the occlusion heatmap streams in afterward.
- **Result** — headline condition + a qualitative confidence tier (High / Moderate / Low) + a model
  info card (DINOv2 v3.5 · 3-view TTA) + a model-integrity banner when the SHA mismatches + the
  source/heatmap pair + top-3 alternatives with mini bars + stage-aware treatment + prevention +
  "Add to plant".
- **Plants & growth tracking** — track individual plants; link any scan to a plant so it joins that
  plant's per-plant Records timeline.
- **History** — every scan persisted in Room; tap to re-open the result.
- **Records** — export a plant's history to CSV / PDF.
- **Dashboard** — at-a-glance stats.

---

## Build & run

Requires Android Studio Koala/Ladybug (or later) and JDK 17.

1. Clone and open the project root in Android Studio. It syncs the Gradle wrapper (Gradle 8.9).
2. Let Android Studio install **Android SDK Platform 35** and **Build-Tools 35.x** if prompted.
3. Select a device or emulator running **API 26+**.
4. Run the `app` configuration.

Command-line (Android SDK installed and `local.properties` pointing at it):

```bash
./gradlew assembleDebug          # build the debug APK
./gradlew assembleRelease        # build the signed release APK (needs KEYSTORE_* env vars)
./gradlew test                   # JVM unit tests
./gradlew lintDebug              # Android Lint
```

`settings.gradle.kts` puts the Tencent + Huawei public Maven mirrors first, so the build resolves
dependencies even when Maven Central rate-limits (429). The 86.8 MB DINOv2 `plant_disease.tflite`
is committed in `app/src/main/assets/models/` — no Kaggle pull needed.

---

## Install the release APK

1. Open the repository's **Releases** page and pick the
   [v2.5.1.0 release](https://github.com/K-dubey09/Plant-disease-detection-system-XAI/releases/tag/v2.5.1.0).
2. Download `LeafGuard-v2.5.1.0-release.apk` (~112 MB) directly.
3. On the device (Android 8.0+), tap the downloaded `.apk` and allow **Install unknown apps** for
   your browser/files app, then confirm.
4. First launch opens the Register screen.

The APK is signed with **v1 + v2 + v3** schemes (forced in `build.gradle.kts`; the CI
`Verify release APK is signed` step asserts v1 + v2). Install over v2.5.0.0 — same release keystore.

---

## Model card — DINOv2 v3.5.0.0

- **Architecture:** DINOv2 ViT-S/14 (self-supervised ImageNet base) + 38-class PlantVillage head.
- **Fine-tune:** Kaggle kernel `kushagradubey09/leafguard-swin-finetune-v3-5-0-0` — DINOv2 base +
  DANN domain-adversarial training + multi-crop + progressive layer unfreeze + CutMix/MixUp +
  field oversampling + layer-wise LR decay + TTA.
- **Conversion:** PyTorch → ONNX → TFLite (FP32) via `onnx2tf`. ImageNet normalization and the final
  Softmax are **baked into the graph**, so the app feeds raw `[0,1]` pixels and reads real
  probabilities. Verified bit-identical to the source ONNX in ONNX Runtime: argmax 3/3, max|diff| = 1e-6.
- **I/O contract:** input `input` `[1,224,224,3] float32` in `[0,1]` (NHWC); output `probabilities`
  `[1,38] float32` softmax. Same 38-class output order as `labels.txt` (the v2.0.0.0 canonical names).
- **Artifact:** `app/src/main/assets/models/plant_disease.tflite` — 86.8 MB (86,845,656 bytes),
  committed (under GitHub's 100 MB limit).
- **Integrity:** SHA-256 `3660cfdbad212bcd5d307ee61e508382b8968b64d5e6f1feb63ba085267b69e7`, pinned
  in `ModelIntegrity.EXPECTED_SHA256`.

---

## Test-time augmentation (TTA)

The headline classification path (`DiseaseClassifier.classify`) runs the exact v3.5 eval TTA recipe
— three views whose softmaxes are averaged before ranking:

1. **orig** — center-crop to a square, resize to 224.
2. **hflip** — horizontal flip of the orig view.
3. **scale0.875** — central 0.875 crop of the square, resize to 224 (a slight zoom).

This lifts on-device field accuracy toward the validated 86.7% (vs ~84% single-crop). The
64-cell occlusion heatmap uses a separate single-pass path
(`LeafClassifier.classifyForSaliency`) — TTA on every occluded patch would 3× its cost for no
explainability gain.

---

## Explainability — occlusion sensitivity

For an image-classification prediction, a saliency heatmap shows which spatial regions of the input
most raised the predicted class score, so the user can verify the model "looked at" the lesion and
not the background. LeafGuard computes this on-device with **occlusion sensitivity** rather than
Grad-CAM, because TFLite's `Interpreter` exposes neither intermediate conv activations nor
gradients. Occlusion sensitivity is model-agnostic and needs only the forward pass: an 8×8 grid is
greyed cell-by-cell and the predicted class's confidence drop per cell becomes the jet-colormap
overlay. The heatmap streams in after the diagnosis (a "Generating explanation…" placeholder fills
the slot while it runs), so the user gets their diagnosis immediately.

---

## CI

`.github/workflows/android.yml` runs on every push to `main`: assembleDebug, assembleRelease,
unit test, lintDebug, verify the release APK is signed (v1 + v2), upload the debug + release APK
artifacts, and publish the signed APK to the `v2.5.1.0` GitHub Release. The trained DINOv2 TFLite
is committed, so CI builds straight from the checkout — no Kaggle pull.

---

## Changelog

- **v2.5.1.0** — Result-screen UX + model transparency: model-info card (DINOv2 v3.5 · 3-view TTA),
  model-integrity banner wired from `ModelIntegrity` (was computed but never shown), qualitative
  confidence tier (High/Moderate/Low), per-alternative mini bars, refreshed low-confidence hint.
  README rewritten. DINOv2 model unchanged.
- **v2.5.0.0** — DINOv2 ViT-S/14 v3.5.0.0 primary classifier (86.7% field, +19.4 pp over v2.0.0.0)
  with 3-view TTA. Retired the v2.0.0.0 Swin-ONNX/ORT path (107 MB, .gitignore'd) and the JK-TK
  YOLOv11 detector (228 MB, not bundled). CI workflow fixed to build from the committed model.
- **v2.0.0.0** — Swin-Tiny ONNX via ONNX Runtime (67.3% field). *(superseded)*
- **v1.5.0.0** — JK-TK YOLOv11 detector. *(superseded)*

See [`docs/ROADMAP.md`](docs/ROADMAP.md) and [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).
