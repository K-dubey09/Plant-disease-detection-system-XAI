# Notebook 1 — `leafguard-finetune-v1` (mobile baseline)

| | |
| --- | --- |
| **Kaggle slug** | `leafguard-finetune-v1` |
| **URL** | https://www.kaggle.com/code/kushagradubey09/leafguard-finetune-v1 |
| **Role** | mobile baseline (escape hatch from a failing Swin model) |
| **Backbone** | MobileNetV3-Small (≈2.5 M parameters, TensorFlow/Keras) |
| **Field acc.** | N/A (no field data) |
| **Shipped as** | the mobile-deployment contract; pre-v2.0.0 app |

## Goal — why this notebook existed

Before this notebook, the project had shipped (as app build v1.4.0.0) a **PlantVillage-overfit Swin
model** that exhibited exactly the failure modes the whole project set out to fix:

- ~36% confidence on diseased tomato (the model was unsure on the very thing it was built for);
- healthy tomato misclassified as strawberry;
- sweet-potato collapsed to tomato — and there is no sweet-potato class in the 38-class scheme, so
  the model was confidently mapping a real crop to a class that does not even exist for it.

This notebook's job was not to fix the field problem (it had no field data to fix it with) but to
establish a **mobile-deployable escape hatch** — a small, fast, on-device model that proved the
deployment pipeline worked while the real model was being rebuilt.

## Strategy & methodology

- **Backbone:** MobileNetV3-Small, built in TensorFlow/Keras (not the PyTorch Swin path) — chosen
  specifically for mobile footprint.
- **Data:** a single PlantVillage source, subsampled to a capped 1,200 images per class.
- **Schedule:** frozen-then-partial-unfreeze (warm up the head on frozen features, then release the
  upper backbone).
- **Conversion:** FP16 TFLite, producing a ≈2.5 MB model — small enough that the on-device size and
  latency budget was proven viable.

## Why this strategy

The priority was **deployment viability, not accuracy.** The prior Swin model was failing in
deployment-relevant ways (confident wrong answers on real crops), and the project needed (a) a model
that fit on a phone and ran fast, and (b) a known-good conversion→on-device path, so that when the
real field-capable model arrived it could drop into a pipeline that already worked. MobileNetV3-Small
is the canonical "prove the mobile contract" backbone; capping to 1,200/class kept the training cheap
on a Kaggle GPU.

## Code & config highlights

- TensorFlow/Keras training loop (the only notebook in the arc not on the PyTorch/Swin path).
- `1,200` images-per-class cap on a single PlantVillage source.
- Frozen-then-partial-unfreeze schedule.
- FP16 TFLite export → ≈2.5 MB on-device artifact.

## Results & logged outcomes

- **No field evaluation** — the notebook had zero field data, so no field accuracy is reported.
- Established the mobile deployment contract: a model of this size class runs on-device within the
  app's latency budget (the later DINOv2 model is 86.8 MB FP32, but the contract that "a model of
  roughly this footprint is deployable on the target ≥6 GB RAM devices" was first proven here).

## Mistakes & diagnosed failures

- **Inherited the prior model's disease, didn't cure it.** Having no field data, this model could
  not fix the field misclassifications it set out to fix. Its base lab accuracy was fine, but lab
  accuracy was never the problem — the problem was field transfer.
- **The prior PV-overfit Swin (v1.4.0.0) was the failure this responded to.** Its specific
  pathologies — confident-wrong on diseased tomato, cross-crop collapse (sweet-potato → tomato) —
  are the evidence that a PlantVillage-only model does not generalize to real leaves.

## Learnings (carried forward)

1. **A PlantVillage-only model does not generalize to field leaves.** This is the founding
   diagnosis of the whole arc. Everything from v2.0 onward is a response to it.
2. **Prove the deployment pipeline early with a small model.** The MobileNetV3 baseline made the
   on-device size/latency budget concrete before the 86.8 MB DINOv2 model existed, so the later
   swap was a model change, not a pipeline change.
3. **Lab accuracy is not the objective.** The baseline had fine lab accuracy and was still the wrong
   model. The metric that matters is field accuracy, which requires field data — the thing this
   notebook lacked and v2.0 introduced.

## Research cross-reference

- **PlantVillage** (Mohanty, Hughes, Salathé, *Frontiers in Plant Science* 2016) — the lab dataset
  this baseline trained on. https://www.frontiersin.org/journals/plant-science/articles/10.3389/fpls.2016.01419/full
- **MobileNetV3** (Howard, Sandler, Chu et al.) — the mobile backbone chosen for footprint; the
  project's only non-transformer backbone.
- See [`00_research_cross_reference.md`](00_research_cross_reference.md) for the full literature map.
