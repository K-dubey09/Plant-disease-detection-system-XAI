# LeafGuard — Future Proposals

This folder collects **forward-looking improvement proposals** for LeafGuard — what a next major
release could pursue, with the rationale, feasibility, and risk for each. None of these are
committed work; they are a structured backlog so the project's direction is visible to reviewers
and future contributors rather than living only in the author's head.

The current shipped state (app **v2.6.0.0**, model **DINOv2 v3.5.0.0**) is the baseline each
proposal is measured against: **86.7% field / 99.3% lab / 96.1% mixed** top-1 accuracy, on-device
TFLite inference with 3-view TTA, an occlusion-sensitivity explanation, and seven public training
notebooks documenting the iteration arc. See [`MODEL_CARD.md`](../MODEL_CARD.md) and
[`DATASHEET.md`](../DATASHEET.md) for the full current-state reference.

## Proposals

| File | Domain | One-line | Effort | Impact |
| --- | --- | --- | :---: | :---: |
| [`01_model.md`](01_model.md) | Model | Quantization, larger backbone, open-set rejection, DINOv2 v3.6 | Med–High | High |
| [`02_architecture.md`](02_architecture.md) | App/system architecture | Modularization, App Bundle, SQLCipher, optional server-assist, model registry | Med | High |
| [`03_implementation.md`](03_implementation.md) | Engineering | Instrumented CI tests, baseline profiles, detekt/ktlint, reproducible builds | Med | Med |
| [`04_dataset.md`](04_dataset.md) | Dataset & data | Expert validation, regional + new-crop data, public versioned field dataset | Med | High |
| [`05_xai_explainability.md`](05_xai_explainability.md) | Explainability | Grad-CAM via ONNX-RT, higher-res occlusion, counterfactuals, localization eval | Med | Med |
| [`06_evaluation_validation.md`](06_evaluation_validation.md) | Evaluation | Prospective field trial, expert audit, robustness + calibration suites | Med | High |

## How to read each proposal

Every proposal file follows the same structure so they are comparable:

- **Current state** — what exists today (with the file/symbol or metric that anchors it).
- **Proposal** — the concrete change(s), broken into sub-proposals labeled A, B, C…
- **Feasibility & effort** — what it would take, dependencies, and an honest effort estimate.
- **Risks & open questions** — what could go wrong or what must be decided first.

## Cross-cutting priorities

If only three things from this folder are pursued, the highest-leverage ones — in order — are:
(1) **independent expert validation of the field labels and accuracy** (`04`, `06`) — the project's
single biggest honesty gap; (2) **an open-set / out-of-distribution rejection path** (`01`) so an
unsupported species is not confidently mislabeled; and (3) **a quantized model variant + App
Bundle delivery** (`01`, `02`) so the app reaches devices below the current 6 GB / 86.8 MB bar.
