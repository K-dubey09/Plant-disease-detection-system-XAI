# Cross-cutting learnings — what the seven-iteration arc teaches

This file collects the lessons that are **not specific to any one notebook** — the meta-learnings
that emerged across the whole 11% → 86.7% journey. They are the reason the arc reads as a coherent
engineering narrative rather than seven disconnected runs. The per-notebook files give the detail;
this file gives the through-line.

## 1. The unifying principle: strengthen domain invariance, *or* protect what the backbone knows

Every strategy in the final (v3.5) recipe falls into exactly one of two buckets:

- **Strengthen domain invariance** — make the feature space less lab-specific so it transfers to
  field. The self-supervised DINOv2 backbone, the DANN gradient-reversal head, the multi-crop
  consistency loss, and the three-fold field oversampling all do this.
- **Protect what the backbone already knows** — don't let fine-tuning destroy the pre-training.
  Progressive unfreezing and effective-number class weighting do this.

Lab accuracy was already saturated at 99.3% by v3. **None of the v3.5 machinery targets lab
accuracy; all of it targets the field domain.** That is why the field number moved by 19.4
percentage points while lab did not move at all. The lesson: once a domain is saturated, stop
optimizing it and route every additional lever at the unsaturated domain. This sounds obvious; the
v2.x arc exists because it was not obvious early, and lab accuracy (98.6% mixed in v1) kept
masquerading as success while field accuracy was 11%.

## 2. The diagnose-fix cycle: each version is a response to a *named* failure

The arc is not "try things until accuracy goes up." Each notebook's goal is a **specific, named,
diagnosed failure of its predecessor**, and the next notebook's first job is to verify the fix
worked before adding anything new:

| Diagnosed failure (version) | Named cause | Fix tried (next version) | Outcome |
| --- | --- | --- | --- |
| v2.0: field 11% | lab drowning field (cap bug) | enforce separate lab/field caps (v2.1) | underfit → 49% |
| v2.1: field/mixed 49% | underfit (5 epochs too few; cap too aggressive) | three-stage 15-epoch schedule + effective-number (v2.2) | PV ceiling → 34% |
| v2.2: field 34% (stuck while lab climbed) | backbone over-specialized to PlantVillage | abandon PV backbone → ImageNet Swin (v3) | **67.3%** |
| v3: field 67.3% | supervised features over-specialize; residual same-species disease confusion | self-supervised DINOv2 + DANN (v3.5) | **86.7%** |

The discipline here is: **name the failure before you change the recipe.** "Accuracy is low" is not
a diagnosis; "lab drowning field because the lab cap was never enforced" is. The v2.0 → v2.1 step
is the cleanest example — a one-line bug (the cap wasn't applied) was named, and the next version
existed almost entirely to fix it and measure the result in isolation (a pure-field validation set
was added precisely so the fix could be measured without mixed accuracy hiding it).

## 3. A backbone ceiling is real, and no amount of regularization breaks it

v2.2 is the most instructive failure in the arc. It did **everything right** at the recipe level —
three-stage schedule, effective-number weighting, corrected label mapping, field-accuracy model
selection, stronger augmentation — and field accuracy **still stuck at 34% across all 15 epochs**
even as lab climbed from 43% to ~80%. The failure pattern was cross-species confusion of healthy
leaves (grape→apple, peach→blueberry): the model could not identify plant species on field images.

The diagnosis: a PlantVillage-overfit backbone encodes a supervised categorization prior that does
not transfer to cluttered field leaves. **No amount of capping, scheduling, or augmentation rescues
a backbone that over-specialized to lab leaves.** The backbone itself had to change (v3: ImageNet
Swin; v3.5: DINOv2). The 34% was not a wasted iteration — it was the proof that made the backbone
swap undeniable.

The general lesson: when a metric is stuck across an entire training run while a different metric
moves, suspect the **representation**, not the optimizer. Stop tuning the loss and change the
backbone.

## 4. Lab accuracy is a vanity metric until field accuracy moves

v1 hit 98.6% mixed accuracy on a lab-only pool — and had **zero field coverage**. v2.0 hit 84.7%
mixed — and 11% field. In both cases a high mixed number masked field collapse, because the mixed
set was lab-dominated. The fix (v2.1) was to build a **pure-field validation set reported
independently of the mixed set**, and to switch model selection to field validation accuracy.

The lesson: when two domains are imbalanced, the aggregate metric tracks the majority domain.
**Always report the minority domain (here, field) separately, and select models on the metric you
actually care about.** A 99% lab / 11% field model is a lab model, not a plant-disease detector.

## 5. Data provenance verification before pooling

v1's most embarrassing mistake: it pooled three PlantVillage sources (518,000 gathered images)
believing them to be independent, but **all three were derivatives of the same ≈172,000 lab leaves**
— color, grayscale, and segmented renders. The pool gave no new information, and the cross-source
full-file SHA-256 dedup ran ~9 hours and timed out on the Kaggle T4.

Two lessons:
- **Verify provenance before pooling.** Three sources that look different can be renderings of one
  source. Inspect the raw counts and a few samples per source before trusting "more data."
- **Match the dedup algorithm to the scale.** Full-file SHA-256 over 500k images is too slow on a
  T4; the within-source MD5-of-prefix strategy (v2.0 onward) removes the vast majority of exact and
  near-exact duplicates in a fraction of the time. After dedup, 90,973 images were retained
  (92,031 duplicates removed), and every retained image was verified with `PIL.Image.verify()`
  before entering the pipeline (zero corrupt images found).

## 6. Aggressive class caps destroy the species-discrimination prior

v2.1 capped lab to 100 images per class to fix the lab-drowning-field bug. The cap worked in the
sense that field stopped being drowned — but it **destroyed the species-discrimination prior**:
apple-rust began confusing with corn-rust, and the model underfit (49% on both mixed and field,
loss still falling at the last epoch). v2.2 raised lab back to 400 per class (with a separate
`CORN_CAP=800` to handle corn's 22,000-images-over-4-classes imbalance) and the prior recovered.

The lesson: **rebalancing has a floor.** Caps that are too aggressive throw away the signal that
lets the model tell species apart. Prefer loss-side rebalancing (effective-number weighting) over
hard data deletion, and when you do cap, cap to retain the discrimination prior (400/class), not to
"balance" the raw count (100/class).

## 7. The conversion is part of the model — guard it

Two conversion lessons recurred across the arc:

- **`dynamo=False` is load-bearing.** v2.0's ONNX export used the torch 2.9+ default dynamo
  exporter, which required the `onnxscript` package and failed at export time. v2.1 added a
  `dynamo=False` retry loop using the legacy TorchScript exporter. In v3.5 this became critical:
  the dynamo exporter lowers DINOv2's window-attention partitioning to an ONNX `Loop` op that
  `onnx2tf`'s flatbuffer-direct path cannot lower; the legacy exporter traces the static window
  partitions into a flat, Loop-free graph. Removing the flag breaks the build.
- **Bake normalization + softmax into the graph.** Inserting ImageNet mean/std `Sub`/`Div` nodes
  and a final `Softmax` node as ONNX nodes *before* conversion means the deployed graph accepts raw
  `[0,1]` pixels and emits real probabilities. This eliminates an entire class of preprocessing bugs
  (wrong normalization constants, missing softmax) and makes the model a **drop-in**: the
  Swin→DINOv2 swap changed the app code by one line plus the SHA-256 digest, because the I/O
  contract (`[1,224,224,3]` → `[1,38]`) was identical.
- **Verify numerical equivalence, not bit-equality.** PyTorch, ONNX Runtime, and TFLite float paths
  differ in operator fusion and accumulation order, so bit-equality is neither expected nor claimed.
  The shipped DINOv2 TFLite was verified against ONNX Runtime on three seeded inputs: argmax
  agreement 3/3, max|Δ| ≈ 1e-6 (well below the 1e-3 threshold at which softmax ranking could flip).
  Without this check, a subtle conversion error could ship a model whose argmax disagrees with the
  validated one.

## 8. Quantization is not a one-flag fix for transformers

The FP32 DINOv2 model is 86.8 MB and targets ≥6 GB RAM devices. The obvious next step — FP16
(~43 MB) and INT8 dynamic-range — was attempted via `onnx2tf`'s flatbuffer-direct quantization and
**failed**: the transformer activations are not calibrated correctly and the INT8 path produced NaN
outputs; the FP16 path hit an XNNPACK conv-dtype mismatch. The lesson (documented in
`future-proposals/01_model.md`): quantizing a transformer is not a flag. The viable path is to
rebuild the model in native Keras, transfer the DINOv2 weights, and quantize via
`tf.lite.TFLiteConverter` with `Optimize.DEFAULT` and a representative dataset for INT8 — keeping
FP32 as the high-accuracy fallback. Measure the accuracy/size trade-off, do not assume it.

## 9. Keep the failed iterations public

The v2.x notebooks (11% → 49% → 34%) are kept public deliberately. The reason is not nostalgia — it
is that **the final 86.7% is only credible as the endpoint of a documented diagnose-fix cycle.** A
repo that shows only the winning model invites suspicion ("did you just get lucky?"). A repo that
shows the 11%, the named cause, the fix, the 49%, the next named cause, the fix, the 34%, the
backbone-swap diagnosis, the 67.3%, and finally the 86.7% shows *how* the result was obtained, which
is the only thing another practitioner can reproduce or learn from. The failed iterations are part
of the artifact.

## 10. The single highest-value remaining change is not an accuracy lever

From `future-proposals/01_model.md` and `06_evaluation_validation.md`: the most valuable next
changes are **out-of-distribution rejection** (today a 39th-species leaf gets a confident wrong
label from the 38 — a trust failure) and an **independent expert accuracy audit** (the 86.7% is
measured against author-assigned ground truth, not expert-validated labels). Neither is a training
trick; both are about *trust*. The arc taught that pushing accuracy is necessary but not sufficient
— a model that is confidently wrong on an unsupported leaf is more dangerous than one that admits
it doesn't know. The next iteration's first job is therefore not "87.7% field" but "refuse to
answer when it shouldn't."
