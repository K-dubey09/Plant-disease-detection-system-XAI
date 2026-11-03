#!/usr/bin/env python3
"""
Stage 1 of the LeafGuard model conversion pipeline:
download the HuggingFace PyTorch MobileNetV2 plant-disease model and export it to ONNX.

Run from the repository root:
    python models/conversion/convert.py

Produces:
    models/source/pytorch_model.bin   (downloaded HF weights)
    models/source/config.json         (HF model config)
    models/onnx/plant_disease_mobilenetv2.onnx   (opset 18, NCHW [-1,1] input, logits output)

Source: linkanjarad/mobilenet_v2_1.0_224-plant-disease-identification
        MobileNetV2 1.0, 224x224, 38 PlantVillage classes, 78.6% eval accuracy.
"""
from pathlib import Path
import requests

ROOT = Path(__file__).resolve().parents[2]      # repo root
SRC_DIR = ROOT / "models" / "source"
ONNX_DIR = ROOT / "models" / "onnx"
SRC_DIR.mkdir(parents=True, exist_ok=True)
ONNX_DIR.mkdir(parents=True, exist_ok=True)

HF_REPO = "linkanjarad/mobilenet_v2_1.0_224-plant-disease-identification"
HF_BASE = f"https://huggingface.co/{HF_REPO}/resolve/main"

# 1. Download the HF model files
print("=== Downloading HuggingFace PyTorch MobileNetV2 ===")
for fname in ["config.json", "preprocessor_config.json", "pytorch_model.bin",
              "README.md", "eval_results.json"]:
    dst = SRC_DIR / fname
    if dst.exists() and dst.stat().st_size > 0:
        print(f"  {fname}: already present ({dst.stat().st_size} bytes)")
        continue
    r = requests.get(f"{HF_BASE}/{fname}", timeout=60, stream=True)
    r.raise_for_status()
    dst.write_bytes(r.content)
    print(f"  {fname}: {dst.stat().st_size} bytes")

# 2. Load + export to ONNX
print("\n=== Loading PyTorch model + exporting to ONNX (opset 18) ===")
import torch
from transformers import AutoModelForImageClassification, AutoConfig

cfg = AutoConfig.from_pretrained(str(SRC_DIR))
model = AutoModelForImageClassification.from_pretrained(str(SRC_DIR))
model.eval()
print(f"  num_labels = {cfg.num_labels}, params = {sum(p.numel() for p in model.parameters()):,}")

ONNX_PATH = ONNX_DIR / "plant_disease_mobilenetv2.onnx"
dummy = torch.randn(1, 3, 224, 224, requires_grad=False)
with torch.no_grad():
    torch.onnx.export(
        model, dummy, str(ONNX_PATH),
        opset_version=17,                  # torch 2.x exporter may bump to 18; both work
        input_names=["pixel_values"], output_names=["logits"],
        dynamic_axes=None, do_constant_folding=True,
    )
print(f"  ONNX: {ONNX_PATH} ({ONNX_PATH.stat().st_size/1e6:.2f} MB)")

# 3. Verify ONNX matches PyTorch
import numpy as np, onnx, onnxruntime as ort
onnx.checker.check_model(onnx.load(str(ONNX_PATH)))
sess = ort.InferenceSession(str(ONNX_PATH), providers=["CPUExecutionProvider"])
inp = np.random.randn(1, 3, 224, 224).astype(np.float32) * 0.5
ort_out = sess.run(None, {"pixel_values": inp})[0]
with torch.no_grad():
    pt_out = model(torch.from_numpy(inp)).logits.numpy()
diff = float(np.max(np.abs(ort_out - pt_out)))
print(f"  ONNX vs PyTorch max|diff| = {diff:.2e}  (must be < 1e-3)")
assert diff < 1e-3, "ONNX export diverged from PyTorch"
print("\nStage 1 complete. Run models/conversion/finalize.py next.")
