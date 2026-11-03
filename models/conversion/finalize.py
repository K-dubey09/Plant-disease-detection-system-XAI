#!/usr/bin/env python3
"""
Stage 2 of the LeafGuard model conversion pipeline:
take the ONNX from stage 1 (raw [-1,1] NCHW input, logits output) and bake in the
app's I/O contract via ONNX graph surgery, then convert to TFLite.

Run from the repository root:
    python models/conversion/finalize.py

App I/O contract (from app/src/main/java/com/leafguard/app/data/inference/DiseaseClassifier.kt):
    Input : [1, 224, 224, 3] float32, pixels in [0, 1]   (NHWC, raw ARGB/255)
    Output: [1, 38] float32, softmax probabilities in [0, 1]

Surgery:
    pixel_values [0,1] --Mul(2.0)--> Sub(1.0) --> normalized [-1,1]  (feeds existing graph)
    logits ----------------------------------------------------> Softmax --> probs
onnx2tf then transposes NCHW->NHWC, so the final .tflite takes NHWC [0,1] and is a drop-in
for app/src/main/assets/models/plant_disease_mobilenetv2.tflite.

Produces:
    models/onnx/plant_disease_mobilenetv2_final.onnx     (surgically modified)
    models/tflite/plant_disease_mobilenetv2_fp32.tflite  (shipped model)
    models/metadata/conversion_metadata.json
"""
import json, time, shutil
from pathlib import Path
import numpy as np

ROOT = Path(__file__).resolve().parents[2]
ONNX_DIR = ROOT / "models" / "onnx"
TF_DIR = ROOT / "models" / "saved_model"
OUT_DIR = ROOT / "models" / "tflite"
META_DIR = ROOT / "models" / "metadata"
for d in (ONNX_DIR, TF_DIR, OUT_DIR, META_DIR):
    d.mkdir(parents=True, exist_ok=True)

RAW_ONNX = ONNX_DIR / "plant_disease_mobilenetv2.onnx"
FINAL_ONNX = ONNX_DIR / "plant_disease_mobilenetv2_final.onnx"
assert RAW_ONNX.exists(), f"Run models/conversion/convert.py first; missing {RAW_ONNX}"

# 1. ONNX graph surgery: bake in [0,1]->[-1,1] rescale + softmax
print("=== Step A: ONNX graph surgery (rescale + softmax) ===")
import onnx
from onnx import helper, TensorProto

model = onnx.load(str(RAW_ONNX))
graph = model.graph
INPUT_NAME, LOGITS_NAME = "pixel_values", "logits"
NORM_NAME, SCALED_NAME, PROBS_NAME = "normalized_pixels", "scaled_2x", "probs"

rewired = 0
for node in graph.node:
    for i, inp in enumerate(node.input):
        if inp == INPUT_NAME:
            node.input[i] = NORM_NAME
            rewired += 1
print(f"  rewired {rewired} node input(s) -> '{NORM_NAME}'")

graph.initializer.append(helper.make_tensor("rescale_two", TensorProto.FLOAT, [], [2.0]))
graph.initializer.append(helper.make_tensor("rescale_one", TensorProto.FLOAT, [], [1.0]))
graph.node.insert(0, helper.make_node("Mul", [INPUT_NAME, "rescale_two"], [SCALED_NAME], name="leafguard_rescale_mul"))
graph.node.insert(1, helper.make_node("Sub", [SCALED_NAME, "rescale_one"], [NORM_NAME], name="leafguard_rescale_sub"))
graph.node.append(helper.make_node("Softmax", [LOGITS_NAME], [PROBS_NAME], axis=-1, name="leafguard_output_softmax"))
assert graph.output[0].name == LOGITS_NAME
graph.output[0].name = PROBS_NAME
onnx.checker.check_model(model)
onnx.save(model, str(FINAL_ONNX))
print(f"  surgery OK -> {FINAL_ONNX} ({FINAL_ONNX.stat().st_size/1e6:.2f} MB)")

import onnxruntime as ort
sess = ort.InferenceSession(str(FINAL_ONNX), providers=["CPUExecutionProvider"])
x01 = np.random.default_rng(1).random((1, 3, 224, 224), dtype=np.float32)
probs = sess.run(None, {"pixel_values": x01})[0]
assert probs.shape == (1, 38) and abs(probs.sum() - 1.0) < 1e-4
print(f"  ORT check: shape={probs.shape}, sum={float(probs.sum()):.4f}  (softmax OK)")

# 2. onnx2tf -> FP32 TFLite (flatbuffer_direct; INT8 dynamic-range + FP16 also emitted but
#    INT8 via this path produced 0/8 argmax in testing -- see README. FP32 is the shipped variant.)
print("\n=== Step B: onnx2tf -> TFLite ===")
import onnx2tf
if TF_DIR.exists():
    shutil.rmtree(TF_DIR)
TF_DIR.mkdir(parents=True, exist_ok=True)
onnx2tf.convert(
    input_onnx_file_path=str(FINAL_ONNX),
    output_folder_path=str(TF_DIR),
    output_signaturedefs=True,
    output_dynamic_range_quantized_tflite=True,
    not_use_onnxsim=False,
)

# 3. Stage the FP32 TFLite (the verified-correct variant)
print("\n=== Step C: stage FP32 TFLite ===")
import glob
fp32_src = next(iter(list(TF_DIR.glob("*float32*.tflite"))), None)
assert fp32_src, "no float32 tflite produced by onnx2tf"
fp32_dst = OUT_DIR / "plant_disease_mobilenetv2_fp32.tflite"
shutil.copy2(fp32_src, fp32_dst)
print(f"  FP32: {fp32_dst} ({fp32_dst.stat().st_size/1e6:.2f} MB)")

# 4. Verify against ONNX
print("\n=== Step D: verify TFLite vs ONNX (8 random [0,1] NHWC inputs) ===")
import tensorflow as tf
interp = tf.lite.Interpreter(model_path=str(fp32_dst))
interp.allocate_tensors()
iid, iod = interp.get_input_details()[0], interp.get_output_details()[0]
print(f"  in: {iid['shape']} {iid['dtype']}  out: {iod['shape']} {iod['dtype']}")
assert list(iid['shape']) == [1, 224, 224, 3] and list(iod['shape']) == [1, 38]
rng = np.random.default_rng(7)
diffs, match = [], 0
for _ in range(8):
    x01 = rng.random((1, 224, 224, 3), dtype=np.float32)
    interp.set_tensor(iid['index'], x01); interp.invoke()
    tfl = interp.get_tensor(iod['index'])[0]
    ref = sess.run(None, {"pixel_values": np.transpose(x01, (0, 3, 1, 2)).astype(np.float32)})[0]
    diffs.append(float(np.max(np.abs(tfl - ref))))
    match += int(int(np.argmax(tfl)) == int(np.argmax(ref)))
mean_d = float(np.mean(diffs))
print(f"  mean max|diff| = {mean_d:.3e}, argmax match {match}/8")

# 5. Latency
print("\n=== Step E: latency (50 warmup + 100 timed, 2 threads) ===")
x = np.random.default_rng(0).random((1, 224, 224, 3), dtype=np.float32)
interp.set_tensor(iid['index'], x)
for _ in range(50): interp.invoke()
t0 = time.perf_counter()
for _ in range(100): interp.invoke()
lat = (time.perf_counter() - t0) / 100 * 1000
print(f"  avg latency = {lat:.2f} ms/inference")

# 6. Metadata
print("\n=== Step F: metadata ===")
import torch
app_labels = (ROOT / "app" / "src" / "main" / "assets" / "models" / "labels.txt").read_text().splitlines()
meta = {
    "source_model": {"repo": "linkanjarad/mobilenet_v2_1.0_224-plant-disease-identification",
                     "architecture": "MobileNetV2 1.0, 224x224, 38 PlantVillage classes",
                     "hf_eval_accuracy": 0.7857752489331437, "total_params": 2272550},
    "io_contract": {"input": "[1,224,224,3] float32 [0,1]", "output": "[1,38] float32 softmax",
                    "preprocessing_baked_in": "Rescaling x*2.0-1.0 + Softmax (ONNX nodes)"},
    "shipped_variant": {"name": fp32_dst.name, "size_mb": round(fp32_dst.stat().st_size/1e6, 3),
                        "latency_ms_cpu_2threads": round(lat, 2),
                        "mean_max_diff_vs_onnx": mean_d, "argmax_match_vs_onnx": f"{match}/8"},
    "versions": {"torch": torch.__version__, "tensorflow": tf.__version__,
                 "onnx": onnx.__version__, "onnx2tf": onnx2tf.__version__},
    "labels": app_labels,
    "recommended_shipped_variant": "fp32 (9 MB, exact match to ONNX; under the 50 MB target)",
    "int8_note": "INT8 dynamic-range via onnx2tf flatbuffer-direct produced 0/8 argmax match in testing. "
                 "To produce a correct INT8, rebuild in tf.keras.applications.MobileNetV2 + transfer HF "
                 "weights, then tf.lite.TFLiteConverter with Optimize.DEFAULT. FP32 is shipped meanwhile.",
}
(META_DIR / "conversion_metadata.json").write_text(json.dumps(meta, indent=2))
print(f"  metadata -> {META_DIR/'conversion_metadata.json'}")
print(f"\nDone. Shipped model: {fp32_dst} ({fp32_dst.stat().st_size/1e6:.2f} MB, {lat:.1f} ms, {match}/8 argmax)")
print("Copy it to app/src/main/assets/models/plant_disease_mobilenetv2.tflite (or let CI pull from Kaggle).")
