# Notebook 2 — `leafguard-swin-finetune-v1` (multi-source PV pool, lab-only)

| | |
| --- | --- |
| **Kaggle slug** | `leafguard-swin-finetune-v1` |
| **URL** | https://www.kaggle.com/code/kushagradubey09/leafguard-swin-finetune-v1 |
| **Role** | multi-source PlantVillage pool (lab-only) |
| **Backbone** | Swin-T (PlantVillage-pretrained) |
| **Field acc.** | N/A (zero field coverage) |
| **Mixed acc.** | 98.6% |

## Goal — why this notebook existed

The first attempt to improve the lab model by **aggregating multiple PlantVillage sources**. The
hypothesis: more data → a better Swin-T plant-disease model. The notebook pooled three PlantVillage
sources (518,000 gathered images) and deduplicated them, intending to train on a larger, cleaner
pool than the single-source v1 baseline.

## Strategy & methodology

- **Backbone:** Swin-T, PlantVillage-pretrained.
- **Data:** three PlantVillage sources pooled to 518,000 gathered images.
- **Dedup:** cross-source full-file SHA-256 — hash every image across all three sources and drop
  exact duplicates.

## Why this strategy

The reasoning was the standard "more data is better" instinct: if one PlantVillage source gives a
decent lab model, three sources deduplicated should give a better one. SHA-256 full-file hashing was
chosen as the dedup primitive because it is exact and source-agnostic — a duplicate that appears in
two sources under different filenames would still be caught by an identical hash.

## Code & config highlights

- Cross-source full-file SHA-256 dedup over 518,000 images.
- Swin-T fine-tune on the deduplicated pool.

## Results & logged outcomes

- **Mixed accuracy 98.6%** — a strong lab number.
- **Zero field coverage** — no field data was included, so no field accuracy is reported.
- Base lab accuracy reached **98.59%**.

## Mistakes & diagnosed failures

Two failures, one embarrassing and one operational:

1. **The three sources were not independent — they were renderings of one source.** On inspection,
   all three PlantVillage sources turned out to be derivatives of the **same ≈172,000 lab leaves** —
   color, grayscale, and segmented renders. The "518,000 gathered images" were mostly the same
   images in three rendering styles. The pool gave essentially no new information beyond the single
   source, which is why lab accuracy was good but unremarkable (98.59%) and field accuracy was
   undefined.
2. **The dedup was too slow.** The cross-source full-file SHA-256 ran for approximately **nine
   hours and timed out** on the Kaggle T4 runtime. The very dedup strategy chosen to make the pool
   trustworthy could not complete within the GPU session.

## Learnings (carried forward)

1. **Verify data provenance before pooling.** Three sources that look different can be renderings
   of one source. Inspect raw per-source counts and a few samples per source before trusting "more
   data." This is the founding data-hygiene lesson of the arc.
2. **Match the dedup algorithm to the scale.** Full-file SHA-256 over 500k images is too slow on a
   T4. v2.0 replaced it with a **within-source MD5-of-prefix** strategy that removes the vast
   majority of exact and near-exact duplicates in a fraction of the time; after dedup, 90,973 images
   were retained (92,031 duplicates removed) and each was verified with `PIL.Image.verify()` before
   entering the pipeline (zero corrupt images).
3. **Lab accuracy ≈ 99% is not success.** v1 hit 98.6% mixed on a lab-only pool and had zero field
   coverage. The number that matters — field accuracy — was not even measurable here. This notebook
   is the last lab-only iteration; from v2.0 onward, field data is always present and reported.

## Research cross-reference

- **PlantVillage** (Mohanty et al., 2016) — the dataset, and the source of the "all three sources
  are renderings of one lab leaf set" gotcha that PlantVillage mirrors are notorious for.
  https://www.frontiersin.org/journals/plant-science/articles/10.3389/fpls.2016.01419/full
- **Swin Transformer** (Liu et al., ICCV 2021) — the backbone.
  https://arxiv.org/abs/2103.14030
- See [`00_research_cross_reference.md`](00_research_cross_reference.md).
