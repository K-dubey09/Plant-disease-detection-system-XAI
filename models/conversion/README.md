# LeafGuard Model Conversion Pipeline

Converts a HuggingFace PyTorch MobileNetV2 plant-disease classifier to a TFLite model that
is a drop-in for `app/src/main/assets/models/plant_disease_mobilenetv2.tflite`.

This is the same shape of pipeline Google used to ship Gemma and other SLMs to Android:
**PyTorch -> ONNX -> TFLite**, with the app's preprocessing baked into the graph so the
on-device `Interpreter` gets raw `[0,1]` pixels and returns real probabilities.

## Files

| File | Purpose |
|------|---------|
| `convert.py` | Stage 1: download the HF PyTorch model, export to ONNX (opset 18), verify. |
| `finalize.py` | Stage 2: ONNX graph surgery (rescale + softmax), convert to TFLite, verify, write metadata. |
| `requirements.txt` | Python deps (CPU-only torch + tensorflow-cpu). |

## Run

```bash
pip install -r models/conversion/requirements.txt
python models/conversion/convert.py
python models/conversion/finalize.py
```

Produces (under the repo root, gitignored):

```
models/source/pytorch_model.bin          # downloaded HF weights (9.3 MB)
models/onnx/plant_disease_mobilenetv2.onnx            # raw [-1,1]/logits
models/onnx/plant_disease_mobilenetv2_final.onnx      # [0,1]/softmax (surgery)
models/tflite/plant_disease_mobilenetv2_fp32.tflite   # SHIPPED (9 MB)
models/metadata/conversion_metadata.json
```

## Source model

`linkanjarad/mobilenet_v2_1.0_224-plant-disease-identification` (HuggingFace)

- MobileNetV2 1.0, 224x224, 38 PlantVillage classes
- Reported eval accuracy: 78.6% (8 epochs)
- HF preprocessor: rescale 1/255, normalize mean=0.5 std=0.5 == `[-1, 1]`
- Class order matches `app/src/main/assets/models/labels.txt` exactly (no remapping)

## App I/O contract (why the surgery)

`DiseaseClassifier.bitmapToInput` produces `[1, 224, 224, 3]` float32 with pixels in `[0, 1]`
(raw ARGB / 255). The HF model expects `[-1, 1]`-normalized input and outputs logits. To make
the TFLite a true drop-in, `finalize.py` inserts two ONNX nodes **inside the graph**:

1. `Mul(2.0)` + `Sub(1.0)` on the input: `[0,1] -> [-1,1]`
2. `Softmax` on the output: logits -> probabilities (so `topConfidence` is a real probability,
   not a raw logit)

`onnx2tf` then transposes NCHW -> NHWC, so the final `.tflite` takes NHWC `[0,1]` and returns
`[1,38]` softmax probabilities. No app code changes required.

## Verification (FP32 shipped)

| Check | Result |
|-------|--------|
| ONNX vs PyTorch max abs diff | 4.08e-06 |
| TFLite vs ONNX mean max abs diff | 5.45e-07 |
| TFLite vs ONNX argmax match | 8 / 8 |
| Softmax output sums to 1.0 | yes |
| Input / output shape | [1,224,224,3] / [1,38] |
| CPU latency (2 threads) | 4.8 ms / inference |
| Size | 9.05 MB (under the 50 MB mobile target) |

## INT8 quantization

FP32 (9 MB) is shipped. INT8 dynamic-range (~2.5 MB) was attempted via three paths and is
currently blocked by toolchain bugs in this environment:

1. `onnx2tf output_dynamic_range_quantized_tflite=True` (flatbuffer-direct): produced 0/8 argmax
   match (bad per-channel scales + Softmax amplifying logit errors; fell back to slow reference
   kernels).
2. `onnx2tf flatbuffer_direct_output_saved_model=True` + `tf.lite.TFLiteConverter`: the onnx2tf
   SavedModel exporter crashes on MobileNetV2's depthwise convolutions
   (`input.shape.rank must be at least 5. Received rank 4`).
3. `ai-edge-torch` (Google official): requires ~1.2 GB CUDA toolkit + cudnn download and
   downgrades torch; exceeded the sandbox disk budget.

The 9 MB FP32 is well under the 50 MB mobile budget, so this is not blocking. The documented
path to a correct INT8 is: rebuild MobileNetV2 via
`tf.keras.applications.MobileNetV2(input_shape=(224,224,3), classes=38, weights=None)`, transfer
weights from the HF `state_dict` (both follow `google/mobilenet_v2_1.0_224`), wrap with
`Rescaling(2.0, -1.0)` + `Softmax`, then `tf.lite.TFLiteConverter` with `Optimize.DEFAULT`.

## XAI

The app uses **occlusion-sensitivity saliency** (`app/src/main/java/com/leafguard/app/data/inference/SaliencyGenerator.kt`):
an 8x8 grid (64 forward passes) where each cell is greyed out and the predicted-class confidence
drop is recorded. This is model-agnostic and works directly with the TFLite `Interpreter` --
TFLite does not expose conv activations or gradients, so true Grad-CAM cannot run on-device.
The converted model is fully compatible; no XAI code changes were required.

## CI integration

`.github/workflows/android.yml` pulls the trained model from a private Kaggle dataset
(`kushagradubey09/plant-disease-xai-models`) and installs it into
`app/src/main/assets/models/plant_disease_mobilenetv2.tflite` before building the APK, so the
shipped APK always uses the trained model rather than the untrained demo model committed in the
repo. Requires repo secrets `KAGGLE_USERNAME` and `KAGGLE_KEY`.
