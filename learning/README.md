# Learning — the seven-notebook training arc

This folder is the **consolidated, per-notebook learning log** for LeafGuard's model development.
It documents, for each of the seven public Kaggle training notebooks, the **strategy, the
methodology, why that strategy was chosen, the code/config highlights, the logged results, the
mistakes and diagnosed failures, and the learnings carried into the next iteration** — plus a
research cross-reference tying each decision to the literature it came from.

The goal is exactly what the project asked of itself: make the iteration auditable. The 86.7% field
result was not obtained in a single run; it is the endpoint of a deliberate seven-iteration arc in
which **each version was a direct response to the documented failure mode of its predecessor**, and
field top-1 accuracy rose **11% → 49% → 34% → 67.3% → 86.7%**.

> **Source of truth.** The per-notebook recipes, hyperparameters, and logged outcomes here are
> consolidated from the project's IEEE-format report (`docs/IEEE_format/LeafGuard_IEEE_Project_Report.pdf`,
> which contains the per-version notebook appendix, the training-strategy rationale, and the
> component ablation) and from `MODEL_CARD.md` / `REPRODUCIBILITY.md` / `future-proposals/`. The
> notebooks themselves remain the canonical source for the exact training code — each lives at
> `https://www.kaggle.com/code/kushagradubey09/<slug>` under the `kushagradubey09` Kaggle account.

## How to read this folder

| File | What it covers |
| --- | --- |
| [`00_research_cross_reference.md`](00_research_cross_reference.md) | The research papers and techniques that informed the design, each with the real URL and how LeafGuard applied it. Read this first if you want the literature map. |
| [`01_v1_mobile_baseline.md`](01_v1_mobile_baseline.md) | Notebook 1 — `leafguard-finetune-v1` — MobileNetV3-S mobile escape-hatch baseline (no field data). |
| [`02_swin_v1_pv_pool.md`](02_swin_v1_pv_pool.md) | Notebook 2 — `leafguard-swin-finetune-v1` — multi-source PlantVillage pool (lab-only, 98.6% mixed, zero field coverage). |
| [`03_swin_v2_first_field.md`](03_swin_v2_first_field.md) | Notebook 3 — `leafguard-swin-finetune-v2` — first field data; lab drowning; **field 11%**. |
| [`04_swin_v2_1_cap_enforcement.md`](04_swin_v2_1_cap_enforcement.md) | Notebook 4 — `leafguard-swin-finetune-v2-1` — cap enforcement; underfit; **field 49%**. |
| [`05_swin_v2_2_three_stage.md`](05_swin_v2_2_three_stage.md) | Notebook 5 — `leafguard-swin-finetune-v2-2` — three-stage schedule; PV→PlantDoc ceiling; **field 34%**. |
| [`06_swin_v3_imagenet_pivot.md`](06_swin_v3_imagenet_pivot.md) | Notebook 6 — `leafguard-swin-finetune-v3` — ImageNet pivot; **field 67.3%**. |
| [`07_v3_5_dinov2_dann.md`](07_v3_5_dinov2_dann.md) | Notebook 7 — `leafguard-swin-finetune-v3-5-0-0` — DINOv2 + DANN; the final **field 86.7%** model. |
| [`08_cross_cutting_learnings.md`](08_cross_cutting_learnings.md) | The meta-learnings that cut across all seven notebooks — the unifying principle, the reusable strategies, the failure patterns, and what the arc teaches about iterative ML engineering. |

Each per-notebook file follows the same template: **Goal → Strategy & methodology → Why this
strategy → Code & config highlights → Results & logged outcomes → Mistakes & diagnosed failures →
Learnings → Research cross-reference.**

## The iteration arc at a glance

| # | Notebook slug | Backbone | Key recipe change | Field | Mixed | Diagnosed failure → next step |
| ---: | --- | --- | --- | ---: | ---: | --- |
| 1 | `leafguard-finetune-v1` | MobileNetV3-S | FP16 TFLite baseline | — | — | prior Swin overfit on PlantVillage |
| 2 | `leafguard-swin-finetune-v1` | Swin-T (PV) | 3 PV sources, SHA dedup | — | 98.6% | zero field coverage; sources were duplicates |
| 3 | `leafguard-swin-finetune-v2` | Swin-T (PV) | add PlantDoc/corn/alex field | 11% | 84.7% | lab cap bug: lab drowned field |
| 4 | `leafguard-swin-finetune-v2-1` | Swin-T (PV) | enforce lab/field caps | 49% | 49% | underfit: 5 epochs too few |
| 5 | `leafguard-swin-finetune-v2-2` | Swin-T (PV) | 3-stage, eff-num weights | 34% | 66% | PV→PlantDoc ceiling hit |
| 6 | `leafguard-swin-finetune-v3` | Swin-T (ImageNet) | CutMix/MixUp, 3× field, TTA | 67.3% | 94.5% | residual same-species disease confusion |
| 7 | `leafguard-swin-finetune-v3-5-0-0` | DINOv2 ViT-S/14 | +DANN GRL, consistency, 3-step | **86.7%** | **96.1%** | — (current shipped model) |

The **v2.x rows are the failed iterations, kept public deliberately** so the seven-iteration arc is
reproducible and the final 86.7% is not a magic number — it is the endpoint of a documented
diagnose-fix cycle. The single biggest jump is v3 → v3.5 (+19.4 percentage points), which is also
where the most engineering learned in the earlier failures converged.

## Final model (v3.5) component ablation

The v3.5 recipe is not a single trick; it is a set of strategies, each chosen to attack a specific
failure observed in an earlier version. The ablation on the 525-image field set quantifies each
component's contribution to the v3 → v3.5 gain:

| Component | Field-accuracy contribution | Attacks |
| --- | ---: | --- |
| DINOv2 self-supervised backbone | **+11.9 pp** (largest single component) | PlantVillage/ImageNet over-specialization |
| Progressive unfreezing (3-step schedule) | **−14.3 pp if removed** (largest single removal) | catastrophic forgetting of self-supervised pre-training |
| Gradient-reversal domain-adversarial head (DANN) | **+7.1 pp** (second-largest component) | the uniform-grey-background cue that fooled v2.0 |
| 3× field oversampling | **+5.4 pp** | lab-dominated training stream |
| Multi-crop consistency + augmentation | **+2.7–3.8 pp** | field-boundary smoothness / over-confidence |
| 3-view test-time augmentation | **+2.7 pp** | single-crop approximation error |

The unifying principle (detailed in [`08_cross_cutting_learnings.md`](08_cross_cutting_learnings.md)):
**every strategy either strengthens domain invariance** (self-supervised backbone, DANN, consistency,
field oversampling) **or protects what the backbone already knows** (progressive unfreeze,
effective-number weighting). Lab accuracy was already saturated at 99.3% by v3, so none of the v3.5
machinery targets lab accuracy — all of it targets the field domain, which is why the field number
moved by 19.4 pp while lab did not move at all.

## Field-accuracy journey

```
field top-1 %
  100 │                                              ●  86.7 (v3.5: DINOv2 + DANN)
   80 │                                   ●  67.3
   60 │                              (v3: ImageNet Swin)
   40 │            ●  49       ●  34
   20 │  ●  11  (v2.1)     (v2.2)
    0 │ (v2.0)
      └──────────────────────────────────────────────────→  iteration
         3      4      5      6      7
```

The dip v2.1 (49%) → v2.2 (34%) is not a regression to be ashamed of — it is the moment the project
proved (to itself) that **no amount of capping, scheduling, or augmentation can rescue a backbone
that over-specialized to lab leaves**, and that the backbone itself had to change. That diagnosis is
what made v3 and v3.5 possible.

## Reproducing any notebook

Each notebook is public at `https://www.kaggle.com/code/kushagradubey09/<slug>` (see the table
above). The notebooks ran on Kaggle's GPU runtime (the accelerator allocated per session is in each
notebook's session metadata). The per-version hyperparameters, schedules, and random seeds are
defined in the notebook source itself — **the notebooks are the source of truth for the training
recipe**; these learning documents consolidate the *why* and the *outcome*, not the raw cell source.
Re-running the final notebook regenerates the PyTorch checkpoint and the ONNX graph;
`models/conversion/convert.py` then produces the shipped TFLite (86,845,656 bytes, SHA-256
`3660cfdb…`). See [`REPRODUCIBILITY.md`](../REPRODUCIBILITY.md) for the full evidence chain.
