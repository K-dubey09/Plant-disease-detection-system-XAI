# LeafGuard — IEEE-Format Deliverables

This directory contains the IEEE-format project report for the LeafGuard
plant-disease-detection system, regenerated from the latest research notebook
sequence (v1 → v3.5.0.0) and the current Android codebase.

> The IEEE-format **research paper is withheld** from this public repository until publication and
> is **not committed here**. It contains no credentials or access tokens and will be added once
> published.

| File | Description |
| --- | --- |
| `LeafGuard_IEEE_Project_Report.pdf` | IEEE-format project report (~40 pp, two-column). Covers motivation, related work, the layered Android architecture (with a layer-responsibility table and the scan-to-diagnosis data flow), dataset construction, the full model-and-training methodology with the version-by-version evolution and a training-strategies rationale (GRL, effective-number, discriminative-LR equations), the PyTorch→ONNX→TFLite conversion, on-device occlusion-sensitivity XAI, results with a component ablation, implementation, testing/CI, comparative evaluation, the suggestion engine, a model-and-code-availability section (public GitHub + the seven Kaggle notebooks including the failed iterations), an AI-assisted-development disclosure, the v2.6.0.0 release notes, and appendices (38-class list, hyperparameters, release history, requirements matrix, glossary, threat model, 8-phase plan, per-version notebook summaries, mathematical formulation, reproducibility), and the improvement roadmap. |

## Headline result

Shipped classifier (build v3.5.0.0): **86.7% field / 99.3% lab / 96.1% mixed**
top-1 accuracy, on-device, offline, with an occlusion-sensitivity explanation and a
SHA-256 runtime model-integrity guard.

The model is committed in the repository under
`app/src/main/assets/models/plant_disease.tflite` (86.85 MB, FP32).
