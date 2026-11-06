# Model Card — LeafGuard DINOv2 v3.5.0.0 Plant-Disease Classifier

> Following the *Model Cards for Model Reporting* framework (Mitchell et al., 2019). This card
> documents the **primary on-device classifier** shipped in the LeafGuard Android app. It is the
> exact byte buffer the app's TFLite `Interpreter` executes; verify any copy against the pinned
> SHA-256 below. The training-data lineage is documented separately in [`DATASHEET.md`](DATASHEET.md).

| Field | Value |
| --- | --- |
| Model name | LeafGuard DINOv2 v3.5.0.0 plant-disease classifier |
| Model version | 3.5.0.0 (shipped in app release 2.6.0.0, `versionCode` 11) |
| Model date | 2026 |
| Owner / author | Kushagra Dubey |
| License | MIT (project code and the trained TFLite artifact). Training data carries its own license — see DATASHEET.md. |
| Point of contact | GitHub issues — `K-dubey09/Plant-disease-detection-system-XAI` |

## Model Details

LeafGuard's primary classifier is a **DINOv2 ViT-S/14** vision transformer fine-tuned for 38-class
plant leaf disease classification. The backbone is Meta AI's self-supervised DINOv2 ViT-S/14
(patch size 14, self-supervised pretraining on ImageNet), to which a 38-way classification head is
attached. The fine-tune recipe — developed through the seven public Kaggle iterations — combines:
(1) a **gradient-reversal domain-adversarial head (DANN)** to close the lab→field domain gap,
(2) **progressive layer-wise unfreezing** with layer-wise learning-rate decay, (3) **multi-crop
consistency regularization**, (4) **CutMix / MixUp** augmentation, (5) **3× field-image
oversampling**, and (6) **test-time augmentation** (orig + horizontal flip + 0.875 central crop,
softmax-averaged).

The trained PyTorch model is exported **PyTorch → ONNX → TFLite (FP32)** via `onnx2tf`. ImageNet
normalization (`Sub(mean) / Div(std)`) and the final `Softmax` are baked into the graph as ONNX
nodes, so the on-device TFLite `Interpreter` consumes raw `[0,1]` pixels and emits real
probabilities. The conversion is verified **numerically equivalent** to the source ONNX (argmax
agreement, max|Δ| ≈ 1e-6), not bit-identical — operator fusion and accumulation order differ
across PyTorch, ONNX Runtime, and TFLite float paths.

- **Artifact path:** `app/src/main/assets/models/plant_disease.tflite`
- **Size:** 86,845,656 bytes (86.8 MB)
- **SHA-256:** `3660cfdbad212bcd5d307ee61e508382b8968b64d5e6f1feb63ba085267b69e7`
- **I/O contract:** input tensor `input` `[1,224,224,3] float32` in `[0,1]` (NHWC); output tensor
  `probabilities` `[1,38] float32` softmax.
- **Integrity guard:** the SHA-256 is pinned in `ModelIntegrity.EXPECTED_SHA256`; the result screen
  surfaces a banner if the executed buffer does not match (e.g. it was modified after signing).
- **Reproducibility:** re-running the final Kaggle notebook
  ([`leafguard-swin-finetune-v3-5-0-0`](https://www.kaggle.com/code/kushagradubey09/leafguard-swin-finetune-v3-5-0-0))
  regenerates the PyTorch checkpoint and ONNX graph; `models/conversion/convert.py` documents the
  ONNX→TFLite conversion recipe (originally authored for the earlier Swin model; the DINOv2 path
  follows the same graph-surgery: ImageNet-norm + Softmax baked in, `dynamo=False`).

## Intended Use

**Primary intended users.** Gardeners, smallholder farmers, agriculture students, and extension
workers who own an Android 8.0+ (API 26) device with ≥ 6 GB RAM.

**Primary intended use.** An on-device, fully offline assistive screening tool that, given a clear
photo of a single leaf, suggests the most likely disease from the 38 supported PlantVillage
classes, localizes the evidence with an occlusion-sensitivity heatmap, and offers IPM-aligned
treatment guidance. The tool is designed to raise an early flag and point the user toward next
steps — not to replace laboratory or extension-service confirmation.

**Out-of-scope uses.** (a) Making spray, harvest, or crop-insurance decisions solely on the
model's output. (b) Classifying species or diseases outside the 38 trained classes, or images that
are not a single leaf filling most of the frame. (c) Use as a medical, regulatory, quarantine, or
phytosanitary authority. (d) Deployment on low-RAM (< 6 GB) or sub-Android-8.0 devices. (e) Any
use that uploads leaf images to a server or redistributes the model for cloud inference — the app
is fully offline by design, and the model is not licensed for that.

## Training Data

The classifier was fine-tuned on a union of the public **PlantVillage** dataset (54,306 images, 38
classes, 14 crops — lab-style detached leaves on plain backgrounds; see DATASHEET.md for provenance
and license) and an **author-collected field set** of real-world leaf photographs exhibiting the
deployment domain (varying backgrounds, natural lighting, multiple leaves, non-canonical angles).
Field images are oversampled 3× during training to counter their smaller size and the lab set's
class imbalance, and a DANN gradient-reversal head aligns the two feature distributions so the
classifier does not simply memorize PlantVillage's plain backgrounds. CutMix/MixUp and multi-crop
consistency regularization further regularize the field partition. The seven-iteration training arc
(field accuracy 11% → 49% → 34% → 67.3% → 86.7%) is documented in the seven public Kaggle
notebooks; the failed v2.x iterations are kept public deliberately so the final 86.7% does not
appear to have appeared by magic.

## Metrics

**Chosen metrics.** Top-1 accuracy on three held-out validation splits — field, lab, and mixed —
plus per-class precision and recall on the mixed split. Accuracy is the headline because the
downstream use is triage where overall correctness matters most; per-class precision/recall is
reported to expose the confusion and imbalance that matter for trust. 3-view TTA is applied at
evaluation to match the on-device inference path, so reported numbers track what a user actually
experiences.

**Reported (DINOv2 v3.5.0.0, with 3-view TTA):**

| Split | Held-out size | Top-1 accuracy |
| --- | ---: | ---: |
| Field | 525 | **86.7%** (0.8667) |
| Lab | 1,520 | 99.3% (0.9934) |
| Mixed | 2,045 | 96.1% (0.9609) |

`best_field_tta` = 0.8667. Per-class precision/recall is high on well-represented classes (e.g.
Apple scab 0.98 / 0.98, Apple Black rot 1.0 / 1.0, Apple Cedar apple rust 0.98 / 0.98). The full
38-class table is in the metrics artifact `metrics_swin_v35.json`, downloadable from the final
Kaggle notebook's output (version 1).

## Evaluation Data

The **field split (525 images)** is the most informative: it is composed of held-out real-world
field photographs not drawn from PlantVillage's lab distribution, so it directly measures the
lab→field generalization that earlier versions failed at (11–49%). The **lab split (1,520 images)**
is held-out PlantVillage-style data and measures in-distribution performance. The **mixed split
(2,045 images)** combines both and is the closest proxy to a random real-world feed. No field image
used in evaluation was used in training; oversampling, DANN alignment, and augmentation operate on
the training partition only.

## Ethical Considerations

**Misdiagnosis harm.** An 86.7% field top-1 means roughly 1 in 8 field leaves is misclassified.
For the three critical/incurable classes (citrus greening, tomato yellow leaf curl virus, tomato
mosaic virus), a false negative could delay removal of an infected plant, while a false positive
could trigger unnecessary pesticide application. Treatment suggestions are general IPM guidance,
not prescriptions.

**Domain and regional bias.** PlantVillage is lab-style and geographically narrow; the model may
perform worse on crops, lighting, disease strains, or growth stages outside that distribution.

**Out-of-distribution confidence.** The softmax output can be high on a leaf of an unsupported
species; users should treat high confidence on an unfamiliar crop with skepticism.

**Privacy.** Inference is on-device and no leaf image ever leaves the phone — a positive privacy
posture, but also a limitation (no server-side fail-over for hard cases).

**No independent expert validation.** Field labels were assigned by the author, not independently
verified by a plant pathologist (see Caveats). This is the single most important caveat for any
high-stakes use.

## Caveats and Recommendations

The explainability artifact is **occlusion sensitivity**, not Grad-CAM. TFLite's `Interpreter`
exposes neither intermediate conv activations nor gradients, so true Grad-CAM is unavailable
on-device; the 8×8 occlusion grid shows "which region mattered" only approximately and is a trust
aid, not a causal explanation. The model is trained on 38 classes only — a healthy leaf of a 39th
species is out-of-distribution, and the model will nonetheless often return a high-confidence
label.

**Recommendations.** (a) Before acting on a diagnosis for a critical decision, confirm with a local
agricultural extension service or plant pathology laboratory. (b) Capture a single leaf filling
most of the frame against a non-distracting background — the model was trained on such images.
(c) If you redistribute the model, preserve the SHA-256 integrity check and the accompanying
DATASHEET so downstream users know the data lineage. (d) When regenerating the TFLite, do not strip
the `dynamo=False` flag or the baked-in ImageNet-norm / Softmax nodes — the on-device I/O contract
depends on them. (e) Re-run independent field validation before any deployment beyond the original
14 crops / 38 classes.

**Cross-version context.** This model supersedes the v2.0.0.0 Swin-Tiny ONNX classifier (67.3%
field, retired) and the v1.5.0.0 JK-TK YOLOv11 detector (not bundled). The v2.5 / v2.6 release line
ships this DINOv2 model unchanged; the v2.6.0.0 release adds transparency surfaces (this card, the
About screen, public notebooks) without altering the trained weights.
