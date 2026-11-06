# Datasheet — LeafGuard Training & Evaluation Data

> Following the *Datasheets for Datasets* framework (Gebru et al., 2018/2021). This datasheet
> documents the datasets used to train and evaluate the LeafGuard DINOv2 v3.5.0.0 plant-disease
> classifier, so that users of the model can understand the data's provenance, composition, and
> limits, and so the model's behavior can be interpreted in light of the data it learned from. The
> companion model documentation is [`MODEL_CARD.md`](MODEL_CARD.md).

**Important — redistribution.** LeafGuard does **not** redistribute the PlantVillage images in this
repository. This repository ships only: the trained TFLite model (`plant_disease.tflite`), the
38-class label list (`labels.txt`), and an authored treatment-suggestion knowledge base
(`disease_suggestions.json`). Researchers wishing to reproduce training must obtain PlantVillage
directly from its canonical sources (linked in §6) under its own license.

---

## 1. Motivation

The dataset was assembled to train an on-device plant-disease classifier that works on real field
photographs, not only on clean lab-style images. The central problem the project addresses is the
**lab→field domain gap**: models trained only on PlantVillage (detached leaves on uniform
backgrounds) collapse when shown real leaves in messy field conditions — the LeafGuard training arc
documents field accuracy as low as 11% in early iterations despite near-perfect lab accuracy. The
data was therefore curated to include both a large lab-distribution base (PlantVillage) and a
smaller but essential field-distribution set, paired with a domain-adversarial training objective
to align them. The intended beneficiaries are gardeners and smallholder farmers without lab access,
and the secondary audience is ML researchers studying domain adaptation for agricultural computer
vision. Funding was the author's undergraduate research resources; no commercial entity
commissioned or constrained the data choices, and the data was not created to reinforce existing
assumptions about any group.

## 2. Composition

The training data is a union of two sources. (a) The public **PlantVillage** dataset — 54,306
images of healthy and diseased crop leaves across **38 classes** spanning **14 crop species**
(Hughes & Salathé, 2015), used as the lab-distribution base. (b) An **author-collected field set**
of real-world leaf photographs capturing the target deployment domain: varying backgrounds, natural
lighting, multiple leaves per frame, and non-canonical capture angles. The model's class space is
exactly PlantVillage's 38 classes, and the field set is labeled into that same 38-class space (no
new classes were introduced).

Validation is held out in three non-overlapping splits: **field (525 images)**, **lab (1,520
images)**, and **mixed (2,045 images)**. These splits are disjoint from the training partition;
field oversampling (3×) and augmentation operate on training data only. There are no "missing" or
placeholder labels — the 12 `___healthy` classes are positive (healthy) labels, not absences. The
data consists of images only; no associated metadata tables, captions, or free text accompany the
images, and no data is withheld on the basis of a third-party license beyond PlantVillage's own
terms. The full 38-class label list (in canonical order, matching the model's output index) is
committed at `app/src/main/assets/models/labels.txt`.

The 38 classes cover 14 crop species: Apple, Blueberry, Cherry, Corn (maize), Grape, Orange,
Peach, Pepper (bell), Potato, Raspberry, Soybean, Squash, Strawberry, and Tomato — each as one or
more disease states or `healthy`.

## 3. Collection

**PlantVillage.** Collected and curated by David Hughes and Marcel Salathé at Penn State
(Hughes & Salathé, 2015) as an open-access repository of expertly curated healthy and infected
crop-leaf images, released to advance automated plant-disease research. The images are lab-style:
detached leaves photographed against uniform grey/black backgrounds under controlled lighting. The
canonical mirrors are spMohanty's GitHub/Kaggle releases of the dataset. Funding was academic
(National Science Foundation / Penn State); the collection is not tied to any user-identification
process, and the leaves were not sourced from identifiable private individuals.

**Field set.** Collected by the author (Kushagra Dubey) as real-world field photographs taken in
natural conditions with a handheld phone camera — the same capture conditions the deployed app is
used in. The field set is the project's contribution to closing the domain gap; its labels were
assigned by the author by visual reference to the PlantVillage class definitions and general
agronomic references. **The field labels were not independently verified by a professional plant
pathologist** — this is the single most important provenance caveat (see §5 and MODEL_CARD
Caveats). The field set is not redistributed in this repository.

## 4. Preprocessing, Cleaning, and Labeling

**Preprocessing for training.** Images are resized and center-cropped to 224×224. Augmentation
includes random resized-crop, horizontal flip, CutMix, MixUp, and multi-crop consistency. The
field partition is oversampled 3×. ImageNet normalization (mean = [0.485, 0.456, 0.406], std =
[0.229, 0.224, 0.225]) is applied — and, for the deployed TFLite, baked into the graph as ONNX
`Sub`/`Div` nodes so the app feeds raw `[0,1]` pixels.

**Labeling.** PlantVillage ships with its canonical 38-class labels, which were adopted verbatim —
no relabeling or class reordering was applied (the model's output index matches `labels.txt`
exactly). Field-set labels were assigned by the author. No raw data was deleted as "unusable"; the
field split was partitioned off before oversampling so evaluation stays honest.

## 5. Uses

**Recommended uses.** Training and evaluating image classifiers for the 38 PlantVillage classes;
studying domain adaptation (lab→field) and the effect of DANN, oversampling, and TTA on field
generalization; benchmarking on-device inference and explainability pipelines (occlusion
sensitivity under a fixed TFLite budget).

**Uses the dataset supports but that warrant care.** Transfer learning to related crops or
diseases outside the 38 classes (the source distribution is narrow and geographically biased);
using the field split's labels as ground truth for a publication without independent expert
verification.

**Tasks the dataset should not be used for.** Diagnosing crops outside the 14 species; detecting
diseases not in the 38 classes; any safety-, regulatory-, or insurance-critical decision without
independent laboratory confirmation; and any re-identification or surveillance task — the data was
not collected for that and contains no person-identifying content.

## 6. Distribution

**PlantVillage** is open access (Hughes & Salathé, 2015). The widely used spMohanty mirror
([github.com/spmohanty/plantvillage-dataset](https://github.com/spmohanty/plantvillage-dataset))
and the Kaggle "PlantVillage Disease Classification Challenge" are distributed under a **Creative
Commons Attribution-ShareAlike 3.0 (CC BY-SA 3.0)** license. Third parties should obtain the
images from those canonical sources and respect that license; this repository does not redistribute
the images.

**The field set** is not redistributed in this repository and has no separate public license at
this time; it is referenced here for transparency about the model's training data. Researchers who
need it for replication should contact the author via GitHub issues.

**What this repository does distribute:** the trained `plant_disease.tflite` (MIT), `labels.txt`
(38-class canonical names), and `disease_suggestions.json` (authored IPM knowledge base, MIT). The
treatment suggestions are derived from public agronomy guidance and are general information, not
prescriptive advice.

## 7. Maintenance

The dataset and this datasheet are maintained by the author (Kushagra Dubey) through the
`K-dubey09/Plant-disease-detection-system-XAI` GitHub repository. The trained model committed in
the repo is fixed to the pinned SHA-256 (`3660cfdb…`, full digest in MODEL_CARD.md); any change to
the model produces a new SHA-256 and a new model-card version. Known errors in the dataset or
labeling should be reported via GitHub issues. If a future release re-trains on additional data
(e.g. more crops, expert-verified field labels), this datasheet will be versioned alongside the
model card, and the per-split sizes and accuracy table will be updated. The PlantVillage upstream
is a fixed historical release and is not expected to change; the field set may grow in future
iterations, which will be reflected here.

---

### References

- Hughes, D. P., & Salathé, M. (2015). *An open access repository of images on plant health to
  enable the development of mobile disease diagnostics.* arXiv:1511.08060.
- Gebru, T., Morgenstern, J., Vecchione, B., et al. (2021/2018). *Datasheets for Datasets.*
  Communications of the ACM.
- Mitchell, M., Wu, S., Zaldivar, A., et al. (2019). *Model Cards for Model Reporting.* FAT*.
