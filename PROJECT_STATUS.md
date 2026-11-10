# Project status

LeafGuard is a complete research artifact and a working, signed Android release. The model is
trained, validated, converted, and committed; the app is at **v2.7.0.0** with green CI; the seven
training notebooks are public. This document is honest research hygiene: it tells reviewers and any
future contributor exactly what maintenance level to expect, so the project can self-continue
without the original maintainer being a bottleneck.

**Status:** Maintenance / community-stewarded. The maintainer is consciously deprioritizing active
development to focus on other projects; the research paper is the final near-term deliverable for
this repository. The code, model, and documentation are not abandoned — they are complete and left
in a state intended to let the public grow the project if there is demand, without requiring the
original author's continued attention.

## What is stable

- **The shipped app.** v2.7.0.0 (`versionCode` 12) is a signed release — DINOv2 v3.5 classifier
  (86.7% field / 99.3% lab / 96.1% mixed), per-plant scan timeline, CSV/PDF export via the Storage
  Access Framework, About screen with version + model SHA. Build it with `./gradlew assembleRelease`
  or install the [v2.7.0.0 release APK](https://github.com/K-dubey09/Plant-disease-detection-system-XAI/releases/tag/v2.7.0.0).
  See [`README.md`](README.md) and [`CHANGELOG.md`](CHANGELOG.md).
- **The trained model.** The 86.8 MB `plant_disease.tflite` is committed in
  `app/src/main/assets/models/` (under GitHub's 100 MB limit), so every checkout builds the real
  classifier — no Kaggle pull. Its SHA-256 is pinned in `ModelIntegrity.EXPECTED_SHA256`. See
  [`MODEL_CARD.md`](MODEL_CARD.md).
- **The training pipeline.** Seven public Kaggle notebooks reproduce every checkpoint, oldest →
  newest, including the failed v2.x iterations kept public on purpose. Re-running the final
  notebook regenerates the PyTorch checkpoint + ONNX graph; `models/conversion/convert.py` then
  converts ONNX → TFLite. See [`REPRODUCIBILITY.md`](REPRODUCIBILITY.md) and the notebook table in
  [`README.md`](README.md#model-and-code-availability).
- **CI.** `.github/workflows/android.yml` builds, tests, lints, verifies the release APK signature,
  and publishes the signed APK to the GitHub Release on every push to `main`. A green `main` is the
  source of truth.

## What is not actively developed

New features, model retraining, dataset expansion, and localization are not in active work. The
maintainer is not committing to a roadmap schedule, a support channel, or response-time SLAs —
which is why there is no `SUPPORT.md`. The dataset constraint that capped field-data gathering
remains; anyone wanting to push field accuracy past 86.7% will need to bring additional
field-domain data, as documented in [`DATASHEET.md`](DATASHEET.md) and the per-notebook learning
logs in [`learning/`](learning/README.md).

The intended direction for future work — if someone takes it up — is captured in
[`docs/ROADMAP.md`](docs/ROADMAP.md) and the scoped ideas in
[`future-proposals/`](future-proposals/00_README.md). These are proposals, not commitments.

## How the project continues without the original maintainer

The repository is structured so that a motivated contributor can pick it up, extend it, or fork it
without contacting the author. Each of these is already in place:

- **License.** [`LICENSE`](LICENSE) is MIT ("Copyright (c) 2026 LeafGuard contributors"). Anyone may
  fork, modify, and redistribute, including for a derivative product. A fork that supersedes this
  repo is a welcome outcome, not a problem.
- **Contribution governance.** [`CONTRIBUTING.md`](CONTRIBUTING.md) sets the bar for a mergeable
  pull request (JDK 17, fork + branch from `main`, `feat/` or `fix/` prefixes, `./gradlew test` +
  `lintDebug` green). [`CODE_OF_CONDUCT.md`](CODE_OF_CONDUCT.md) sets community norms. Issue and PR
  templates (`.github/ISSUE_TEMPLATE/`, `.github/PULL_REQUEST_TEMPLATE.md`) structure incoming
  work so it does not need authorial triage to be actionable.
- **Reproducibility.** [`REPRODUCIBILITY.md`](REPRODUCIBILITY.md) + the public notebooks + the
  committed model mean the full chain — dataset → preprocessing → training → evaluation →
  conversion → on-device inference — is reconstructable by a third party.
- **Research integrity docs.** [`MODEL_CARD.md`](MODEL_CARD.md), [`DATASHEET.md`](DATASHEET.md),
  and [`docs/AI_ASSISTANCE.md`](docs/AI_ASSISTANCE.md) document the model, the data, and the
  AI-assisted-development disclosure so a successor does not inherit undocumented assumptions.
- **Security.** [`SECURITY.md`](SECURITY.md) defines the threat model (offline app, no network
  calls, no leaf leaves the device) and the private reporting channel (GitHub Private Security
  Advisories). A vulnerability in a fork or a future release should follow the same process.

## For contributors and would-be co-maintainers

If you rely on LeafGuard and want to see it grow, the path is open:

1. **Fix a bug or add a feature** — follow [`CONTRIBUTING.md`](CONTRIBUTING.md) and open a pull
   request. Merges may be slower than an actively-maintained project; if a PR sits, see step 3.
2. **Extend the model or the data** — start from [`REPRODUCIBILITY.md`](REPRODUCIBILITY.md) and
   the final Kaggle notebook; bring your own field data and document it in a new datasheet section.
3. **Volunteer as a co-maintainer** — open an issue titled `role: co-maintainer` describing your
   interest and what you intend to maintain. If the original author is unavailable, a fork with a
   clear note in its README is the expected and acceptable path to continuity; link back to this
   repository as the upstream.

The original author remains responsible for the lines already committed (per the
[`docs/AI_ASSISTANCE.md`](docs/AI_ASSISTANCE.md) disclosure) but does not commit to ongoing
maintenance of new work. The project's success from here is measured by whether the public finds it
useful enough to carry forward — and the governance above is in place precisely so that can happen.
