#!/usr/bin/env python3
"""
LeafGuard model conversion: HuggingFace Swin-Tiny plant-disease model -> TFLite.

Source: surgeonwz/plant-village
  - SwinForImageClassification (Swin-Tiny, ~28M params, ~110 MB)
  - 38 PlantVillage classes, 224x224 input, 98.59% HF validation accuracy
  - Preprocessor: rescale 1/255, normalize ImageNet mean=[0.485,0.456,0.406] std=[0.229,0.224,0.225]

App I/O contract (app/src/main/java/com/leafguard/app/data/inference/DiseaseClassifier.kt):
  Input : [1, 224, 224, 3] float32, pixels in [0, 1]   (NHWC, raw ARGB/255)
  Output: [1, 38] float32, softmax probabilities in [0, 1]

Pipeline: PyTorch (.bin) -> ONNX (opset 17, dynamo=False legacy exporter)
       -> ONNX graph surgery (bake in ImageNet normalization + Softmax)
       -> onnx2tf flatbuffer-direct -> TFLite (NHWC, drop-in).

The [0,1]->ImageNet-normalized rescaling and the final Softmax are inserted as ONNX
nodes before conversion, so the app feeds raw [0,1] pixels and reads real probabilities.
No app-side preprocessing or postprocessing changes.

Run from the repository root:
    pip install -r models/conversion/requirements.txt
    python models/conversion/convert.py

Produces (under the repo root, gitignored):
    models/source/               downloaded HF weights + config
    models/onnx/                 raw + surgically-modified ONNX
    models/saved_model/          onnx2tf intermediates + TFLite variants
    models/tflite/plant_disease_fp32.tflite   SHIPPED (~112 MB)
    models/metadata/conversion_metadata.json

NOTE: dynamo=False is required. torch 2.9+'s default dynamo exporter lowers Swin's
window-attention partitioning to an ONNX Loop op that onnx2tf cannot lower
("Loop WHILE lowering requires explicit max_trip_count"). The legacy TorchScript
exporter traces Swin's static window partitions into a flat, Loop-free graph.
"""
import os, sys, json, time, shutil
from pathlib import Path
import numpy as np

ROOT = Path(__file__).resolve().parents[2]      # repo root
SRC = ROOT / "models" / "source"
ONNX_DIR = ROOT / "models" / "onnx"
TF_DIR = ROOT / "models" / "saved_model"
OUT_DIR = ROOT / "models" / "tflite"
META_DIR = ROOT / "models" / "metadata"
for d in (SRC, ONNX_DIR, TF_DIR, OUT_DIR, META_DIR):
    d.mkdir(parents=True, exist_ok=True)

HF_REPO = "surgeonwz/plant-village"
HF_BASE = f"https://huggingface.co/{HF_REPO}/resolve/main"

# 1. Download the HF model files
print("=== Step 1: download HuggingFace Swin-Tiny ===")
import requests
for fname in ["config.json", "preprocessor_config.json", "pytorch_model.bin", "README.md"]:
    dst = SRC / fname
    if dst.exists() and dst.stat().st_size > 0:
        print(f"  {fname}: present ({dst.stat().st_size} bytes)"); continue
    r = requests.get(f"{HF_BASE}/{fname}", timeout=180, stream=True)
    r.raise_for_status()
    dst.write_bytes(r.content)
    print(f"  {fname}: {dst.stat().st_size} bytes")

# 2. Load + export to ONNX (dynamo=False -- see module docstring)
print("\n=== Step 2: load PyTorch + export to ONNX (dynamo=False) ===")
import torch
from transformers import AutoModelForImageClassification, AutoConfig
cfg = AutoConfig.from_pretrained(str(SRC))
model = AutoModelForImageClassification.from_pretrained(str(SRC))
model.eval()
n_params = sum(p.numel() for p in model.parameters())
print(f"  {cfg.model_type}, {cfg.num_labels} labels, {n_params:,} params")

RAW_ONNX = ONNX_DIR / "plant_disease_raw.onnx"
dummy = torch.randn(1, 3, 224, 224, requires_grad=False)
with torch.no_grad():
    torch.onnx.export(
        model, dummy, str(RAW_ONNX),
        opset_version=17,
        input_names=["pixel_values"], output_names=["logits"],
        dynamic_axes=None, do_constant_folding=True,
        dynamo=False,   # legacy exporter; avoids Loop op from window attention
    )
print(f"  ONNX: {RAW_ONNX} ({RAW_ONNX.stat().st_size/1e6:.2f} MB)")

# Verify ONNX vs PyTorch
import onnx, onnxruntime as ort
from onnx import helper, TensorProto
onnx.checker.check_model(onnx.load(str(RAW_ONNX)))
sess = ort.InferenceSession(str(RAW_ONNX), providers=["CPUExecutionProvider"])
inp = np.random.randn(1, 3, 224, 224).astype(np.float32) * 0.5
ort_out = sess.run(None, {"pixel_values": inp})[0]
with torch.no_grad():
    pt_out = model(torch.from_numpy(inp)).logits.numpy()
diff = float(np.max(np.abs(ort_out - pt_out)))
print(f"  ONNX vs PyTorch max|diff| = {diff:.2e}  (must be < 1e-3)")
assert diff < 1e-3, "ONNX export diverged from PyTorch"

# 3. ONNX surgery: bake in ImageNet normalization + Softmax
print("\n=== Step 3: ONNX surgery (ImageNet norm + Softmax) ===")
FINAL_ONNX = ONNX_DIR / "plant_disease_final.onnx"
m = onnx.load(str(RAW_ONNX))
g = m.graph
INPUT_NAME, LOGITS_NAME = "pixel_values", "logits"
NORM_NAME, MINUS_MEAN, PROBS_NAME = "normalized_pixels", "minus_mean", "probs"

MEAN = np.array([0.485, 0.456, 0.406], dtype=np.float32).reshape(1, 3, 1, 1)
STD  = np.array([0.229, 0.224, 0.225], dtype=np.float32).reshape(1, 3, 1, 1)
g.initializer.append(helper.make_tensor("norm_mean", TensorProto.FLOAT, [1,3,1,1], MEAN.flatten().tolist()))
g.initializer.append(helper.make_tensor("norm_std",  TensorProto.FLOAT, [1,3,1,1], STD.flatten().tolist()))

for node in g.node:
    for i, n in enumerate(node.input):
        if n == INPUT_NAME:
            node.input[i] = NORM_NAME
g.node.insert(0, helper.make_node("Sub", [INPUT_NAME, "norm_mean"], [MINUS_MEAN], name="leafguard_norm_sub"))
g.node.insert(1, helper.make_node("Div", [MINUS_MEAN, "norm_std"], [NORM_NAME], name="leafguard_norm_div"))
g.node.append(helper.make_node("Softmax", [LOGITS_NAME], [PROBS_NAME], axis=-1, name="leafguard_output_softmax"))
g.output[0].name = PROBS_NAME
onnx.checker.check_model(m)
onnx.save(m, str(FINAL_ONNX))
print(f"  surgery OK -> {FINAL_ONNX} ({FINAL_ONNX.stat().st_size/1e6:.2f} MB)")

sess_f = ort.InferenceSession(str(FINAL_ONNX), providers=["CPUExecutionProvider"])
x01 = np.random.default_rng(1).random((1, 3, 224, 224), dtype=np.float32)
probs = sess_f.run(None, {"pixel_values": x01})[0]
assert probs.shape == (1, 38) and abs(probs.sum() - 1.0) < 1e-4
print(f"  ORT check: shape={probs.shape}, sum={float(probs.sum()):.4f} -> softmax OK")

# 4. onnx2tf -> TFLite
print("\n=== Step 4: onnx2tf -> TFLite ===")
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
fp32_src = next(iter(list(TF_DIR.glob("*float32*.tflite"))), None)
assert fp32_src, "no float32 tflite produced"
fp32_dst = OUT_DIR / "plant_disease_fp32.tflite"
shutil.copy2(fp32_src, fp32_dst)
print(f"  FP32 TFLite: {fp32_dst} ({fp32_dst.stat().st_size/1e6:.2f} MB)")

# 5. Verify + benchmark
print("\n=== Step 5: verify + benchmark ===")
import tensorflow as tf
interp = tf.lite.Interpreter(model_path=str(fp32_dst))
interp.allocate_tensors()
iid, iod = interp.get_input_details()[0], interp.get_output_details()[0]
assert list(iid['shape']) == [1, 224, 224, 3] and list(iod['shape']) == [1, 38]
rng = np.random.default_rng(7)
diffs, match = [], 0
for _ in range(8):
    x01 = rng.random((1, 224, 224, 3), dtype=np.float32)
    interp.set_tensor(iid['index'], x01); interp.invoke()
    tfl = interp.get_tensor(iod['index'])[0]
    ref = sess_f.run(None, {"pixel_values": np.transpose(x01, (0, 3, 1, 2)).astype(np.float32)})[0]
    diffs.append(float(np.max(np.abs(tfl - ref))))
    match += int(int(np.argmax(tfl)) == int(np.argmax(ref)))
mean_d = float(np.mean(diffs))
x = np.random.default_rng(0).random((1, 224, 224, 3), dtype=np.float32)
interp.set_tensor(iid['index'], x)
for _ in range(20): interp.invoke()
t0 = time.perf_counter()
for _ in range(50): interp.invoke()
lat = (time.perf_counter() - t0) / 50 * 1000
print(f"  argmax match {match}/8, mean max|diff|={mean_d:.2e}, latency={lat:.1f}ms (2 threads)")

# 6. Metadata
app_labels = (ROOT / "app" / "src" / "main" / "assets" / "models" / "labels.txt").read_text().splitlines()
meta = {
    "source_model": {"repo": HF_REPO, "architecture": "SwinForImageClassification (Swin-Tiny)",
                     "hf_eval_accuracy": 0.9859128993646994, "num_classes": 38, "image_size": 224,
                     "total_params": n_params, "license": "apache-2.0"},
    "pipeline": ["PyTorch (.bin) dynamo=False -> ONNX (opset 17)",
                 "ONNX surgery: Sub(mean)+Div(std) ImageNet norm + Softmax",
                 "onnx2tf flatbuffer-direct -> TFLite (NHWC)"],
    "io_contract": {"input": "[1,224,224,3] float32 [0,1]", "output": "[1,38] float32 softmax",
                    "preprocessing_baked_into_graph": "ImageNet normalization + Softmax (ONNX nodes)"},
    "shipped_variant": {"name": fp32_dst.name, "size_mb": round(fp32_dst.stat().st_size/1e6, 3),
                        "latency_ms_cpu_2threads": round(lat, 2),
                        "mean_max_diff_vs_onnx": mean_d, "argmax_match_vs_onnx": f"{match}/8",
                        "accuracy_hf_reported": 0.9859},
    "dynamo_false_note": "Required: torch 2.9+ dynamo exporter lowers Swin window-attention to an ONNX "
                         "Loop op onnx2tf cannot lower. dynamo=False uses the legacy TorchScript exporter.",
    "labels": app_labels,
}
(META_DIR / "conversion_metadata.json").write_text(json.dumps(meta, indent=2))
print(f"\nDone. Shipped: {fp32_dst} ({fp32_dst.stat().st_size/1e6:.2f} MB, {match}/8 argmax, {lat:.0f}ms)")
print("Copy to app/src/main/assets/models/plant_disease.tflite (or let CI pull from Kaggle).")
