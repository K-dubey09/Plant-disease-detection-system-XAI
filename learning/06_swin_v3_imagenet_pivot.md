# Notebook 6 — `leafguard-swin-finetune-v3` (ImageNet pivot)

| | |
| --- | --- |
| **Kaggle slug** | `leafguard-swin-finetune-v3` |
| **URL** | https://www.kaggle.com/code/kushagradubey09/leafguard-swin-finetune-v3 |
| **Role** | the pivot — abandon the PlantVillage-overfit backbone for an ImageNet-pretrained Swin |
| **Backbone** | Swin-Tiny (`microsoft/swin-tiny-patch4-window7-224`, ImageNet-pretrained, `ignore_mismatched_sizes=True`) |
| **Field acc.** | **67.3%** |
| **Mixed acc.** | 94.5% |

## Goal — why this notebook existed

v2.2 proved that the PlantVillage-overfit backbone was the ceiling (34% field, stuck while lab
climbed). This notebook's job was to **change the backbone** — swap the PV-overfit Swin for an
**ImageNet-pretrained** Swin-Tiny, on the reasoning that ImageNet's general object-recognition
pre-training would transfer to field leaves better than PlantVillage's lab-only pre-training. The
dataset plan was reused from v2.2 unchanged, so the only variable changed was the backbone — a clean
experiment.

## Strategy & methodology

- **Backbone:** ImageNet-pretrained Swin-Tiny (`microsoft/swin-tiny-patch4-window7-224`), loaded
  with `ignore_mismatched_sizes = True`. The 1000-way ImageNet head was replaced with a **38-way
  disease head**, preserving the app's I/O contract (`[1,224,224,3] → [1,38]`) so the model is a
  drop-in.
- **Data:** the v2.2 dataset plan, unchanged.
- **Domain-bridge recipe** (all new here, carried into v3.5):
  - **CutMix/MixUp** with combined probability 0.5, `λ ~ Beta(0.4, 0.4)`, `λ ← max(λ, 1 − λ)`.
  - **Three-fold field oversampling** via `WeightedRandomSampler`.
  - **Layer-wise learning-rate decay** of 0.75.
  - **4-view test-time augmentation** (original, horizontal flip, ±10° rotation), softmaxes averaged.
- **Two-stage schedule:**
  1. linear probe — 3 epochs @ `1 × 10⁻³`, backbone frozen;
  2. full fine-tune — 12 epochs @ `2 × 10⁻⁵`, layer-wise LR decay 0.75 across the four encoder
     stages, cosine LR.

## Why this strategy

The v2.2 diagnosis was "the backbone over-specialized to PlantVillage." The fix is to use a backbone
whose pre-training is **more general** — ImageNet, not PlantVillage. The domain-bridge recipe
(CutMix/MixUp, field oversampling, TTA) is the toolkit for making a generally-pretrained backbone
adapt to a specific target domain without forgetting the general features: CutMix/MixUp force smooth
multi-label decision regions; oversampling rebalances the lab-dominated stream; TTA reduces
single-crop approximation error at evaluation.

The 38-way head swap (replacing ImageNet's 1000-way head) is an engineering decision as much as a
modeling one: by preserving the I/O contract, the model can be swapped in the app by changing one
line (the model filename) plus the SHA-256 digest.

## Code & config highlights

- First ImageNet-pretrained backbone in the arc (`microsoft/swin-tiny-patch4-window7-224`).
- `ignore_mismatched_sizes = True` to load the ImageNet head shape onto the 38-class output.
- First use of **CutMix + MixUp** (`Beta(0.4, 0.4)`, `max(λ, 1−λ)`).
- First use of **three-fold field oversampling** via `WeightedRandomSampler`.
- First use of **layer-wise LR decay** (0.75 here; 0.85/0.9 in v3.5).
- First use of **test-time augmentation** (4 views here; 3 views in v3.5).
- 38-way head preserving the app I/O contract — the drop-in model swap is born here.

## Results & logged outcomes

- **Field 67.3%, lab 99.3%, mixed 94.5%.** A +33 pp jump over v2.2's 34% — the biggest single-version
  gain until v3.5.
- **Broke the ≈70 percentage-point PlantVillage→PlantDoc gap** that had capped v2.x.

## Mistakes & diagnosed failures

No catastrophic failure — this is the first unambiguous success. But the **residual** field errors
shifted in character: from v2.2's *cross-species* confusion (grape→apple) to *same-species disease*
confusion — potato early blight vs late blight, corn northern leaf blight vs cercospora leaf blight.
The model could now identify the species; it was confusing diseases *within* a species.

The new diagnosis: **ImageNet-supervised features still over-specialize.** ImageNet pre-training is
better than PlantVillage pre-training for field transfer, but it is still a *supervised* prior
committed to ImageNet's 1000 object categories, and it is not stable enough under the
PlantVillage-to-field shift to push field accuracy past ~67%. The ceiling moved; it did not vanish.

## Learnings (carried forward)

1. **ImageNet pretraining >> PlantVillage-overfit pretraining for field transfer** (+33 pp). The
   backbone swap was the single highest-leverage change in the arc up to this point.
2. **CutMix/MixUp + field oversampling + TTA are an effective domain-bridge toolkit** — they move a
   generally-pretrained backbone toward a specific target domain without destroying the general
   features. All three carried into v3.5.
3. **Supervised features have a ceiling (~67%).** The residual same-species disease confusion is the
   signal that the representation is still over-specialized. The next jump requires a backbone that
   is not committed to *any* supervised taxonomy — i.e., self-supervised DINOv2 (v3.5).
4. **The I/O-contract-preserving head swap enables one-line model swaps in the app.** This notebook
   established the contract that made the v3.5 DINOv2 swap a one-line change plus a SHA-256 update.

## Research cross-reference

- **Swin Transformer** (Liu et al., ICCV 2021) — the backbone.
  https://arxiv.org/abs/2103.14030
- **CutMix** (Yun et al., ICCV 2019) and **MixUp** (Zhang et al., ICLR 2018) — the mixing
  augmentation introduced here.
- **ImageNet pretraining** — the more-general supervised prior that beat PlantVillage pre-training
  but still capped at 67%.
- See [`00_research_cross_reference.md`](00_research_cross_reference.md).
