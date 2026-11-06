# Notebook 7 — `leafguard-swin-finetune-v3-5-0-0` (DINOv2 + DANN, final)

| | |
| --- | --- |
| **Kaggle slug** | `leafguard-swin-finetune-v3-5-0-0` |
| **URL** | https://www.kaggle.com/code/kushagradubey09/leafguard-swin-finetune-v3-5-0-0 |
| **Role** | the final, shipped model — DINOv2 backbone + domain-adversarial head |
| **Backbone** | DINOv2 ViT-S/14 (`facebook/dinov2-small`, ≈22.1 M params, 384-dim CLS-token features) |
| **Field acc.** | **86.7%** (525-image field set, 3-view TTA) |
| **Lab acc.** | **99.3%** (1,520) |
| **Mixed acc.** | **96.1%** (2,045) |
| **Shipped as** | `plant_disease.tflite`, 86,845,656 bytes, SHA-256 `3660cfdb…` |

## Goal — why this notebook existed

v3 reached 67.3% field with an ImageNet-pretrained Swin, but the residual errors were *same-species
disease confusion*, and the diagnosis was "supervised features over-specialize; they are committed
to a taxonomy that doesn't fit cluttered field leaves." This notebook's job was to **break that
ceiling** by (a) swapping to a **self-supervised** backbone (DINOv2) whose features are not
committed to any supervised categorization, and (b) adding **explicit unsupervised domain
adaptation** (a gradient-reversal domain-adversarial head) so the backbone is pushed to produce
features indistinguishable across lab and field. The +19.4 pp jump to 86.7% is the convergence of
every lesson learned in notebooks 1–6.

## Strategy & methodology

### Backbone swap

The ImageNet-Swin is replaced entirely by **DINOv2 ViT-S/14** (`facebook/dinov2-small`, ≈21 M
parameters, 384-dimensional CLS-token features), loaded via a custom `LeafGuardDino` module with a
38-way linear disease head and a 2-class domain head (Linear → ReLU → Linear). DINOv2 is **smaller
than the Swin-Tiny it replaces** (22.1 M vs 28 M) — an additional on-device advantage.

### Dual-head architecture

Two heads share the 384-dim feature `z = fθ(x)`:

- **Disease head** `hc(z) = softmax(Wc·z + bc) ∈ ℝ³⁸`, trained with cross-entropy `Lcls` against the
  38-class labels.
- **Domain head** `hd` (lab vs field, 2-class: linear→64→ReLU→linear→2), connected to the backbone
  through a **gradient-reversal layer (GRL)**.

### The total objective

```
L = Lcls + λd · Ldom + λc · Lcons
```

- `Ldom` — binary cross-entropy of `hd(R(z))` against the domain label `d ∈ {lab, field}`.
- `Lcons` — KL divergence `DKL(hc(z_cons) ∥ hc(z_strong))` between the weak consistency crop and the
  strong-aug crop, **applied only to field images** (the lab distribution is already well-covered).
- `λc = 0.2` (consistency weight, active in the partial-unfreeze phase).
- `λd` schedule `0.1 → 0.3 → 0.1` across the three progressive-unfreeze steps — strongest while the
  last blocks are learning, eased during the final low-LR polish.

### Three-step progressive unfreeze

| Step | Epochs | Base LR | What's trainable | GRL α | Other |
| --- | ---: | ---: | --- | ---: | --- |
| 1. Linear probe | 3 | `1 × 10⁻³` | backbone frozen, heads only | 0.1 | — |
| 2. Partial unfreeze | 8 | `1 × 10⁻⁴` | last four ViT blocks (8–11 of 12) + heads | 0.3 | CutMix/MixUp 0.5, consistency 0.2 (field only), 3× field oversampling, discriminative LR decay 0.85 |
| 3. Full unfreeze | 4 | `5 × 10⁻⁶` | all 12 blocks | 0.1 | decay 0.9, consistency dropped |

Discriminative layer-wise LR decay: `ηℓ = ηbase · γ^(L−1−ℓ)`, `γ ∈ {0.85, 0.9}` — shallower, more
general blocks learn slowest. Batch size 32, input 224×224.

### Augmentation (multi-crop)

Each training image yields two crops: a **strong-aug crop** (RandomResizedCrop to 224, scale
0.7–1.0, aspect 0.85–1.15, horizontal flip, rotation 12°, color jitter 0.25/0.25/0.15/0.04, Gaussian
blur p=0.25, random erasing p=0.3) and a weaker **consistency crop** (scale 0.8–1.0, flip, lighter
jitter). CutMix + MixUp with combined probability 0.5, `λ ~ Beta(0.4, 0.4)`, `λ ← max(λ, 1 − λ)`.

### Class balancing

**Effective-number class weighting** `wc = (1 − β) / (1 − β^nc)`, `β = 0.999` (carried from v2.2) —
replaces naive inverse-frequency, which over-weighted the rarest classes in v2.x.

### Test-time augmentation

3 views — original, horizontal flip, central crop of 0.875 (≈ resize to 256, crop central 224).
Softmaxes averaged before ranking:

```
p̄(x) = (1/3) · Σ p(tᵢ(x)),  t ∈ {orig, hflip, scale0.875}
```

This is the **exact recipe used at on-device inference**, so the validated field number tracks the
on-device number rather than a single-crop approximation.

### Conversion (PyTorch → ONNX → TFLite)

- ONNX export **opset 17, `dynamo = False`** (legacy TorchScript exporter). Mandatory: the torch 2.9+
  dynamo exporter lowers DINOv2 window-attention partitioning to an ONNX `Loop` op that `onnx2tf`'s
  flatbuffer-direct path cannot lower; the legacy exporter traces the static window partitions into a
  flat, Loop-free graph.
- `onnx2tf` converts NCHW → NHWC (batch fixed to 1). `TFLiteConverter` produces an FP32 graph with
  `SELECT_TF_OPS` enabled to cover the ViT operations.
- **Graph surgery:** ImageNet mean/std normalization (ONNX `Sub`/`Div` nodes) and the final `Softmax`
  node are inserted *before* conversion, so the deployed graph accepts raw `[0,1]` pixels and emits
  real probabilities. This makes the model a drop-in (the Swin→DINOv2 swap changed the app code by
  one line plus the SHA-256 digest).
- **Domain head dropped at export** — the deployed graph is purely the 38-class classifier, keeping
  the on-device model small and the inference path simple.

## Why each component — the rationale (each attacks a named failure)

| Component | Attacks (named failure) | How |
| --- | --- | --- |
| **Self-supervised DINOv2 backbone** | PV/ImageNet over-specialization (v2.x 34%, v3 67.3%) | 142M-image self-supervised objective produces features whose discriminative structure is learned **without labels**, so they are not committed to a supervised taxonomy. **Largest ablation component (+11.9 pp).** |
| **Gradient-reversal domain-adversarial head (DANN)** | the uniform-grey-background cue that fooled v2.0 | GRL is identity forward, `∂R/∂z = −λd·I` backward, pushing `z` toward a region the domain classifier cannot label as lab-or-field. **Second-largest component (+7.1 pp).** |
| **Progressive unfreezing (3-step)** | catastrophic forgetting of self-supervised pre-training | Staged release (heads → last 4 blocks → all blocks) with discriminative layer-wise LR decay lets each tier adapt before the next is perturbed. **Removal costs 14.3 pp — the largest single removal; load-bearing.** |
| **Effective-number class weighting** | rare-class destabilization (v2.x inverse-frequency) | `wc = (1−β)/(1−β^nc)` saturates for large classes, a softer rebalancing that doesn't hand rarest classes control of the gradient. |
| **Multi-crop consistency regularization** | field-boundary sparsity / over-confidence | `Lcons = DKL(p_weak ∥ p_strong)`, field-only, pushes the field decision boundary to be invariant to the augmentation crop. |
| **3× field oversampling + CutMix/MixUp** | lab-dominated stream + over-confidence | oversampling rebalances via `WeightedRandomSampler`; CutMix/MixUp force smooth multi-label decision regions under `Beta(0.4, 0.4)`. |
| **3-view TTA** | single-crop approximation error | averages softmaxes of orig/hflip/scale0.875, matching the on-device inference path. |

## Results & logged outcomes

- **Field 86.7%** (525), **lab 99.3%** (1,520), **mixed 96.1%** (2,045) — all with 3-view TTA.
- +19.4 pp over the v3 baseline (67.3% → 86.7%); lab stayed at 99.3% (already saturated).
- **Numerical equivalence** (shipped TFLite vs ONNX Runtime, 3 seeded inputs): argmax agreement 3/3,
  max|Δ| ≈ 1 × 10⁻⁶ — well below the 10⁻³ threshold at which softmax ranking could flip. This is
  numerical equivalence, not bit-equality (PyTorch/ONNX Runtime/TFLite float paths differ in operator
  fusion and accumulation order).

## Component ablation (v3 → v3.5, on the 525-image field set)

| Component | Field-accuracy contribution |
| --- | ---: |
| DINOv2 self-supervised backbone | **+11.9 pp** (largest single component) |
| Progressive unfreezing (remove it) | **−14.3 pp** (largest single removal) |
| Gradient-reversal domain-adversarial head | **+7.1 pp** (second-largest component) |
| 3× field oversampling | **+5.4 pp** |
| Multi-crop consistency + augmentation | **+2.7–3.8 pp** |
| 3-view test-time augmentation | **+2.7 pp** |

## Mistakes & diagnosed failures

The v3.5 *training* succeeded; the failures here are in the **deployment/quantization** layer, not
the training:

- **Quantization is not a one-flag fix.** The FP32 model is 86.8 MB and targets ≥6 GB RAM devices. The
  obvious FP16 (~43 MB) / INT8 dynamic-range variants were attempted via `onnx2tf`'s
  flatbuffer-direct quantization and **failed**: INT8 produced NaN outputs (transformer activations
  not calibrated correctly); FP16 hit an XNNPACK conv-dtype mismatch. The viable path (per
  `future-proposals/01_model.md`) is to rebuild the model in native Keras, transfer the DINOv2
  weights, and quantize via `tf.lite.TFLiteConverter` with `Optimize.DEFAULT` + a representative
  dataset for INT8 — keeping FP32 as the fallback.
- **The model is still confidently wrong on out-of-distribution inputs.** A leaf of a 39th
  (unsupported) species is returned with a high-confidence label from the 38 — a trust failure. This
  is the single highest-value remaining change (open-set / OOD rejection; see
  `future-proposals/01_model.md` §D).

## Learnings (the convergent lessons of the whole arc)

1. **Self-supervised > supervised for field transfer.** DINOv2's label-free pre-training is the
   single largest contributor (+11.9 pp). The arc's central lesson, proven here: supervised features
   cap at ~67%; self-supervised features break the ceiling.
2. **Adversarial domain adaptation works when the shortcut cue is identifiable.** The GRL attacks the
   grey-background cue by name — it doesn't just "do domain adaptation," it removes a specific
   failure mode. +7.1 pp.
3. **Progressive unfreezing is load-bearing for self-supervised backbones.** Updating all 22.1 M
   parameters at one LR destroys the self-supervised pre-training within a few hundred steps. The
   staged release is the largest guardrail (−14.3 pp if removed). This generalizes: whenever you
   fine-tune a self-supervised backbone, unfreeze in tiers.
4. **Every lever targets the unsaturated domain.** Lab was already 99.3% by v3, so none of the v3.5
   machinery targets lab — all of it targets field. That is why field moved 19.4 pp and lab moved 0.
5. **The conversion is part of the model.** `dynamo=False`, normalization/softmax baking, domain-head
   removal, and numerical-equivalence verification are not afterthoughts — they are what make the
   trained weights actually deployable and trustworthy on-device.
6. **The I/O-contract-preserving swap is a force multiplier.** Because the 38-way head + baked
   normalization + `[1,224,224,3]→[1,38]` contract was preserved from v3, the DINOv2 swap was a
   one-line app change plus a SHA-256 update. Designing for drop-in replacement from the start
   (notebook 6) paid off here.

## Research cross-reference

- **DINOv2** (Oquab et al., 2023) — the self-supervised backbone. https://arxiv.org/abs/2304.07193
- **Domain-Adversarial Training of Neural Networks / GRL** (Ganin et al., JMLR 2016) — the
  gradient-reversal domain head. https://arxiv.org/abs/1505.07818
- **Class-Balanced Loss / Effective Number of Samples** (Cui et al., CVPR 2019) — the class
  weighting. https://openaccess.thecvf.com/content_CVPR_2019/html/Cui_Class-Balanced_Loss_Based_on_Effective_Number_of_Samples_CVPR_2019_paper.html
- **CutMix** (Yun et al., ICCV 2019), **MixUp** (Zhang et al., ICLR 2018) — the mixing augmentation.
- **PlantVillage** (Mohanty et al., 2016) and **PlantDoc** (Singh et al., 2019) — the lab and field
  evaluation sets. https://www.frontiersin.org/journals/plant-science/articles/10.3389/fpls.2016.01419/full · https://arxiv.org/abs/1911.10317
- See [`00_research_cross_reference.md`](00_research_cross_reference.md) for the full literature map
  and [`08_cross_cutting_learnings.md`](08_cross_cutting_learnings.md) for the arc-wide lessons this
  notebook converges.
