# Notebook 4 — `leafguard-swin-finetune-v2-1` (cap enforcement, underfit)

| | |
| --- | --- |
| **Kaggle slug** | `leafguard-swin-finetune-v2-1` |
| **URL** | https://www.kaggle.com/code/kushagradubey09/leafguard-swin-finetune-v2-1 |
| **Role** | bug-fix release — enforce lab/field caps; first pure-field validation set |
| **Backbone** | Swin-T (PlantVillage) |
| **Field acc.** | **49.0%** |
| **Mixed acc.** | 49% |

## Goal — why this notebook existed

This notebook exists almost entirely to **fix the named bug from v2.0** — the lab cap that was never
enforced, which caused lab to drown field and field accuracy to collapse to 11%. The recipe,
augmentation, and backbone are as in v2.0; the changes are surgical: enforce the cap, add a
pure-field validation set, and fix the ONNX export path that had broken.

## Strategy & methodology

- **Backbone & data:** as in v2.0 (Swin-T on PV + PlantDoc + corn + field).
- **Cap enforcement:** `LAB_CAP_TRAIN = 100` now actually applied — lab capped to 100 images per
  class, separately from field.
- **Validation:** a **pure-field validation set** built and reported independently of the mixed
  validation set, so field accuracy can no longer be hidden by a lab-dominated mixed number.
- **Export:** added a `dynamo=False` retry loop in the ONNX export. (v2.0's dynamo default required
  the `onnxscript` package and failed at export time; the legacy TorchScript exporter succeeds.)

## Why this strategy

The diagnosis was precise ("the cap wasn't enforced"), so the fix was precise ("enforce the cap,
measure field independently"). Capping to 100/class was chosen as an aggressive rebalancing — the
instinct was "make lab and field comparable in volume." The `dynamo=False` retry was an operational
fix for a real export crash. The pure-field validation set was the measurement fix that made the
whole arc auditable from here on.

## Code & config highlights

- `LAB_CAP_TRAIN = 100` (now applied) — the one-line bug fix that defines this notebook.
- First pure-field validation set, reported alongside (not merged into) the mixed validation set.
- `dynamo=False` ONNX export retry loop — the conversion lesson that became load-bearing in v3.5.

## Results & logged outcomes

- **Field ≈ 49%, mixed ≈ 49%.** The cap stopped lab from drowning field — field jumped from 11% to
  49% — but the model **underfit**: both mixed and field accuracy sat at approximately 49%.
- Loss was **still falling** and validation **still rising** at the final epoch — the classic
  underfit signature: the run ended before the model converged.

## Mistakes & diagnosed failures

1. **The cap was too aggressive — it destroyed the species-discrimination prior.** Capping lab to
   100 images per class threw away so much species signal that apple-rust began confusing with
   corn-rust. The model could no longer reliably tell species apart, which is a prerequisite for
   telling diseases apart.
2. **5 epochs were too few for the 8,000 hard mixed images.** Combined with the aggressive cap
   (less data per class) and the still-falling loss, the model simply didn't train long enough to
   converge.

The diagnosed failure was named: **underfit** — too few epochs, too aggressive a cap, loss still
falling at the last epoch.

## Learnings (carried forward)

1. **Rebalancing has a floor.** Caps that are too aggressive throw away the species-discrimination
   signal. v2.2 raised lab back to **400 per class** (with a separate `CORN_CAP = 800` for corn's
   22,000-images-over-4-classes imbalance) and the prior recovered. Prefer loss-side rebalancing
   (effective-number weighting, added in v2.2) over hard data deletion.
2. **Diagnose underfit from the curves, not the accuracy.** "Loss still falling, validation still
   rising at the last epoch" is the definition of "train longer." The fix in v2.2 was a three-stage,
   15-epoch schedule.
3. **A pure-field validation set is non-negotiable.** Once it existed (here), no future iteration
   could hide field collapse behind a mixed number. This is the measurement infrastructure that made
   the rest of the arc trustworthy.
4. **`dynamo=False` is the export path that works.** Discovered here as a retry loop; in v3.5 it
   becomes mandatory because the dynamo exporter lowers DINOv2 window-attention to an ONNX `Loop`
   op that `onnx2tf` cannot lower.

## Research cross-reference

- **PlantVillage** (Mohanty et al., 2016) — the lab source whose over-aggressive cap destroyed the
  species prior. https://www.frontiersin.org/journals/plant-science/articles/10.3389/fpls.2016.01419/full
- **ONNX export (TorchScript vs dynamo)** — the operational conversion lesson; see
  `REPRODUCIBILITY.md` and the v3.5 conversion notes.
- See [`00_research_cross_reference.md`](00_research_cross_reference.md).
