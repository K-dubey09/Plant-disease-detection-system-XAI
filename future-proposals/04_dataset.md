# 04 — Dataset & Data

## Current state

The model is trained on a union of the public **PlantVillage** dataset (54,306 images, 38 classes,
14 crops — Hughes & Salathé 2015, open access; spMohanty mirror under CC BY-SA 3.0) and an
**author-collected field set** of real-world leaf photographs. Validation is held out in three
splits: field (525), lab (1,520), mixed (2,045). The class space is exactly PlantVillage's 38
classes — 14 crop species, all temperate/common garden crops. **The field labels were assigned by
the author, not independently verified by a plant pathologist**, which is the project's single
biggest honesty gap. PlantVillage is lab-style and geographically narrow; the field set is small
and not redistributed in the repository.

## Proposal

**A. Independent expert validation of the field labels — top priority.** Engage a plant
pathologist (university agriculture department or extension service) to re-label the 525-image
field validation set and a fresh held-out set. Publish the inter-rater agreement (Cohen's κ) and
the post-correction accuracy. Until this is done, every reported field number is a measurement
against unverified ground truth, and the MODEL_CARD's "no independent expert validation" caveat
stays. This is cheap relative to its impact on trust.

**B. Expand crop and disease coverage.** PlantVillage covers 14 temperate crops and omits major
food-security crops — rice, wheat, sugarcane, millet, cotton — that dominate the author's region.
Add per-crop field datasets (e.g., rice leaf disease sets that exist publicly) and train new class
heads, versioning the model and label list as `v4.x`. This materially broadens who the app is
useful to.

**C. Regional field data collection.** Collect field images in the deployment region (India /
tropical climate) to counter PlantVillage's geographic and lighting bias. The DANN recipe already
targets lab→field; a regionally-representative field set lets the domain-adversarial head align to
the actual deployment distribution, not just "field in general."

**D. Public, versioned field-dataset release.** Package the field set (post expert-labeling) as a
versioned, documented dataset under CC BY 4.0 with a proper train/val/test split, a datasheet, and
class manifests — the same transparency standard the model already meets. This lets others
reproduce and benchmark, and turns the project's field data from a private asset into a referenced
resource.

**E. Privacy-preserving active-learning loop.** With explicit opt-in, the app can locally flag
low-confidence field scans (below the "Low" confidence tier) and offer the user a one-tap "submit
for labeling" action that uploads that single image to a labeling queue. This grows the field set
where the model is actually weak, with the user's consent and only for the images they choose —
never a silent upload.

**F. Class-imbalance audit + min-sample thresholds.** Audit per-class training counts, define a
minimum-sample threshold below which a class is flagged "experimental" in the UI, and report
per-class support alongside the per-class precision/recall in the metrics artifact. This prevents
a thinly-trained class from presenting the same confidence surface as a well-trained one.

## Feasibility & effort

| Sub-proposal | New data? | External dependency? | Effort | Impact |
| --- | :---: | :---: | :---: | :---: |
| A. Expert validation | re-label existing | yes (expert) | Low–Med | High |
| B. New crops | yes | datasets | Med | High |
| C. Regional collection | yes | fieldwork | Med–High | High |
| D. Public dataset release | no (package existing) | no | Low | Med |
| E. Active-learning loop | grows over time | upload infra | Med | Med |
| F. Imbalance audit | no | no | Low | Med |

## Risks & open questions

Expert validation (A) can go two ways: if the labels largely agree, it validates the 86.7%; if
they disagree substantially, the headline number drops and the MODEL_CARD must be revised
downward. Either outcome is a net positive for honesty, but it must be done before any expansion
(B/C) builds on top of possibly-wrong labels. Regional collection (C) and the public release (D)
raise consent and licensing questions: field photographs of plants on other people's land, and the
CC BY-SA 3.0 obligation on derived works from PlantVillage. The active-learning loop (E) must
remain strictly opt-in and auditable — it is the one proposal here that introduces an upload path,
and it must not quietly become telemetry.
