# LeafGuard — Explainable Plant Disease Detection for Android

LeafGuard diagnoses plant leaf diseases **on-device** with a DINOv2 ViT-S/14 transformer and
explains every prediction with an **occlusion-sensitivity** saliency heatmap. The user registers
once with a 6-digit PIN, tracks individual plants, scans leaves in real time, and builds a
per-plant disease history with actionable, stage-aware treatment suggestions. No leaf ever leaves
the device.

> **Current: v2.7.0.0** — reliability + export release. The DINOv2 v3.5 primary classifier
> (86.7% field / 99.3% lab / 96.1% mixed) is **unchanged**; new: a per-plant scan timeline on Plant
> detail (the "Checks", recent-to-last, with a progress summary), CSV/PDF export via the Storage
> Access Framework (the user picks Downloads) with a completion notification, an About screen that
> reads version + SHA from code, and a maintained [`CHANGELOG.md`](CHANGELOG.md). Build the signed
> release APK with `./gradlew assembleRelease` (or via CI); install over v2.6.0.0 — same release
> keystore. CI is green on `main`.

---

## Model accuracy across releases

| Release | Primary model | Field | Lab | Mixed | On-device? |
| --- | --- | ---: | ---: | ---: | --- |
| v1.5.0.0 | JK-TK YOLOv11 detector (116 cls) | — | — | — | 228 MB, not bundled |
| v2.0.0.0 | Swin-Tiny ONNX (ImageNet base) via ORT | 67.3% | 99.3% | 94.5% | 107 MB, .gitignore'd (broke fresh builds) |
| **v2.5.0.0** | **DINOv2 ViT-S/14 v3.5 (TFLite, 3-view TTA)** | **86.7%** | **99.3%** | **96.1%** | **86.8 MB, committed** |
| **v2.5.1.0** | DINOv2 v3.5 (unchanged) + result-screen UX | **86.7%** | **99.3%** | **96.1%** | **86.8 MB, committed** |
| **v2.6.0.0** | DINOv2 v3.5 (unchanged) + About screen + disclosures | **86.7%** | **99.3%** | **96.1%** | **86.8 MB, committed** |
| **v2.7.0.0** | DINOv2 v3.5 (unchanged) + per-plant timeline + SAF export | **86.7%** | **99.3%** | **96.1%** | **86.8 MB, committed** |

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

1. Open the repository's **Releases** page and download the
   [v2.7.0.0 release](https://github.com/K-dubey09/Plant-disease-detection-system-XAI/releases/tag/v2.7.0.0)
   APK (~112 MB) — `LeafGuard-v2.7.0.0-release.apk`.
2. On the device (Android 8.0+), tap the downloaded `.apk` and allow **Install unknown apps** for
   your browser/files app, then confirm. **Install over v2.5.1.0 / v2.6.0.0** — same release
   keystore, so your scans and plants carry over (uninstalling would erase them).
3. First launch opens the Register screen.

The APK is signed with **v1 + v2 + v3** schemes (forced in `build.gradle.kts`; the CI
`Verify release APK is signed` step asserts v1 + v2).

---

## Model card — DINOv2 v3.5.0.0

- **Architecture:** DINOv2 ViT-S/14 (self-supervised ImageNet base) + 38-class PlantVillage head.
- **Fine-tune:** Kaggle notebook
  [`kushagradubey09/leafguard-swin-finetune-v3-5-0-0`](https://www.kaggle.com/code/kushagradubey09/leafguard-swin-finetune-v3-5-0-0)
  — DINOv2 base + DANN domain-adversarial training + multi-crop + progressive layer unfreeze +
  CutMix/MixUp + field oversampling + layer-wise LR decay + TTA. The six earlier notebooks
  (including the failed v2.x iterations) are public under the same account; see
  "Model and code availability" below.
- **Conversion:** PyTorch → ONNX → TFLite (FP32) via `onnx2tf`. ImageNet normalization and the final
  Softmax are **baked into the graph**, so the app feeds raw `[0,1]` pixels and reads real
  probabilities. Verified **numerically equivalent** (not bit-identical) to the source ONNX in ONNX
  Runtime: argmax 3/3, max|diff| = 1e-6 (well below the 1e-3 threshold at which softmax ranking
  could flip). PyTorch, ONNX Runtime, and TFLite float paths differ in operator fusion and
  accumulation order, so bit-equality is not expected; numerical equivalence is the validated claim.
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
artifacts, and publish the signed APK to the GitHub Release. The trained DINOv2 TFLite
is committed, so CI builds straight from the checkout — no Kaggle pull.

---

## Model and code availability

A reviewer can follow the whole chain — **dataset → preprocessing → training → evaluation →
conversion → on-device inference** — without contacting the author, because every step is either a
committed artifact or a public notebook.

- **Source code + deployed model:** this repository. The shipped 86.8 MB
  `plant_disease.tflite` is at `app/src/main/assets/models/plant_disease.tflite` — the exact byte
  buffer the on-device interpreter executes. Verify a downloaded copy against the pinned SHA-256
  (`3660cfdb…`, full digest in `ModelIntegrity.EXPECTED_SHA256`).
- **Trained weights:** re-running the final Kaggle notebook regenerates the PyTorch checkpoint and
  the ONNX graph; `models/conversion/convert.py` (with `requirements.txt`) then converts ONNX →
  TFLite with ImageNet normalization and softmax baked in. The TFLite graph already committed in the
  APK assets is the shipped one.
- **Training notebooks (including the failed experiments):** the seven Kaggle notebooks that produced
  every checkpoint are public, oldest → newest. The failed v2.x iterations (field accuracy
  11% → 49% → 34%) are kept public on purpose — they show how the model evolved and prevent the final
  86.7% from looking like it appeared magically.

  | Kaggle slug | Role | Field |
  | --- | --- | ---: |
  | `leafguard-finetune-v1` | mobile baseline (no field) | — |
  | `leafguard-swin-finetune-v1` | multi-source PV pool (lab-only) | — |
  | `leafguard-swin-finetune-v2` | first field data (lab drowning) | 11.0% |
  | `leafguard-swin-finetune-v2-1` | cap enforcement (underfit) | 49.0% |
  | `leafguard-swin-finetune-v2-2` | three-stage schedule (PV ceiling) | 34.0% |
  | `leafguard-swin-finetune-v3` | ImageNet pivot | 67.3% |
  | `leafguard-swin-finetune-v3-5-0-0` | DINOv2 + DANN (final) | **86.7%** |

  Each is at `https://www.kaggle.com/code/kushagradubey09/<slug>`; the final one is
  [`leafguard-swin-finetune-v3-5-0-0`](https://www.kaggle.com/code/kushagradubey09/leafguard-swin-finetune-v3-5-0-0).
  The per-notebook methodology and diagnosed failures are in the project report's "Per-Version
  Training Notebook Summaries" appendix (`docs/IEEE_format/LeafGuard_IEEE_Project_Report.pdf`).

## AI-assisted development

AI coding assistants (large language models) were used for routine scaffolding — boilerplate Compose
layouts, Gradle/CI configuration, conversion-script skeletons, and first-draft prose. All generated
material was reviewed, revised, tested, and integrated by the author, who is responsible for every
committed line. The model architecture, the domain-adaptation recipe, the experimental methodology,
the ablation analysis, and all engineering and validation decisions are the author's own. This
disclosure is surfaced in-app on the **About** screen (v2.6.0.0) and in
[`docs/AI_ASSISTANCE.md`](docs/AI_ASSISTANCE.md). The project is not presented as if every
implementation detail were manually written, and the design and validation are not claimed to be
AI-generated.

---

## Changelog

The full, dated history lives in [`CHANGELOG.md`](CHANGELOG.md) (every change recorded with date,
time, and contributor). Summary:

- **v2.7.0.0** — Reliability + export release. DINOv2 model **unchanged**. Plant detail now lists
  that plant's scans (recent-to-last) with a progress summary + tap-to-open; CSV/PDF export writes
  to a user-picked location via the Storage Access Framework (Downloads) and posts a completion
  notification (`POST_NOTIFICATIONS`); the About screen reads version + SHA from `BuildConfig` /
  `ModelIntegrity`; camera capture scratch file cleaned up; stale Phase-1 comments corrected.
  `versionCode` 11 → 12. **Scan-history persistence was already correct in source** (every
  classification calls `saveScan` into a persistent Room file); if you saw history vanish on close,
  you were on a pre-Phase-9 build — install v2.7.0.0.
- **v2.6.0.0** — Transparency & reproducibility release. DINOv2 model **unchanged** (86.7% field).
  New in-app **About** screen (version, model SHA-256 prefix, model/code availability,
  AI-assisted-development disclosure); seven public Kaggle training notebooks (including the failed
  v2.x iterations); updated README + `docs/ARCHITECTURE.md` + new `docs/AI_ASSISTANCE.md`; the
  "bit-identical" conversion claim corrected to "numerically equivalent" throughout the docs.
  `versionCode` 10 → 11.
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
