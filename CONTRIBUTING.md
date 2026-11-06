# Contributing to LeafGuard

LeafGuard is an undergraduate research project and an open-source Android application. External
contributions are welcome — bug fixes, model improvements, documentation, and tests all help. This
document sets the bar for a contribution that can be merged without rework.

## Before you start

- Read [`README.md`](README.md) for the product and [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
  for the layered design (MVVM + Clean Architecture; `ui` / `domain` / `data`).
- Read [`MODEL_CARD.md`](MODEL_CARD.md) and [`DATASHEET.md`](DATASHEET.md) if your change touches
  the model, the labels, or the training data.
- Check [`future-proposals/`](future-proposals/00_README.md) — your idea may already be scoped
  there, and aligning with it keeps the project coherent.

## Development setup

- **JDK 17**, Android Studio Koala/Ladybug or later, Android SDK Platform 35 + Build-Tools 35.x.
- The Gradle wrapper is committed (`./gradlew`), so no local Gradle install is needed.
- The 86.8 MB DINOv2 model is committed in `app/src/main/assets/models/` — a fresh checkout builds
  the real classifier with no Kaggle pull.
- Build: `./gradlew assembleDebug`. Test: `./gradlew test` (152 JVM unit tests). Lint:
  `./gradlew lintDebug`.

## Workflow

1. **Fork** the repository and clone your fork.
2. **Branch** from `main` with a descriptive name:
   - `feat/<scope>-<summary>` (e.g. `feat/scan-low-light-hint`)
   - `fix/<scope>-<summary>` (e.g. `fix/result-confidence-bar-overflow`)
   - `docs/<summary>` / `test/<summary>` / `chore/<summary>`
3. **Make focused commits.** One logical change per commit; small PRs review faster.
4. **Test locally** before pushing — `./gradlew test` and `./gradlew lintDebug` must pass.
5. **Open a pull request** against `main` using the PR template. CI must be green before merge.
6. Squash-merge is the default; the commit message should follow the convention below.

## Commit message conventions

Follow [Conventional Commits](https://www.conventionalcommits.org/), matching the existing
history (`docs:`, `fix(ci):`, `feat(scan):`, …):

```
<type>(<scope>): <imperative summary>

<optional body — what and why, not how>

<optional footer>
```

Types: `feat`, `fix`, `docs`, `test`, `refactor`, `perf`, `chore`, `ci`, `build`. The summary is
imperative ("add", not "added"), lowercase, ≤ 72 chars. The body explains *why* the change is
needed when it is not obvious from the diff.

## Code style

- **Kotlin** per the official Android Kotlin style; the project uses a version catalog
  (`gradle/libs.versions.toml`) and KSP for Hilt + Room.
- Keep `domain` layer pure Kotlin — **no Android framework imports** in `domain/`. Hilt modules
  bind `data/` implementations to `domain/` interfaces.
- Prefer `StateFlow` + `collectAsStateWithLifecycle` in the UI; ViewModels expose state, never
  expose mutable state holders.
- All Room, TFLite, and CameraX work runs on `Dispatchers.IO` (or the dedicated single-thread
  TFLite dispatcher — the `Interpreter` is not thread-safe).
- Match the existing comment density; write a comment only for a constraint the code cannot show.

Static analysis (`ktlint` + `detekt`) is not yet enforced in CI but is the intended standard — see
`future-proposals/03_implementation.md` §B. Following it now makes that transition trivial.

## Model and data changes — special rules

The shipped model is the source of truth for on-device behavior, so changes to it are treated more
strictly than code changes:

- **Do not modify `app/src/main/assets/models/plant_disease.tflite`** without simultaneously
  updating `ModelIntegrity.EXPECTED_SHA256` to the new file's SHA-256, bumping the model version,
  and updating [`MODEL_CARD.md`](MODEL_CARD.md) (size, SHA-256, metrics). A buffer/digest mismatch
  is a runtime integrity failure.
- **Do not alter the 38-class label order** in `app/src/main/assets/models/labels.txt` — the
  model's output index depends on it. Appending new classes requires retraining and a new model
  card version.
- Any change to the training data or conversion pipeline must update [`DATASHEET.md`](DATASHEET.md)
  and be reproducible from `models/conversion/convert.py` (see [`REPRODUCIBILITY.md`](REPRODUCIBILITY.md)).
- Do not strip the `dynamo=False` flag or the baked-in ImageNet-norm / Softmax nodes when
  regenerating the TFLite — the on-device I/O contract depends on them.

## Tests

- Every bug fix or feature should include or update JVM unit tests. The Robolectric migration test
  (`LeafGuardDatabaseMigrationTest`) covers Room schema changes on the JVM; instrumented tests for
  CameraX/TFLite are a tracked gap (`future-proposals/03_implementation.md` §A).
- Pure logic (CSV generation, PDF record-line formatting, label parsing, suggestion engine) must
  be JVM-testable — extract Android-dependent code so the logic is testable without a device.

## Licensing

By contributing, you agree your contributions are licensed under the project's
[MIT License](LICENSE). Do not include code you do not have the right to license under MIT.
