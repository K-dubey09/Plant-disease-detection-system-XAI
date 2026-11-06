# LeafGuard — IEEE-Format Deliverables

This directory contains the two IEEE-format PDF deliverables for the LeafGuard
plant-disease-detection system, regenerated from the latest research notebook
sequence (v1 → v3.5.0.0) and the current Android codebase.

| File | Description |
| --- | --- |
| `LeafGuard_IEEE_Paper_Updated.pdf` | Updated IEEE-format research paper (~7 pp, two-column conference format). Reports the DINOv2 + DANN classifier (86.7% field accuracy) and the documented seven-iteration evolution from the 11% PlantVillage-overfit Swin baseline. 20 references including recent (2024–2026) related work. |
| `LeafGuard_IEEE_Project_Report.pdf` | IEEE-format project report (~32 pp, two-column). Covers motivation, related work, layered Android architecture, dataset, the full model-and-training methodology with the version-by-version evolution, the PyTorch→ONNX→TFLite conversion, on-device occlusion-sensitivity XAI, results, implementation, testing/CI, ablation, comparative evaluation, the suggestion engine, appendices (38-class list, hyperparameters, release history, requirements matrix, glossary, threat model, 8-phase plan, per-version notebook summaries, mathematical formulation, reproducibility), and the improvement roadmap. |

## Headline result

Shipped classifier (build v3.5.0.0): **86.7% field / 99.3% lab / 96.1% mixed**
top-1 accuracy, on-device, offline, with an occlusion-sensitivity explanation and a
SHA-256 runtime model-integrity guard.

The model is committed in the repository under
`app/src/main/assets/models/plant_disease.tflite` (86.85 MB, FP32).
