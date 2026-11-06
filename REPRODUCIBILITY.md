# Reproducibility

This document explains how a reviewer can follow the entire LeafGuard evidence chain —
**dataset → preprocessing → training → evaluation → conversion → on-device inference** — and arrive
at the same shipped artifact, or verify the one already shipped. It complements
[`MODEL_CARD.md`](MODEL_CARD.md) (the model) and [`DATASHEET.md`](DATASHEET.md) (the data).

## TL;DR — the pinned reference artifact

The source of truth is the **committed TFLite model**, not any particular training run:

- **Path:** `app/src/main/assets/models/plant_disease.tflite`
- **Size:** 86,845,656 bytes (86.8 MB)
- **SHA-256:** `3660cfdbad212bcd5d307ee61e508382b8968b64d5e6f1feb63ba085267b69e7`
- **Pinned in code:** `ModelIntegrity.EXPECTED_SHA256`

A fresh `git clone` + `./gradlew assembleDebug` builds the APK against this exact byte buffer. Any
copy can be verified with:

```bash
sha256sum app/src/main/assets/models/plant_disease.tflite
# expect: 3660cfdbad212bcd5d307ee61e508382b8968b64d5e6f1feb63ba085267b69e7
```

The training and conversion steps regenerate the weights and the graph; the committed,
SHA-guarded TFLite is what the app actually executes.

## The evidence chain

| Stage | Where it lives | Reproducible from this repo? |
| --- | --- | :---: |
| 1. Dataset | PlantVillage (public) + author field set (see DATASHEET.md) | PlantVillage: yes (canonical mirror); field set: contact author |
| 2. Preprocessing & training | 7 public Kaggle notebooks | yes (notebooks public) |
| 3. Evaluation | Kaggle notebook output + `metrics_swin_v35.json` | yes (notebook output, version 1) |
| 4. Conversion (PyTorch→ONNX→TFLite) | `models/conversion/convert.py` + `requirements.txt` | yes |
| 5. On-device inference | `app/src/main/java/.../data/inference/*` + committed TFLite | yes (builds from checkout) |

## Build environment (Android application)

Verified from `gradle/libs.versions.toml`, `app/build.gradle.kts`, and the Gradle wrapper:

| Component | Version |
| --- | --- |
| JDK | 17 |
| Android Gradle Plugin (AGP) | 8.7.2 |
| Gradle | 8.9 (wrapper committed) |
| Kotlin | 2.0.21 |
| compileSdk / targetSdk | 35 / 35 |
| minSdk | 26 (Android 8.0) |
| Jetpack Compose BOM | 2024.10.01 |
| Hilt | 2.52 |
| Room | 2.6.1 |
| CameraX | 1.3.4 |
| TensorFlow Lite | 2.16.1 (support 0.4.4, GPU delegate 2.16.1) |
| Coil | 2.7.0 |

```bash
./gradlew assembleDebug      # debug APK
./gradlew assembleRelease    # signed release APK (needs KEYSTORE_* env vars)
./gradlew test               # 152 JVM unit tests
./gradlew lintDebug          # Android Lint
```

CI (`.github/workflows/android.yml`) runs all of the above on every push to `main`, plus an
`apksigner verify` step that asserts v1 + v2 signing, and publishes the signed APK to GitHub
Releases. The model is committed, so CI builds straight from the checkout — no Kaggle pull.

## Conversion environment (Python)

Verified from `models/conversion/requirements.txt`. CPU-only (no CUDA), so the pipeline runs in a
modest sandbox:

```text
torch>=2.14
torchvision
transformers>=4.38,<4.46
onnx>=1.15
onnxruntime
onnx2tf
tensorflow-cpu
pillow, numpy, requests
kaggle                  # for uploading the model dataset + CI pulling it during APK build
```

```bash
pip install -r models/conversion/requirements.txt
python models/conversion/convert.py
```

The conversion does graph surgery so the TFLite is a true drop-in: it inserts ONNX `Sub(mean)` /
`Div(std)` nodes (ImageNet normalization) on the input and a `Softmax` node on the output, so the
on-device `Interpreter` consumes raw `[0,1]` pixels and emits real probabilities. `onnx2tf`
transposes NCHW → NHWC. **`dynamo=False` is required** — torch 2.9+'s default dynamo exporter
lowers Swin/DINOv2 window-attention partitioning to an ONNX `Loop` op that `onnx2tf`'s
flatbuffer-direct path cannot lower; the legacy TorchScript exporter traces the static window
partitions into a flat, Loop-free graph. Do not remove this flag.

## Training environment (Kaggle)

The seven training notebooks are public under the Kaggle account `kushagradubey09`, oldest →
newest, including the failed v2.x iterations (field 11% → 49% → 34%) kept public deliberately:

| Slug | Role | Field acc. |
| --- | --- | ---: |
| `leafguard-finetune-v1` | mobile baseline | — |
| `leafguard-swin-finetune-v1` | multi-source PV pool (lab-only) | — |
| `leafguard-swin-finetune-v2` | first field data (lab drowning) | 11.0% |
| `leafguard-swin-finetune-v2-1` | cap enforcement (underfit) | 49.0% |
| `leafguard-swin-finetune-v2-2` | three-stage schedule (PV ceiling) | 34.0% |
| `leafguard-swin-finetune-v3` | ImageNet pivot | 67.3% |
| `leafguard-swin-finetune-v3-5-0-0` | DINOv2 + DANN (final) | **86.7%** |

Each notebook is at `https://www.kaggle.com/code/kushagradubey09/<slug>`; the final one is
[`leafguard-swin-finetune-v3-5-0-0`](https://www.kaggle.com/code/kushagradubey09/leafguard-swin-finetune-v3-5-0-0).
The notebooks ran on **Kaggle's GPU runtime**; the exact accelerator allocated per session is
recorded in each notebook's session metadata. The per-version hyperparameters, schedules, and
random seeds are defined in the notebook source itself — **the notebooks are the source of truth
for the training recipe**, and this document does not duplicate those values (which would drift
from the notebook). Re-running the final notebook regenerates the PyTorch checkpoint and the ONNX
graph; `convert.py` then produces the TFLite.

## Verification

**Numerical equivalence (shipped DINOv2 v3.5 TFLite vs source ONNX).** The conversion is verified
**numerically equivalent, not bit-identical** — PyTorch, ONNX Runtime, and TFLite float paths
differ in operator fusion and accumulation order, so bit-equality is neither expected nor claimed:
argmax agreement 3/3, max|Δ| ≈ 1e-6 (well below the 1e-3 threshold at which softmax ranking could
flip). This is the validated claim in the README's model card.

**Pipeline soundness (earlier Swin conversion, same pipeline).** The conversion README documents
the same graph-surgery pipeline on the earlier Swin model with tighter reference numbers, as
additional evidence the pipeline is sound: ONNX-vs-PyTorch max|Δ| = 2.38e-06; TFLite-vs-ONNX mean
max|Δ| = 4.42e-08; TFLite-vs-ONNX argmax match 8/8; softmax sums to 1.0; I/O
`[1,224,224,3]` / `[1,38]`; CPU latency ~94 ms/inference on 2 threads.

**Model integrity (runtime).** `ModelIntegrity` SHA-256-guards the exact byte buffer the
`Interpreter` executes against the pinned digest; the result screen surfaces a banner if the
buffer was modified after signing.

**Metrics (DINOv2 v3.5, with 3-view TTA).** Held-out top-1: **field 86.7% (525)**, **lab 99.3%
(1,520)**, **mixed 96.1% (2,045)**; `best_field_tta` = 0.8667. Per-class precision/recall is in
`metrics_swin_v35.json` (Kaggle notebook output, version 1).

## How to reproduce end-to-end

1. **Verify the shipped artifact** (fastest path — no GPU needed):
   `git clone` → `sha256sum app/src/main/assets/models/plant_disease.tflite` → confirm
   `3660cfdb…` → `./gradlew assembleDebug` → install on an Android 8.0+ / ≥6 GB RAM device.
2. **Reproduce the training:** open the final Kaggle notebook
   (`leafguard-swin-finetune-v3-5-0-0`) and run it top-to-bottom on a Kaggle GPU. It regenerates
   the PyTorch checkpoint and the ONNX graph. The six earlier notebooks reproduce the iteration
   arc and the diagnosed failures.
3. **Reproduce the conversion:** `pip install -r models/conversion/requirements.txt` →
   `python models/conversion/convert.py` → produces `plant_disease_fp32.tflite` (the SHIPPED
   variant) plus FP16/INT8 variants (currently not usable on-device — see MODEL_CARD Caveats).
4. **Reproduce the on-device inference path:** build the app and scan a leaf. The headline path
   (`DiseaseClassifier.classify`) runs the 3-view TTA recipe (orig + hflip + scale0.875); the
   occlusion-sensitivity heatmap uses the separate single-pass `classifyForSaliency` path.

## What is NOT reproducible from this repository alone (honest gaps)

- **The field image set** is not redistributed here (PlantVillage's and the field set's licensing
  is documented in DATASHEET.md). PlantVillage is obtainable from its canonical mirror; the field
  set requires contacting the author.
- **The field labels are not independently expert-verified.** Re-running the notebook reproduces
  the model, but the field accuracy (86.7%) is a measurement against author-assigned ground truth,
  not an expert-validated one. This is the project's primary reproducibility caveat and the top
  item in `future-proposals/06_evaluation_validation.md`.
- **Instrumented on-device tests are not in CI.** The CameraX capture → scan id and the real
  TFLite forward pass are covered by JVM/Robolectric substitutes, not a device run
  (`future-proposals/03_implementation.md` §A).
- **Reproducible-from-source APK (bit-for-bit)** is not yet guaranteed; the realistic target is
  "reproducible within a pinned toolchain" (`future-proposals/03_implementation.md` §E).
