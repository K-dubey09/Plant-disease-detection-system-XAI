# LeafGuard Model Conversion Pipeline

Converts a HuggingFace PyTorch **Swin-Tiny** plant-disease classifier to a TFLite model that
is a drop-in for `app/src/main/assets/models/plant_disease.tflite`.

This is the same shape of pipeline Google used to ship Gemma and other models to Android:
**PyTorch -> ONNX -> TFLite**, with the app's preprocessing baked into the graph so the
on-device `Interpreter` gets raw `[0,1]` pixels and returns real probabilities.

## Source model

`surgeonwz/plant-village` (HuggingFace, apache-2.0)

- SwinForImageClassification (Swin-Tiny, patch4-window7-224), ~28M params, ~110 MB
- 38 PlantVillage classes, 224x224 input
- **98.59% HF validation accuracy**
- Preprocessor: rescale 1/255, normalize ImageNet mean=[0.485,0.456,0.406] std=[0.229,0.224,0.225]
- Class order matches `app/src/main/assets/models/labels.txt` exactly (no remapping)

Targeted at 6GB+ RAM Android devices (the model is ~112 MB in FP32; well within that budget).

## Files

| File | Purpose |
|------|---------|
| `convert.py` | End-to-end: download HF weights, export to ONNX (`dynamo=False`), graph-surgery (ImageNet norm + Softmax), onnx2tf -> TFLite, verify, benchmark, write metadata. |
| `requirements.txt` | Python deps (CPU-only torch + tensorflow-cpu). |

## Run

```bash
pip install -r models/conversion/requirements.txt
python models/conversion/convert.py
```

Produces (under the repo root, gitignored):

```
models/source/pytorch_model.bin          # downloaded HF weights (~110 MB)
models/onnx/plant_disease_raw.onnx       # raw ONNX (NCHW, [-1,1]/ImageNet-normalized, logits)
models/onnx/plant_disease_final.onnx     # surgically modified ([0,1] input, softmax output)
models/saved_model/                      # onnx2tf intermediates + FP32/FP16/INT8 TFLite variants
models/tflite/plant_disease_fp32.tflite  # SHIPPED (~112 MB)
models/metadata/conversion_metadata.json
```

## Why bake the preprocessing into the graph

`DiseaseClassifier.bitmapToInput` produces `[1, 224, 224, 3]` float32 with pixels in `[0, 1]`
(raw ARGB / 255). The HF Swin model expects ImageNet-normalized input and outputs logits. To
make the TFLite a true drop-in, `convert.py` inserts ONNX nodes **inside the graph**:

1. `Sub(mean)` + `Div(std)` on the input: `[0,1] -> ImageNet-normalized`
2. `Softmax` on the output: logits -> probabilities (so `topConfidence` is a real probability)

`onnx2tf` transposes NCHW -> NHWC, so the final `.tflite` takes NHWC `[0,1]` and returns
`[1,38]` softmax probabilities. No app code changes required.

## Why `dynamo=False` (important)

torch 2.9+'s default dynamo-based ONNX exporter lowers Swin's window-attention partitioning
to an ONNX `Loop` op. onnx2tf's flatbuffer-direct path cannot lower `Loop WHILE` and fails with
"Loop WHILE lowering currently requires explicit max_trip_count and condition inputs". The
legacy TorchScript-based exporter (`dynamo=False`) traces Swin's static window partitions into
a flat, Loop-free graph that onnx2tf converts cleanly. This is set in `convert.py` and must not
be removed.

## Verification (FP32 shipped)

| Check | Result |
|-------|--------|
| ONNX vs PyTorch max abs diff | 2.38e-06 |
| TFLite vs ONNX mean max abs diff | 4.42e-08 |
| TFLite vs ONNX argmax match | 8 / 8 |
| Softmax output sums to 1.0 | yes |
| Input / output shape | [1,224,224,3] / [1,38] |
| CPU latency (2 threads) | ~94 ms / inference |
| Size | ~112 MB (FP32, under the 6GB-RAM-device budget) |

## INT8 / FP16 variants

onnx2tf also emits FP16 (~56 MB) and INT8 dynamic-range (~111 MB) TFLite variants, but neither
is usable on-device in this environment: the FP16 model hits a TFLite XNNPACK conv input-dtype
mismatch, and the INT8 dynamic-range model produces NaN outputs (onnx2tf's flatbuffer-direct
quantization does not calibrate Swin's transformer activations correctly). FP32 is the shipped
variant. If a smaller model is ever needed for lower-end devices, rebuild in native Keras +
transfer the HF weights, then quantize via `tf.lite.TFLiteConverter` with `Optimize.DEFAULT`.

## XAI

The app uses **occlusion-sensitivity saliency** (`app/src/main/java/com/leafguard/app/data/inference/SaliencyGenerator.kt`):
an 8x8 grid (64 forward passes) where each cell is greyed out and the predicted-class confidence
drop is recorded. Model-agnostic, works directly with the TFLite `Interpreter` (no gradients or
internal activations needed, which TFLite does not expose). At ~94 ms/inference, a full
explanation takes roughly 6 seconds on-device -- acceptable for a post-scan explanation step.

## CI integration

`.github/workflows/android.yml` pulls the trained model from the private Kaggle dataset
(`kushagradubey09/plant-disease-xai-models`) and installs it into
`app/src/main/assets/models/plant_disease.tflite` before building the APK, overwriting the
untrained demo model committed in the repo. Requires repo secrets `KAGGLE_USERNAME` and
`KAGGLE_KEY`.

## Previous model (MobileNetV2, superseded)

The first shipped model was `linkanjarad/mobilenet_v2_1.0_224-plant-disease-identification`
(MobileNetV2, 78.6% accuracy, 9 MB). It is superseded by the Swin-Tiny model (98.59% accuracy)
for the 6GB+ RAM target. The MobileNetV2 conversion script is preserved in git history
(commit d4f4960) as a smaller-footprint fallback for low-RAM devices.
