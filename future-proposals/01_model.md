# 01 — Model Improvements

## Current state

The shipped classifier is a **DINOv2 ViT-S/14** vision transformer fine-tuned for the 38
PlantVillage classes, exported to TFLite FP32 (86,845,656 bytes, SHA-256 `3660cfdb…`). It reaches
**86.7% field / 99.3% lab / 96.1% mixed** top-1 with 3-view TTA, trained with a DANN
gradient-reversal head, progressive layer unfreeze, CutMix/MixUp, multi-crop consistency, and 3×
field oversampling. Inference runs on a singleton TFLite `Interpreter`; the explanation path is
occlusion sensitivity because TFLite exposes no conv activations or gradients. The seven-iteration
training arc (11% → 49% → 34% → 67.3% → 86.7%) is public on Kaggle.

## Proposal

**A. Quantized model variants for size-constrained devices.** The current FP32 model is 86.8 MB
and targets ≥ 6 GB RAM. An FP16 (~43 MB) and an INT8 dynamic-range variant would unlock 3–4 GB RAM
devices. The earlier attempt via `onnx2tf`'s flatbuffer-direct quantization produced NaN outputs
(the transformer activations are not calibrated correctly) and the FP16 path hit an XNNPACK
conv-dtype mismatch, so this is **not** a one-flag fix: rebuild the model in native Keras, transfer
the DINOv2 weights, and quantize via `tf.lite.TFLiteConverter` with `Optimize.DEFAULT` and a
representative dataset for INT8. Keep FP32 as the high-accuracy fallback; let the runtime choose
by device capability (see `02_architecture.md` §F).

**B. DINOv2 v3.6 — push field accuracy past 86.7%.** Strengthen the domain-adaptation recipe rather
than the backbone: a stronger DANN schedule (λ ramp tuned per epoch, not linear), 5-view TTA (add
+90° rotation and a 1.125 zoom to the current orig/hflip/0.875), class-conditional augmentation
that stresses the under-performing classes, and harder CutMix on the field partition. These are
cheap experiments that reuse the existing notebook scaffold.

**C. Larger backbone with distillation.** Train a DINOv2 ViT-B/14 teacher, distill into the ViT-S
student for on-device deployment. The teacher is run offline only; the student ships. This raises
the accuracy ceiling without raising the on-device footprint, at the cost of one extra training
run and a distillation loss implementation.

**D. Open-set / out-of-distribution rejection.** Today, a leaf of a 39th (unsupported) species is
returned with a high-confidence label from the 38 — a trust failure. Add an "unknown" rejection
path: an energy-based score or a MaxSoftmax threshold tuned on a held-out set of out-of-distribution
leaves, surfacing a "not recognized — please photograph a supported leaf" result instead of a
confident wrong answer. This is the single highest-value model change for trust.

**E. Multi-task head: disease + severity.** Severity is currently derived by a rule lookup
(`Severity` enum keyed off the label). A learned severity head trained on a small
severity-annotated set would make the severity tier data-driven and continuous rather than
class-fixed — but it depends on `04_dataset.md` §A (expert labels) existing first.

**F. Lightweight ensemble.** Pair DINOv2 with a tiny ConvNeXt-Tiny (or MobileViT) trained on the
same data; average softmaxes at inference. Cheap robustness on common classes, at roughly +15–20
MB and one extra forward pass. Worth it only if the quantized DINOv2 variant is not enough.

## Feasibility & effort

| Sub-proposal | New data needed? | GPU cost | Effort | Blocker |
| --- | :---: | :---: | :---: | --- |
| A. Quantized variants | representative set for INT8 | low | High | Keras rebuild + weight transfer |
| B. v3.6 recipe | no (existing data) | 1–2 runs | Low–Med | none |
| C. ViT-B distillation | no | 2 extra runs | Med | distillation loss plumbing |
| D. OOD rejection | OOD leaf set | low | Med | needs an OOD held-out set |
| E. Severity head | severity labels | low | Med | blocked by expert labels |
| F. Ensemble | no | 1 run | Med | size/latency trade-off |

## Risks & open questions

Quantization (A) is the riskiest: transformer activation ranges are wide, and a naive INT8 model
could lose several points of field accuracy — the accuracy/size trade-off must be measured, not
assumed, before shipping to low-RAM devices. The OOD rejection (D) needs a principled threshold
tuned to a low false-reject rate on supported classes, or it will annoy users by rejecting valid
leaves. Distillation (C) and the ensemble (F) both add training-run surface area and a new
artifact to integrity-guard. Before any of B/C is attempted, the field-label accuracy gap (`06`)
should be closed first — otherwise we are optimizing a metric whose ground truth is unverified.
