# Notebook 5 — `leafguard-swin-finetune-v2-2` (three-stage schedule, PV ceiling)

| | |
| --- | --- |
| **Kaggle slug** | `leafguard-swin-finetune-v2-2` |
| **URL** | https://www.kaggle.com/code/kushagradubey09/leafguard-swin-finetune-v2-2 |
| **Role** | first three-stage schedule; the PlantVillage→PlantDoc ceiling |
| **Backbone** | Swin-T (PlantVillage) |
| **Field acc.** | **34.0%** |
| **Mixed acc.** | 66% |

## Goal — why this notebook existed

To fix v2.1's underfit with a proper training schedule and proper class balancing — and to address
the species-discrimination collapse that v2.1's aggressive cap caused. This notebook does
**everything right at the recipe level** and still hits a wall, which is what makes it the most
instructive failure in the arc.

## Strategy & methodology

- **Backbone & data:** Swin-T on PV + field (as in v2.0/v2.1).
- **Class balancing:** lab raised back to **400 per class** (recovering the species-discrimination
  prior that v2.1's 100-cap destroyed); a separate **`CORN_CAP = 800`** added to prevent corn
  dominance (corn has 22,000 images over only 4 classes); **effective-number class weighting**
  (`β = 0.999`) replacing naive inverse-frequency weighting.
- **Label cleanup:** corrected a `grape_leaf` folder that had been fuzzy-mis-mapped to a Grape
  disease class (it is healthy).
- **Model selection:** switched to **field validation accuracy** (not mixed).
- **Augmentation:** strengthened — rotation 20°, stronger color jitter, affine shear 15°,
  perspective 0.2.
- **Three-stage, 15-epoch schedule:**
  1. head-only — 3 epochs @ `5 × 10⁻⁴`;
  2. partial unfreeze — 8 epochs @ `1 × 10⁻⁵` (layers 2–3 + pooler + head trainable);
  3. targeted boost — 4 epochs @ `3 × 10⁻⁶` (worst-recall classes oversampled 3×).

## Why this strategy

Each choice is a direct response to a named v2.1 failure:

- Three-stage 15-epoch schedule → fix underfit (loss was still falling at v2.1's 5 epochs).
- Lab back to 400/class → recover the species-discrimination prior v2.1's 100-cap destroyed.
- `CORN_CAP = 800` → handle corn's specific imbalance without re-destroying the species prior.
- Effective-number weighting → loss-side rebalancing that doesn't over-weight the rarest classes the
  way inverse-frequency did in v2.x.
- Label cleanup → the grape_leaf mis-mapping was a literal wrong label inflating a disease class
  with healthy samples.
- Field-accuracy model selection → pick the checkpoint that's best at the thing we care about.

## Code & config highlights

- First three-stage progressive-unfreeze schedule (the ancestor of v3.5's load-bearing 3-step
  unfreeze).
- First use of **effective-number class weighting** (`wc = (1 − β) / (1 − β^nc)`, `β = 0.999`) —
  carried into v3.5.
- First per-class hard cap targeted at a single dominant class (`CORN_CAP = 800`).
- First **field-accuracy model selection** — checkpoints chosen by field validation, not mixed.
- Strengthened augmentation (rotation 20, shear 15, perspective 0.2).

## Results & logged outcomes

- **Field ≈ 34%, stuck across all 15 epochs**, even as **lab climbed from 43% to ≈80%**. Mixed 66%.
- The divergence — field flat while lab rises — is the signature of a representation problem, not an
  optimization problem.

## Mistakes & diagnosed failures

**The PlantVillage→PlantDoc ceiling.** Field accuracy stuck at ~34% while lab climbed. The failure
pattern was **cross-species confusion of healthy leaves**: grape→apple, peach→blueberry. The model
could not identify plant species on field images — it was confusing entire species, not just
diseases within a species.

The diagnosis: a **PlantVillage-overfit backbone encodes a supervised categorization prior that does
not transfer to cluttered field leaves.** The backbone had learned to recognize PlantVillage's
uniform-background lab leaves; on real field photographs (cluttered backgrounds, varied lighting,
whole-plant context) that prior is a liability. **No amount of capping, scheduling, or augmentation
rescues a backbone that over-specialized to lab leaves.**

This is the most important diagnosis in the arc: it is the moment the project proved to itself that
the **backbone itself had to change**, which made v3 (ImageNet Swin) and v3.5 (DINOv2) possible.

## Learnings (carried forward)

1. **When a metric is stuck across an entire run while a different metric moves, suspect the
   representation, not the optimizer.** Stop tuning the loss and change the backbone. v2.2 did
   everything right at the recipe level and still hit 34% — the recipe was not the problem.
2. **Effective-number weighting > inverse-frequency.** It rebalances without handing the rarest
   classes control of the gradient. This carried straight into v3.5.
3. **Field-accuracy model selection is correct.** Selecting on mixed accuracy would have picked a
   checkpoint good at lab and bad at field.
4. **A backbone ceiling is real.** The 34% was not a wasted iteration — it was the proof that made
   the v3 backbone swap undeniable. Without v2.2's named ceiling, v3's ImageNet pivot would have
   looked like an arbitrary change of mind.

## Research cross-reference

- **Class-Balanced Loss / Effective Number of Samples** (Cui et al., CVPR 2019) — the weighting
  scheme introduced here and kept through v3.5.
  https://openaccess.thecvf.com/content_CVPR_2019/html/Cui_Class-Balanced_Loss_Based_on_Effective_Number_of_Samples_CVPR_2019_paper.html
- **PlantVillage→PlantDoc domain gap** — the ceiling this notebook diagnosed; the gap the DINOv2 +
  DANN recipe (v3.5) ultimately closes. PlantDoc: https://arxiv.org/abs/1911.10317
- See [`00_research_cross_reference.md`](00_research_cross_reference.md).
