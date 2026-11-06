# Research cross-reference

This document maps every external research contribution that informed LeafGuard's training arc to
**where and how the project applied it**, with the real source URL for each. It is the literature
layer of the `learning/` folder — read it alongside the per-notebook files to see which paper
motivated which decision, and at which iteration it entered.

The IEEE-format project report (`docs/IEEE_format/LeafGuard_IEEE_Project_Report.pdf`) cites these as
numbered references; this file makes the application explicit and adds the URLs.

## The papers that shaped the design

### DINOv2 — self-supervised ViT backbone (the v3.5 backbone swap)

- **Paper:** Maxime Oquab et al., *"DINOv2: Learning Robust Visual Features without Supervision,"*
  Meta AI, 2023.
- **URL:** https://arxiv.org/abs/2304.07193
- **Where applied:** Notebook 7 (`leafguard-swin-finetune-v3-5-0-0`), backbone swap — the single
  largest component in the ablation (+11.9 pp field accuracy).
- **How applied:** The backbone `facebook/dinov2-small` (ViT-S/14, ≈22.1 M parameters, 384-dim
  CLS-token features) replaces the ImageNet-pretrained Swin-Tiny. The 142-million-image
  self-supervised pre-training objective produces features whose discriminative structure is learned
  **without labels**, so they encode general visual structure (leaf texture, lesion shape, background
  context) without committing to a supervised categorization. This is the project's central lesson:
  supervised ImageNet/PlantVillage features over-specialize and cap at ~67% field accuracy (v3);
  self-supervised features transfer to cluttered field leaves and break the ceiling.
- **Why it beat the alternatives:** Both the PlantVillage-overfit Swin (v2.x, 34% field) and the
  ImageNet-pretrained Swin (v3, 67.3% field) encode supervised categorization priors that do not
  transfer cleanly to cluttered field leaves. DINOv2 does not commit to a taxonomy, so it is freer
  to adapt to the field distribution during fine-tuning.

### Domain-Adversarial Neural Networks (DANN) + Gradient Reversal Layer

- **Paper:** Yaroslav Ganin et al., *"Domain-Adversarial Training of Neural Networks,"* JMLR 2016
  (expands the ICML 2015 *"Unsupervised Domain Adaptation by Backpropagation"*).
- **URL:** https://arxiv.org/abs/1505.07818
- **Where applied:** Notebook 7 — the gradient-reversal domain-adversarial head; the
  second-largest v3.5 component (+7.1 pp).
- **How applied:** A 2-class lab/field domain head (Linear → ReLU → Linear) shares the 384-dim
  DINOv2 feature `z` with the disease head, connected through a **gradient-reversal layer (GRL)**
  that is the identity in the forward pass, `R(z) = z`, but negates its gradient by `λd` in the
  backward pass, `∂R/∂z = −λd·I`. The backbone therefore receives a domain-classification gradient
  of opposite sign, pushing `z` toward a region the domain classifier **cannot** label as lab-or-field
  — i.e., domain-invariant features. The domain weight follows a schedule `λd = 0.1 → 0.3 → 0.1`
  across the three progressive-unfreeze steps, so the adversarial signal is strongest while the last
  blocks are learning and eased during the final low-LR polish. The domain head is **dropped at
  export** — the deployed graph is purely the 38-class classifier.
- **Why it was needed:** v2.0 had shown the model was exploiting the uniform grey PlantVillage
  background as a shortcut cue. The GRL directly attacks that cue by forcing the backbone to produce
  features indistinguishable across lab/field, so the disease head cannot lean on the background.

### Class-Balanced Loss Based on Effective Number of Samples

- **Paper:** Yin Cui, Menglin Jia, Tsung-Yi Lin, Yang Song, Serge Belongie, *"Class-Balanced Loss
  Based on Effective Number of Samples,"* CVPR 2019.
- **URL:** https://openaccess.thecvf.com/content_CVPR_2019/html/Cui_Class-Balanced_Loss_Based_on_Effective_Number_of_Samples_CVPR_2019_paper.html
- **Where applied:** Notebook 5 (`leafguard-swin-finetune-v2-2`) onward — replaces naive
  inverse-frequency class weighting.
- **How applied:** Each class `c` with `nc` samples is re-weighted by `wc = (1 − β) / (1 − β^nc)`
  with `β = 0.999`. This asymptotes to `1/nc` for small classes but **saturates for large ones**,
  giving a softer rebalancing than naive inverse-frequency weighting.
- **Why it was needed:** v2.x found that naive inverse-frequency weighting over-weighted the rarest
  classes and destabilized training — the rarest classes seized control of the gradient. The
  effective-number formulation keeps the rebalancing intent without handing rare classes outsized
  influence. (Corn, with 22,000 images over only 4 classes, additionally got a hard cap of 800 to
  prevent corn dominance — a domain-specific complement to the loss-side rebalancing.)

### PlantVillage — the canonical lab benchmark

- **Paper:** S. P. Mohanty, D. P. Hughes, M. Salathé, *"Using Deep Learning for Image-Based Plant
  Disease Detection,"* Frontiers in Plant Science, 2016.
- **URL:** https://www.frontiersin.org/journals/plant-science/articles/10.3389/fpls.2016.01419/full
- **Where applied:** The lab training source across all seven notebooks (capped per-class to avoid
  drowning the field data from v2.0 onward).
- **How applied / the lesson:** PlantVillage is a **laboratory** benchmark — leaves photographed
  against uniform backgrounds under controlled lighting. The entire v2.x failure arc is the story of
  a model overfit to PlantVillage failing on real field photographs (PlantDoc + field-acquired
  images). The project's central domain gap — the ≈70 percentage-point PlantVillage→PlantDoc gap —
  is the gap this paper's dataset implicitly created by being lab-only. LeafGuard's field set and
  the DANN recipe exist to close exactly this gap.

### PlantDoc — the field-stress dataset

- **Paper:** Davesh Singh, Bharti Jain, Debzani Deb, Srimanta Mandal, Shishir Kumar
  Prasad, *"PlantDoc: A Dataset for Visual Plant Disease Detection,"* KDD 2019.
- **URL:** https://arxiv.org/abs/1911.10317
- **Where applied:** Notebook 3 onward — the first field-stress dataset added alongside the
  field-acquired corn collection and the author's own field set.
- **How applied / the lesson:** PlantDoc (~2,598 field photographs across 13 plant species) was the
  first honest field signal. Adding it in v2.0 produced the **11% field collapse** that diagnosed
  "lab drowning field," and the **34% ceiling** in v2.2 that proved the PlantVillage-overfit
  backbone could not identify plant species on field images (cross-species confusion:
  grape→apple, peach→blueberry). PlantDoc is the benchmark against which every field-accuracy number
  in this folder is measured.

### Swin Transformer — the v2.x / v3 backbone

- **Paper:** Ze Liu et al., *"Swin Transformer: Hierarchical Vision Transformer using Shifted
  Windows,"* ICCV 2021.
- **URL:** https://arxiv.org/abs/2103.14030
- **Where applied:** Notebooks 2–6 — the Swin-Tiny backbone, first PlantVillage-pretrained (v2.x)
  then ImageNet-pretrained (`microsoft/swin-tiny-patch4-window7-224`, v3).
- **How applied / the lesson:** Swin-Tiny (≈28 M parameters) was the backbone through the entire
  diagnose-the-domain-gap arc. The v3 ImageNet-pretrained Swin reached 67.3% field — better than
  the PV-overfit Swin (34%), but still capped by supervised-feature over-specialization. The
  comparison Swin-Tiny (28 M) vs DINOv2 ViT-S/14 (22.1 M) — DINOv2 is **smaller and more accurate**,
  an additional on-device advantage that made the v3.5 swap a strict improvement.

## Techniques applied (used as methods, not as the project's contribution)

These are standard training techniques the project adopted; they are named in the per-notebook
files and the IEEE report but are not LeafGuard's inventions:

- **CutMix** (Yun et al., ICCV 2019) and **MixUp** (Zhang et al., ICLR 2018) — mixed-region and
  convex-mixing augmentation, applied with combined probability 0.5, `λ ~ Beta(0.4, 0.4)`,
  `λ ← max(λ, 1 − λ)`. Entered the recipe in v3 (Notebook 6) to force smooth multi-label decision
  regions; carried into v3.5.
- **Progressive / layer-wise unfreezing** — a staged release of backbone parameters (heads only →
  last four ViT blocks → all blocks) with a discriminative layer-wise learning-rate decay
  `ηℓ = ηbase · γ^(L−1−ℓ)`, `γ ∈ {0.85, 0.9}`, so shallower, more general blocks learn slowest.
  Entered the recipe in v2.2 (the first three-stage schedule); in v3.5 it is the largest
  load-bearing guardrail (removal costs 14.3 pp) because it protects the self-supervised
  pre-training from catastrophic forgetting.
- **Multi-crop consistency regularization** — a KL-divergence loss
  `Lcons = DKL(p(xweak) ∥ p(xstrong))` between a strong-aug crop and a weaker consistency crop,
  applied only to field images at `λc = 0.2`. Entered in v3.5 (Notebook 7) to push the field decision
  boundary to be invariant to the augmentation crop.
- **Test-time augmentation (TTA)** — averaging softmaxes over multiple views. v3 used 4 views
  (original, flip, ±10° rotation); v3.5 settled on 3 views (original, horizontal flip, central
  crop of 0.875) — the exact recipe used **at on-device inference**, so the validated field number
  tracks the on-device number rather than a single-crop approximation.
- **ONNX → TFLite conversion with graph surgery** — ImageNet mean/std normalization and the final
  softmax are inserted as ONNX nodes *before* conversion, so the deployed graph accepts raw `[0,1]`
  pixels and emits real probabilities. This makes the model a drop-in: the Swin→DINOv2 swap changed
  the app code by one line (the model filename) plus the SHA-256 digest, because the I/O contract
  was identical. The legacy TorchScript exporter (`dynamo=False`) is required because torch 2.9+'s
  default dynamo exporter lowers DINOv2 window-attention partitioning to an ONNX `Loop` op that
  `onnx2tf`'s flatbuffer-direct path cannot lower.

## The honest note that ties them together

None of these papers is a recipe you follow once. Each entered the arc at a specific iteration,
**in response to a specific diagnosed failure**:

| Failure diagnosed | Paper/technique brought in to address it | Iteration |
| --- | --- | --- |
| lab drowning field (v2.0, 11%) | separate lab/field caps; effective-number weighting | v2.1, v2.2 |
| underfit (v2.1, 49%) | three-stage 15-epoch schedule | v2.2 |
| PV→PlantDoc ceiling (v2.2, 34%) | abandon PV-overfit backbone → ImageNet Swin (Swin paper) | v3 |
| ImageNet-feature over-specialization (v3, 67.3%) | self-supervised DINOv2 backbone | v3.5 |
| grey-background shortcut cue | DANN / Gradient Reversal Layer | v3.5 |
| rare-class destabilization | effective-number class weighting (Cui) | v2.2 → v3.5 |
| catastrophic forgetting of self-supervised pre-training | progressive unfreezing | v2.2 → v3.5 |
| field-boundary sparsity / over-confidence | multi-crop consistency + CutMix/MixUp | v3.5 |

This is the through-line of the whole `learning/` folder: **the literature was not a shopping list;
it was a toolbox opened one tool at a time, each tool matched to a measured failure.**
