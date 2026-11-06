# Notebook 3 — `leafguard-swin-finetune-v2` (first field data, lab drowning)

| | |
| --- | --- |
| **Kaggle slug** | `leafguard-swin-finetune-v2` |
| **URL** | https://www.kaggle.com/code/kushagradubey09/leafguard-swin-finetune-v2 |
| **Role** | first field data — the lab-drowning-field collapse |
| **Backbone** | Swin-T (PlantVillage) |
| **Field acc.** | **11.0%** |
| **Mixed acc.** | 84.7% |

## Goal — why this notebook existed

This is the notebook that **introduced genuine field data** for the first time — PlantDoc, the corn
field collection, and the author's smaller field-acquired set. Every prior notebook was lab-only;
this one set out to measure and improve field accuracy, which is the metric the project actually
cares about.

## Strategy & methodology

- **Backbone:** Swin-T, PlantVillage-pretrained.
- **Data:** PlantVillage (lab) **+ PlantDoc + corn field collection + field-acquired set** (field) —
  ~157,000 lab + ~25,000 field images.
- **Dedup:** replaced the failed cross-source full-file SHA-256 (v1) with **within-source
  MD5-of-prefix** — fast enough to complete on the T4, removes the vast majority of exact and
  near-exact duplicates.

## Why this strategy

The diagnosis from v1 was "no field data → can't fix field accuracy." The natural next step: add
field data. The MD5-of-prefix dedup was a direct operational fix for v1's nine-hour SHA-256 timeout.
At this point the project still believed the path to field accuracy was "add field data to the
training set."

## Code & config highlights

- First notebook to mix lab (PlantVillage) and field (PlantDoc + corn + author field) sources.
- Within-source MD5-of-prefix dedup (the fast replacement for full-file SHA-256).
- No per-domain cap on the lab source (the bug — see below).

## Results & logged outcomes

- **Field accuracy collapsed to ≈11%** on PlantDoc field photographs (89% correct-or-unknown), even
  as **mixed accuracy reached 84.7%**.
- The mixed number looked respectable; the field number was catastrophic. This is the textbook case
  of an aggregate metric masking minority-domain collapse.

## Mistakes & diagnosed failures

**The lab cap was never enforced — a capping bug.** PlantVillage's 157,000 lab images drowned the
25,000 field images in the training stream. The model learned the lab distribution (uniform grey
backgrounds, controlled lighting) and essentially ignored the field data. On field photographs —
cluttered backgrounds, varied lighting, whole-plant context — it collapsed.

The diagnosed failure was named precisely: **lab drowning field.** The 11% was not "the model is
bad at field"; it was "the model never saw enough field data relative to lab to learn field at all,
because the cap that should have limited lab was silently not applied."

## Learnings (carried forward)

1. **When two domains are imbalanced, the majority domain dominates training.** Adding field data is
   not enough — you must also cap the lab data, or lab will drown field. This is the single most
   important data lesson in the arc.
2. **Report the minority domain separately.** The 84.7% mixed number would have looked like progress
   if field accuracy had not been measured independently. The next notebook (v2.1) added a
   **pure-field validation set reported independently of the mixed set** precisely so this kind of
   masking could never happen again.
3. **Name the bug, then fix only the bug.** The v2.0 → v2.1 step exists almost entirely to enforce
   the cap that v2.0 silently skipped, and to measure the result in isolation. That discipline — fix
   one named thing, measure, then iterate — is what made the rest of the arc interpretable.

## Research cross-reference

- **PlantDoc** (Singh et al., KDD 2019) — the field-stress dataset whose addition produced the 11%
  collapse. https://arxiv.org/abs/1911.10317
- **PlantVillage** (Mohanty et al., 2016) — the lab source that drowned the field data.
  https://www.frontiersin.org/journals/plant-science/articles/10.3389/fpls.2016.01419/full
- **Domain imbalance / lab-drowning-field** — the failure mode the DANN recipe (v3.5) ultimately
  attacks structurally with the gradient-reversal head.
- See [`00_research_cross_reference.md`](00_research_cross_reference.md).
