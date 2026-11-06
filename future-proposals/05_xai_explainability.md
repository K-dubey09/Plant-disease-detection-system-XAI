# 05 — Explainability (XAI)

## Current state

LeafGuard explains each prediction with **occlusion sensitivity**: an 8×8 grid (64 greyed-cell
forward passes) where each cell's contribution is the predicted-class confidence drop, rendered as
a jet-colormap overlay on the captured leaf. This path is **model-agnostic by necessity** — TFLite's
`Interpreter` exposes neither intermediate conv activations nor gradients, so true Grad-CAM is
unavailable on the current FP32 TFLite graph. The heatmap runs on a background coroutine after the
headline diagnosis (a "Generating explanation…" placeholder fills the slot), costs ~6 s on-device
(~94 ms/inference × 64 cells), and uses a separate single-pass `classifyForSaliency` path (no TTA)
because 3-view TTA on every occluded patch would 3× the cost for no explainability gain.

## Proposal

**A. Grad-CAM via an ONNX Runtime on-device path.** The cleanest upgrade is to ship the same model
as an ONNX graph and run it through ONNX Runtime's Android execution provider, which **does**
expose intermediate layer activations and supports gradient backprop — enabling true Grad-CAM and
Grad-CAM++. The trade-off is a larger runtime dependency and a different inference path than the
current TFLite singleton, but the explanation quality jumps from a coarse 8×8 grid to a
pixel-aligned, lesion-localized heatmap. The FP32 TFLite path can remain the headline classifier
for latency; ONNX-RT can run only for the explanation step.

**B. Adaptive, higher-resolution occlusion.** If the ONNX-RT path is not taken, evolve occlusion
itself: a 16×16 grid with an early-exit rule that stops refining a cell once its saliency variance
is below a threshold, bounding the worst-case cost while sharpening localized lesions. Combine with
a coarse-to-fine two-stage pass (8×8 to find the region, then 8×8 within it) for a sharper map at
similar total cost.

**C. Pixel-level attribution via the source model.** Run Integrated Gradients / SmoothGrad on the
PyTorch source model offline (in a notebook) over the validation set, and ship a small set of
reference attributions per class as part of the result screen's "what the model typically looks at"
educational content. This is offline-only and does not change on-device inference, but it gives
users a calibrated expectation of what a good explanation looks like for each disease.

**D. Counterfactual / contrastive explanations.** Beyond "what mattered," offer "what minimal change
would flip the prediction" — e.g., a side-by-side of the input with a synthetically-perturbed
version that crosses the decision boundary. This is the most demanding proposal and depends on a
gradient-accessible model (A), but it is the strongest trust affordance for a misdiagnosis-prone
task.

**E. Explanation-quality evaluation.** Today the heatmap's quality is judged by inspection.
Annotate a small set of leaves with lesion bounding boxes and measure localization metrics
(pointing game accuracy, IoU of the top-saliency region vs. the box). This turns "the explanation
looks reasonable" into "the explanation points at the lesion on X% of the held-out set."

**F. Explanation literacy in the UI.** The jet overlay is opaque to a non-ML user. Add a short,
plain-language caption ("warm regions are where the model looked to decide; it is approximate, not
a diagnosis map") and a tap-to-explain affordance, so the heatmap is read correctly rather than
over-trusted.

## Feasibility & effort

| Sub-proposal | New runtime dep? | Needs gradients? | Effort | Impact |
| --- | :---: | :---: | :---: | :---: |
| A. ONNX-RT Grad-CAM | yes (onnxruntime-android) | yes | Med–High | High |
| B. Adaptive occlusion | no | no | Low–Med | Med |
| C. Offline IG reference | no (notebook) | yes (offline) | Low | Low–Med |
| D. Counterfactuals | yes | yes | High | Med |
| E. Localization eval | no | no | Med | Med |
| F. UI literacy | no | no | Low | Med |

## Risks & open questions

The Grad-CAM path (A) is the headline upgrade but introduces a second inference runtime, which
complicates the model-integrity story (a second graph must be SHA-guarded) and the memory budget
(ONNX-RT resident alongside the TFLite singleton on a 6 GB device must be measured). Grad-CAM on
ViT-style transformers also behaves differently than on CNNs — block-level attention maps can be
diffuse — so the result may be less visually crisp than a CNN Grad-CAM until tuned. The explanation
literacy work (F) is cheap and high-value and should land regardless of which technical path is
chosen: an explanation a user misreads is worse than no explanation.
