# 03 — Engineering & Implementation

## Current state

The build is Gradle 8.9 + AGP 8.7.2 with a version catalog (`gradle/libs.versions.toml`), Kotlin
2.0.21, KSP-processed Hilt + Room. CI (`.github/workflows/android.yml`) runs on every push to
`main`: assembleDebug, assembleRelease (v1+v2+v3 signed), 152 JVM unit tests, lintDebug, an
`apksigner verify` step, and GitHub Release publish — all from the committed model, no Kaggle pull.
Testing is JVM/Robolectric only (no instrumented tests in CI, no emulator step); the Robolectric
migration test substitutes for the Room instrumented gap. R8 minification + resource shrinking are
on; there is no static-analysis gate, no dependency-bot, no baseline profile, and no reproducible
build verification.

## Proposal

**A. Instrumented tests in CI.** Add an `android-test` job on a `macos-latest` runner with the
Android emulator, covering the on-device paths that JVM tests cannot reach: CameraX capture →
non-null `scanId`, the real TFLite `Interpreter` forward pass + occlusion grid on a test image, and
the in-memory Room round-trip on a real device. Gate the release APK publish on this job. This
closes the honest "real on-device instrumented tests" gap noted in the v1.0.0 release.

**B. ktlint + detekt gate + branch protection.** Add `ktlint` (style) and `detekt` (smell/codegen)
as a required CI job, fail on findings, and enforce branch protection on `main` (status checks +
require linear history). This raises the floor on every PR and makes the AI-assisted scaffolding
(which already produces most of the boilerplate) pass a consistent style bar.

**C. Baseline / startup profiles.** Generate a `baseline-prof.txt` via Macrobenchmark covering
cold-start to first-scan-ready and the first TFLite inference. This measurably cuts first-scan
latency (the path users feel most) and is a standard Android optimization the project currently
lacks.

**D. Dependency automation + upgrade cadence.** Enable Dependabot (or Renovate) for the version
catalog, with grouped weekly PRs and CI verification. Track the Compose compiler ↔ Kotlin and
AGP ↔ Gradle compatibility matrix explicitly so upgrades do not silently break the build.

**E. Reproducible builds + sigstore signing.** Make the release APK bit-for-bit reproducible from
a clean checkout (pin deterministic R8/d8 ordering, strip build timestamps) and optionally attach
a sigstore signature alongside the v1+v2+v3 JAR/APK signatures, so a third party can verify the
shipped APK was built from the public source.

**F. APK size budget CI gate + per-PR diff.** Add a step that reports the APK size (total +
excl-model) on every PR and fails if it grows beyond a budget (e.g. excl-model > 25 MB, the
v1.0.0 target). Pair with the per-ABI/App Bundle work in `02_architecture.md` §B.

**G. CI matrix + differential build.** Run the JVM test + assembleDebug matrix across
`minSdk`/`targetSdk` edges (26 and current) and across debug vs release R8, so a config-only
regression is caught before merge, not at release.

## Feasibility & effort

| Sub-proposal | New CI infra? | Effort | Notes |
| --- | :---: | :---: | --- |
| A. Instrumented tests | yes (macos emulator) | Med | emulator boot flakiness must be tamed |
| B. ktlint + detekt | no | Low | existing code may need a one-time cleanup pass |
| C. Baseline profile | no (Macrobenchmark) | Low–Med | needs a benchmarkable module |
| D. Dependabot | no | Low | config-only |
| E. Reproducible + sigstore | no | Med–High | AGP determinism is the hard part |
| F. Size budget gate | no | Low | cheap, high value |
| G. CI matrix | no | Low | may double CI minutes |

## Risks & open questions

The emulator-based instrumented job (A) is the most failure-prone: GitHub Actions Android
emulators are historically flaky, so it needs a retry policy and a generous timeout, and it should
not block debug-build PRs (only release publish). Reproducible builds (E) are aspirational for
AGP 8.x — R8/d8 ordering is not fully deterministic across versions, so the realistic target is
"reproducible within a pinned toolchain," not "reproducible forever." The size budget (F) and
ktlint/detekt (B) are cheap and should land first; they raise quality immediately and make the
larger refactors in `02_architecture.md` safer.
