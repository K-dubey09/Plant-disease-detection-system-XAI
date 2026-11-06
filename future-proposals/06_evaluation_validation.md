# 06 — Evaluation & Validation

## Current state

The model is evaluated on three held-out splits — **field 86.7% (525), lab 99.3% (1,520), mixed
96.1% (2,045)** — with 3-view TTA matching the on-device inference path, plus per-class
precision/recall on the mixed split (in `metrics_swin_v35.json`). The accuracy arc across versions
(11% → 49% → 34% → 67.3% → 86.7%) is documented in the public Kaggle notebooks. What is **not**
done: independent expert validation of the field ground truth, a prospective real-world trial,
robustness/calibration testing, and statistical-significance testing between versions. The
confidence tiers (High / Moderate / Low) are qualitative, not calibration-tuned.

## Proposal

**A. Prospective field trial — the keystone.** Hand the app to a cohort of farmers/students for
N weeks, log prediction-vs-actual-outcome (with explicit consent and on-device storage), and
measure real-world utility — first-scan correctness, false-alarm rate, and whether the treatment
suggestion led to a better outcome. This measures the thing that actually matters (does the app
help a farmer) rather than held-out accuracy alone. Pair with `04_dataset.md` §E for a closed
feedback loop.

**B. Independent expert accuracy audit.** A plant pathologist re-labels the 525-image field set
**and** a fresh, never-seen-by-the-author set, then the model is scored against the expert labels.
This is the external validity check the project currently lacks (and the MODEL_CARD's #1 caveat).

**C. Robustness suite.** A perturbation stress test: per-image transforms (lighting shift, Gaussian
blur, rotation, scale, background clutter, partial-leaf occlusion) and adversarial-ish inputs
(FGSM-style at small ε) to measure how accuracy degrades and where the model is brittle. Publish
the robustness curves alongside the clean accuracy.

**D. Calibration + confidence-tier tuning.** Measure the Expected Calibration Error (ECE) and a
reliability diagram on the field split. If the softmax is over/under-confident, apply
temperature scaling and re-tune the High/Moderate/Low tier thresholds against calibrated
probabilities, so a "High confidence" result corresponds to a measured accuracy band.

**E. Per-class / per-crop confusion-matrix deep-dive.** Publish the full 38×38 confusion matrix as
an appendix, with per-crop roll-ups, so the classes that confuse each other (e.g., early-blight vs.
Septoria leaf spot on tomato) are visible and addressable in the next training iteration.

**F. Statistical significance between versions.** The 67.3% → 86.7% jump is large, but the smaller
v2.x deltas (11 → 49 → 34) overlap. Report Wilson score intervals on every split accuracy and run
McNemar's test between consecutive versions, so "improvement" is a statistical claim, not a
point-estimate assertion.

**G. Privacy-preserving aggregate telemetry (opt-in).** With consent, collect only aggregate,
non-image signals — low-confidence rate by crop, scan-to-outcome delay distribution, crash
signatures — to guide retraining priorities. No images, no identifiers, off by default.

## Feasibility & effort

| Sub-proposal | Fieldwork? | New infra? | Effort | Impact |
| --- | :---: | :---: | :---: | :---: |
| A. Prospective trial | yes | consent + logging | High | High |
| B. Expert audit | yes (expert) | no | Low–Med | High |
| C. Robustness suite | no | no | Low–Med | Med |
| D. Calibration tuning | no | no | Low | Med |
| E. Confusion-matrix deep-dive | no | no | Low | Med |
| F. Significance testing | no | no | Low | Med |
| G. Aggregate telemetry | no | upload infra | Med | Med |

## Risks & open questions

The prospective trial (A) is the highest-effort, highest-impact item and the one most likely to
surface a real-world accuracy that is **lower** than 86.7% — field lighting, leaf angles, and the
"photograph a single leaf" instruction are harder to control outside a held-out set. The result
must be reported honestly even if it is humbling; it is the difference between an app that scored
well on a benchmark and one that works for farmers. Expert audit (B) and the robustness suite (C)
are the cheap, do-now items: they sharpen every number already in the MODEL_CARD without new
fieldwork. Calibration (D) is especially important before the confidence tiers are trusted for any
treatment-suggestion escalation.
