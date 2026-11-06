# Changelog

All notable changes to LeafGuard are documented in this file. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to
[Semantic Versioning](https://semver.org/).

**Rule (enforced via `CONTRIBUTING.md`):** every change to the application, the model, the build,
the CI, or the docs MUST be recorded here with the **date, the time, and the contributor name**.
Entries are grouped under a version heading (`Added` / `Changed` / `Fixed` / `Removed` /
`Security`); the newest version is at the top. `Unreleased` holds changes merged since the last cut.

---

## [Unreleased]

### Added
- `learning/` folder — the consolidated, per-notebook learning log for the seven-iteration training
  arc (field accuracy 11% → 49% → 34% → 67.3% → 86.7%). One file per Kaggle notebook following a
  fixed template (Goal → Strategy & methodology → Why this strategy → Code & config highlights →
  Results & logged outcomes → Mistakes & diagnosed failures → Learnings → Research cross-reference),
  plus a `README.md` index, a `00_research_cross_reference.md` mapping each design decision to its
  source paper (DINOv2, DANN/GRL, effective-number, PlantVillage, PlantDoc, Swin — all with verified
  URLs), and an `08_cross_cutting_learnings.md` meta-learnings doc. Sourced from the IEEE project
  report's per-version appendix, training-strategy rationale, and component ablation.
  — 2026-11-06 23:13 UTC, Kushagra Dubey

---

## [2.7.0.0] — 2026-11-06, 22:30 UTC

**Contributor:** Kushagra Dubey (author). A reliability + export release. The DINOv2 v3.5 model is
**unchanged** (86.7% field / 99.3% lab / 96.1% mixed); all changes are application-layer.
`versionCode` 11 → 12.

### Fixed
- **Per-plant scan timeline on Plant detail (the "Checks").** Tapping a plant in the Plants list now
  shows every scan linked to it, newest-first, with a progress summary (scan count + first/last
  dates) and a tap-to-open row that navigates to the full result. Previously `PlantDetailScreen`
  showed no scans at all, so a plant's checks were invisible from the Plants tab.
  — `PlantDetailScreen.kt`, `PlantDetailViewModel.kt`, `LeafGuardNavHost.kt`.
- **CSV / PDF export now writes to a user-picked location.** Both exports were writing to the
  private app cache dir (`context.cacheDir`) — unreachable to the user and evictable by Android.
  They now launch the system Storage Access Framework (`ActivityResultContracts.CreateDocument`)
  so the user picks a destination (e.g. Downloads), and a completion notification is posted on a
  new `exports` channel. `POST_NOTIFICATIONS` is declared and requested on Android 13+.
  — `RecordsScreen.kt`; `PdfExporter.kt` gains `writeTo(OutputStream)`; `LeafGuardApplication.kt`
  creates the channel; `AndroidManifest.xml`.
- **About screen no longer drifts from the build.** Version + build number now come from
  `BuildConfig.VERSION_NAME` / `VERSION_CODE`, and the SHA-256 prefix from
  `ModelIntegrity.EXPECTED_SHA256`, instead of hardcoded string literals that silently desynced on
  a version bump. — `AboutScreen.kt`.
- **Camera capture scratch file leak.** Each Capture wrote a `leaf_capture_*.jpg` to the cache dir
  and abandoned it after decode; the file is now deleted immediately after the bitmap is staged.
  — `ScanScreen.kt`.
- **Stale comments.** The `MainActivity` splash `setKeepOnScreenCondition { false }` comment no
  longer claims "real auth checks arrive in Phase 2" (auth is wired via the nav graph); the
  `LeafGuardApplication` KDoc no longer claims WorkManager's initializer is disabled (it is not).

### Note on scan-history persistence
Scan-history persistence was already correct in the source: every successful classification calls
`ScanViewModel.analyze` → `scanRepository.saveScan` → `ScanDao.insert` (`OnConflictStrategy.ABORT`
+ an autoGenerate primary key, so scans accumulate and never overwrite) into a real persistent
Room file (`leafguard.db`) with no destructive migration, no `allowMainThreadQueries`, and no
`clearAllTables` call anywhere in `main`. If you previously saw history vanish on app close, you
were running a build older than the Phase-9 "save on classify" reordering (pre-`a46d90a`), where
the scan lived only in the in-memory `ScanResultHolder` (one slot, cleared on process death) —
which is exactly the "only the last scan / lost on close" signature. **Install v2.7.0.0 and
history persists across restarts.** The new per-plant timeline makes those persisted scans visible
from the Plants tab as well as the History tab.

### Changed
- `versionCode` 11 → 12, `versionName` "2.6.0.0" → "2.7.0.0".
- `PlantDetailViewModel` now injects `ScanRepository` to expose the per-plant scan Flow.

### Added
- `CHANGELOG.md` (this file) as the canonical, maintained change log.
- `CONTRIBUTING.md` now mandates a dated, attributed `CHANGELOG.md` entry for every change.
- New strings: export channel name/description, export-done banners, plant-detail "Checks" header
  / progress / empty / scan-row.

---

## [2.6.0.0] — 2026-11-06

**Contributor:** Kushagra Dubey (author). Transparency & reproducibility release. DINOv2 model
unchanged. New in-app **About** screen (version, model SHA-256 prefix, model/code availability,
AI-assisted-development disclosure); seven public Kaggle training notebooks (including the failed
v2.x iterations); `MODEL_CARD.md`, `DATASHEET.md`, `REPRODUCIBILITY.md`, `SECURITY.md`,
`CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, issue/PR templates, branch protection on `main`.
`versionCode` 10 → 11. *(not cut as a GitHub release tag; superseded by v2.7.0.0.)*

---

## [2.5.1.0] — result-screen UX + model transparency

Model-info card (DINOv2 v3.5 · 3-view TTA), model-integrity banner wired from `ModelIntegrity`
(was computed but never shown), qualitative confidence tier (High/Moderate/Low), per-alternative
mini bars, refreshed low-confidence hint. DINOv2 model unchanged.

---

## [2.5.0.0] — DINOv2 ViT-S/14 v3.5.0.0 primary

86.7% field (+19.4 pp over v2.0.0.0) with 3-view TTA. Retired the v2.0.0.0 Swin-ONNX/ORT path
(107 MB, .gitignore'd) and the JK-TK YOLOv11 detector (228 MB, not bundled). CI workflow fixed to
build from the committed model.

---

## [2.0.0.0] — Swin-Tiny ONNX via ONNX Runtime

67.3% field. *(superseded)*

---

## [1.5.0.0] — JK-TK YOLOv11 detector

*(superseded)*

---

[Unreleased]: https://github.com/K-dubey09/Plant-disease-detection-system-XAI/compare/v2.7.0.0...HEAD
[2.7.0.0]: https://github.com/K-dubey09/Plant-disease-detection-system-XAI/releases/tag/v2.7.0.0
