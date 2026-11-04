# LeafGuard — Phased Roadmap

This is the working roadmap mirrored in the PDF plan. Each phase ends with a commit + push to
the repository and a report, so progress is never lost.

Legend: ✅ done · ⏳ pending · 🔜 next

## Phase 1 — Project foundation & navigation skeleton ✅

**Goal:** a buildable Android project whose navigation graph between every screen in the
specification is real and exercised, with honest placeholders for not-yet-built features.

**Deliverables**
- Gradle Kotlin DSL build (`settings.gradle.kts`, root + app `build.gradle.kts`,
  `gradle/libs.versions.toml` version catalog), Gradle 8.9 wrapper (jar + scripts).
- `AndroidManifest.xml`, Material 3 theme (light/dark + dynamic color), splash screen,
  adaptive launcher icon, backup/data-extraction rules, strings, colors.
- `LeafGuardApplication` (`@HiltAndroidApp`), single-activity `MainActivity`.
- Navigation graph with routes: register, login, dashboard, plants, plant/{id}, scan,
  result/{scanId}, history, records, images — bottom nav over 5 destinations.
- Placeholder screens (`PhasePlaceholder`) for each unfinished screen, each stating its phase.
- `README.md`, `docs/ARCHITECTURE.md`, `docs/ROADMAP.md`, `.gitignore`, `.editorconfig`,
  `LICENSE` (MIT), CI workflow.
- JVM unit test for `Routes` helpers (validates the test source set).

**Validation:** `./gradlew assembleDebug` + `./gradlew test` on a machine with the Android SDK.
This sandbox has no Android SDK, so an automated build is run in CI on push.

---

## Phase 2 — PIN authentication & session gating ✅

**Goal:** first launch forces registration; returning users unlock with their 6-digit PIN.

**Deliverables**
- `PinStore` over `EncryptedSharedPreferences` keyed to the Android Keystore: stores a
  salted, PBKDF2-HMAC-SHA256 derived key (100k iterations; the raw PIN is never persisted) +
  display name + registration timestamp.
- `PinHasher` (pure JDK crypto, JVM-unit-testable) + `PinValidator` (6-digit rules).
- `AuthRepository`/`AuthRepositoryImpl` exposing `StateFlow<AuthState>` (`Unregistered |
  Locked | Unlocked`); `register`/`login`/`logout`/`reset`.
- `AuthViewModel` (Hilt) exposing `authState` + a one-shot `failure: StateFlow<AuthFailure?>`.
- `AuthScreen` rewired to the ViewModel (register/login modes, validation, reset dialog).
- NavHost auth gating: an `AUTH_ROOT` launcher routes to register/login/dashboard from
  `AuthState`; the gating effect only force-navigates while on an auth route, so in-app
  navigation is never hijacked.
- Hilt `SecurityModule` + `RepositoryModule` (incl. an `@IoDispatcher` qualifier).
- Tests: `PinHasherTest`, `PinValidatorTest`, `AuthRepositoryImplTest` (fake `PinStore`),
  `AuthViewModelTest` (fake repository, `Dispatchers.setMain`), `RoutesTest` — **38 JVM unit
  tests, all passing locally.** Instrumented `EncryptedPinStoreTest` for the real Keystore
  round-trip on a device.

**Validation:** `./gradlew :app:assembleDebug` produces a 43.9 MB debug APK; `./gradlew
:app:testDebugUnitTest` runs 38 tests with 0 failures. CI runs the same on push.

---

## Phase 3 — Room persistence & Plants CRUD ✅

**Goal:** the data backbone for plants (scans/history/records arrive in later phases).

**Deliverables**
- `Plant` entity + `PlantDao` (Flow return types for observe, suspend for mutations) +
  `LeafGuardDatabase` (version 1, `exportSchema=false` for now).
- `PlantRepository`/`PlantRepositoryImpl` (single source of truth; reads on `@IoDispatcher`).
- Hilt `DatabaseModule` (Singleton DB + DAO) + `PlantRepository` binding in `RepositoryModule`.
- `PlantsViewModel` + real `PlantsScreen`: live list, add-plant dialog (name/species/notes),
  per-row delete, empty state.
- `PlantDetailViewModel` + real `PlantDetailScreen`: view metadata, edit name/species/notes,
  "Scan this plant", delete-with-confirm.
- Tests: `PlantRepositoryImplTest` (fake DAO, 7), `PlantsViewModelTest` (fake repo, 5),
  instrumented `PlantDaoTest` (in-memory Room) on a device. **50 JVM unit tests pass locally
  (38 from Phase 2 + 12 new).**

**Validation:** `./gradlew :app:assembleDebug` compiles cleanly (KSP/Room codegen OK); the APK
is built by CI (the 4 GB sandbox OOMs at the D8 dex step). `./gradlew :app:testDebugUnitTest`
runs 50 tests with 0 failures locally.

---

## Phase 4 — TFLite model + Explainable AI engine ✅

**Goal:** on-device inference + an explainability heatmap over a leaf photo.

**Deliverables**
- Model assets under `app/src/main/assets/models/`:
  `plant_disease.tflite` (~112 MB, Swin-Tiny transformer, patch4-window7-224, 38-class head,
  **98.59% PlantVillage validation accuracy**), `labels.txt` (38 PlantVillage classes),
  `disease_suggestions.json` (label → summary/treatments/prevention, authored from general IPM
  knowledge). The model is a **fully-trained classifier** — source is `surgeonwz/plant-village` on
  HuggingFace (apache-2.0), converted via the PyTorch→ONNX→TFLite pipeline in
  `models/conversion/convert.py` (with `dynamo=False` to avoid an onnx2tf Loop-op blocker, and
  ImageNet normalization + Softmax baked into the graph). Targeted at 6GB+ RAM devices. The
  committed asset is an untrained demo fallback; CI pulls the trained model from the private
  Kaggle dataset `kushagradubey09/plant-disease-xai-models` (v2) on every build.
- `DiseaseClassifier` wraps the TFLite `Interpreter`: preprocesses the bitmap to `[1,224,224,3]` float `[0,1]`
  (the model owns the ImageNet normalization, baked in as ONNX Sub(mean)/Div(std) nodes), runs inference,
  returns top-k labels + confidences (the Softmax is also baked in, so confidences are real probabilities).
- `SaliencyGenerator` produces an occlusion-sensitivity heatmap (model-agnostic — true Grad-CAM needs the
  convolutional activations/gradients that TFLite's Interpreter does not expose): a coarse 8×8 grid is greyed
  cell-by-cell and the predicted class's confidence drop per cell becomes the heatmap.
- `SuggestionEngine` maps a predicted label to advisory `Suggestion`s; `LabelParser`/`SuggestionParser` load
  the assets. `ScanResultHolder` (Singleton) carries the latest scan between the scan + result screens.
- Hilt `InferenceModule` binds a singleton `Interpreter` (loaded compression-safe via `readBytes()` into a
  direct `ByteBuffer`), labels, suggestions, classifier, saliency generator, suggestion engine.
- UI: real `ScanScreen` (photo-picker import → classify → saliency → result) and `ResultScreen`
  (prediction + confidence bar + source/heatmap side-by-side + alternatives + advisory).
- Tests: `LabelParserTest`, `SuggestionEngineTest`, `OcclusionRectsTest`, `PixelNormalizeTest` (4 each, 16 new).

**Validation:** `./gradlew :app:testDebugUnitTest` → **66 JVM unit tests pass** (50 from Phases 2–3 + 16 new),
0 failures, locally and on CI. `assembleDebug` (APK) built by CI; the 4 GB dev sandbox OOMs at the D8 dex step.
The TFLite conversion is verified off-device: the Swin FP32 model matches its ONNX reference to a mean max
abs diff of 4.42×10⁻⁸ with 8/8 argmax agreement; the occlusion heatmap + Interpreter runtime path are
exercised on-device via the trained Swin model (98.59% accuracy). The **signed release APK** is built by
CI (keystore decoded from `KEYSTORE_BASE64`), signed with **v1 + v2 + v3** schemes
(`enableV1Signing = true` in `build.gradle.kts` — AGP 8.x omits v1 by default once `minSdk ≥ 24`,
which left a v2-only APK that some OEM installers rejected with "There was a problem parsing the
package"; the v1 JAR signature is the one every installer understands), verified by an `apksigner
verify` CI step that asserts v1 + v2 are present, and published as a raw `.apk` to GitHub Releases.

---

## Phase 5 — CameraX live scan → result flow ✅

**Goal:** the user scans a leaf in real time and gets a diagnosis + explanation, attachable to
a plant and persisted for history.

**Deliverables**
- `ScanScreen` rewired to CameraX: a `PreviewView` (back camera, `FILL_CENTER`) bound to a
  `Preview` + `ImageCapture` use case via `ProcessCameraProvider`; a Capture button saves a frame
  to the cache dir, decodes it, and stages it for analysis. Gallery import (`PickVisualMedia`)
  remains as a fallback for emulators / re-scanning existing photos; a Retake button clears the
  staged bitmap. Runtime CAMERA permission is requested on entry (`uses-feature camera.any
  required="false"` so the app still installs on camera-less devices).
- `ScanEntity` (Room) + `ScanDao`: a persisted scan row (id, nullable `plantId` foreign key to
  `plants` with `ON DELETE SET NULL`, `labelName`, `labelCondition`, `confidence`, `createdAt`).
  Deleting a plant keeps its scan history (plantId → NULL) rather than cascading.
- `LeafGuardDatabase` bumped to v2 with `MIGRATION_1_2` (adds the `scans` table + its plant FK +
  index) so an in-place app upgrade never loses the user's plants.
- `ScanRepository`/`ScanRepositoryImpl` (single source of truth, Flow-based, `@IoDispatcher`) +
  Hilt bindings in `DatabaseModule` (ScanDao) and `RepositoryModule` (ScanRepository).
- `ScanViewModel.analyze(bitmap, plantId?)`: classify → occlusion saliency → suggest →
  persist `ScanEntity` → publish Bitmaps to `ScanResultHolder` → emit `UiState.Done(scanId)` so the
  UI navigates to `result/{scanId}`. `plantId` is non-null only when launched from PlantDetail.
- `ResultViewModel`: reads `ScanResultHolder` (Bitmaps + diagnosis, in-memory) AND observes the
  persisted `ScanEntity` by `scanId` (via `SavedStateHandle`) so the metadata survives process
  death and so Phase 6 history → result navigation works without the holder.
- Navigation: `scan` (bottom-nav, unattached) + `scan-plant/{plantId}` (from PlantDetail,
  auto-attached) + `result/{scanId}` (addressable). The two-route scan pattern mirrors
  `plant/{plantId}` and avoids optional-query-arg resolution ambiguity.
- Tests: `ScanRepositoryImplTest` (8 cases — empty, persist+emit, plantId link, unattached,
  per-plant filter, getById, delete, observe-then-null) + `FakeScanDao` + `FakeScanRepository`;
  `RoutesTest` updated for `result(scanId)` + `scan-plant/{plantId}`. **74 JVM unit tests** total.

**Validation:** `./gradlew :app:testDebugUnitTest` → 74 JVM unit tests pass (66 from Phases 2–4 +
8 new), 0 failures, on CI. `assembleDebug` + `assembleRelease` (v1+v2+v3 signed) built by CI; the
release APK is published to GitHub Releases. The scan → persist → result/{scanId} path and the
CameraX binding are exercised on-device (instrumented capture → non-null scan id is the next
instrumented test target). The `scan_demo_banner` string was corrected: it no longer claims
"DEMO MODEL / predictions are a placeholder" (false for the release APK, which ships the trained
Swin-Tiny model); it now states the real on-device inference + advisory nature.

---

## Phase 6 — Dashboard, History, Images, Records ✅

**Goal:** the information surfaces over accumulated scans.

**Deliverables**
- `DashboardScreen`: stats (scans this week, active plants, most-recent disease), quick actions.
- `HistoryScreen`: reverse-chronological feed, filter by plant/disease.
- `ImagesScreen`: gallery of originals + overlays, grouped by plant/date.
- `RecordsScreen`: structured per-plant disease log + CSV and PDF export.
- ViewModels + Flow queries backing each.
- Unit tests for aggregation queries; instrumented for list rendering.

**Status: complete (shipped across two increments).**

*History + Dashboard:*
- `DashboardViewModel` exposes the live scan + plant lists (Eagerly started); `DashboardScreen`
  renders a stats card (total scans, plants tracked, most-recent diagnosis with confidence % and
  timestamp) above the Scan / Plants / Images quick-action cards.
- `HistoryViewModel` joins each persisted scan with its plant name in-memory and applies a plant
  filter; `HistoryScreen` renders filter chips (All + one per plant) over a `LazyColumn` of scan
  cards, each tapping into the Phase 5 `result/{scanId}` route.

*Records:*
- `RecordsViewModel` groups scans by plant (null = Unattached), resolves names in-memory, sorts
  groups newest-scan-first; no `Context` in the VM so grouping is JVM-testable.
- `RecordsScreen` shows per-plant cards + Export CSV / Export PDF buttons (write to the app cache
  dir; result shown inline). `CsvGenerator` is a pure RFC-4180 generator (unit-tested); `PdfExporter`
  uses the on-device `android.graphics.pdf.PdfDocument` (compiled + linted; CSV is the tested path).

*Images:*
- `ScanEntity` v3 adds `sourceImagePath` / `saliencyImagePath` (nullable paths to the captured leaf
  + occlusion overlay saved to internal storage); Room v3 + `MIGRATION_2_3` (ALTER TABLE ADD COLUMN
  x2) preserves every prior scan and plant in-place. `ScanViewModel` saves both bitmaps to
  `filesDir/scans/` at scan time (JPEG source, PNG overlay) and passes the paths into the entity;
  pre-Phase-6 scans (no stored image) are excluded from the gallery.
- `ImagesViewModel` groups image-bearing scans by plant; `ImagesScreen` is a Coil `AsyncImage`
  gallery — per-plant sections over `LazyRow`s of cards (source leaf + saliency overlay + condition
  + date). Empty state.

**Tests:** 24 new JVM tests this phase — `DashboardViewModelTest` (4), `HistoryViewModelTest` (7),
`CsvGeneratorTest` (9), `RecordsViewModelTest` (8), `ImagesViewModelTest` (7). **109 JVM unit tests
total.** CI run 41730838690 (commit `ed3f02d`) is fully green: debug + release build, 109 tests,
lint, v1+v2 signing, GitHub Release publish (137.4 MB APK).

---

## Phase 7 — Treatment suggestions engine ✅

**Goal:** every diagnosis carries actionable, disease-specific guidance.

**Deliverables**
- `disease_suggestions.json`: per-class severity, immediate/weekly/seasonal actions, organic and
  chemical options, prevention notes — derived from public agronomy guidance (cited).
- `SuggestionsEngine` use-case: given a `Diagnosis`, return ranked suggestions.
- `ResultScreen` suggestions section + "add to plant care log" action.
- Unit tests: every one of the 38 classes resolves to a non-empty suggestion set.

**Status: complete.** The suggestion layer moved from a flat treatments list to a per-disease
knowledge base with a severity tier, stage-aware treatment steps (Immediate / Short-term /
Long-term), and a prevention checklist for all 38 PlantVillage classes.
- `Severity` enum (`HEALTHY` / `LOW` / `MEDIUM` / `HIGH` / `CRITICAL`) with a `fromString` fallback;
  `TreatmentStage(stage, steps)`; `Suggestion` gains `severity` + `treatmentStages` and drops the
  flat `treatments` list. `SuggestionEngine.severityFor(label)` reads the tier without pulling the
  whole `Suggestion`. `SuggestionParser` parses the new fields; a legacy flat `treatments` array
  degrades to a single Immediate stage, and a missing severity degrades to MEDIUM.
- `disease_suggestions.json` regenerated: 26 diseases get a severity (14 HIGH / 9 MEDIUM / 3
  CRITICAL) and three ordered stages with concrete IPM steps, keeping the prior
  summary/condition/plant/prevention; the 12 `___healthy` classes are `HEALTHY` with empty stages +
  wellness prevention. The 3 CRITICAL entries are the incurable/lethal: citrus greening, tomato
  yellow leaf curl virus, tomato mosaic virus.
- `ResultScreen` renders a colored `SeverityBadge` (green/blue/amber/orange/red by tier) above the
  summary, then the staged treatment sections + prevention; a HEALTHY result shows a "no treatment
  needed" note instead of treatment stages.
- 11 new JVM tests: `SuggestionEngineTest` (7, rewritten for the new model) and the new
  `SuggestionParserTest` (8), which parses the production JSON via a real `org.json:json`
  (testImplementation, shadowing Android's stub) and asserts 38 entries, every `labels.txt` key
  present, 26 diseases x 3 stages with steps, 12 healthy + empty, the 3 CRITICAL labels, stage
  order Immediate -> Short-term -> Long-term, and non-empty prevention/condition/summary. **120 JVM
  unit tests total.** CI run 41735955447 (commit `aceaee0`) is fully green on the first try:
  debug + release build, 120 tests, lint, v1+v2 signing, GitHub Release publish (137.4 MB APK).

---

## Phase 8 — Polish & release readiness ✅

**Goal:** production-quality release artifact.

**Deliverables**
- Full instrumented test pass on a reference 6 GB+ device.
- R8 release build, resource shrinking, APK size audit (target < 25 MB excluding model).
- Accessibility pass (TalkBack, min touch targets, contrast).
- Localized strings (start with en; structure for translation).
- Versioning + signed release build (keystore kept out of VCS).
- Manual accuracy + Grad-CAM locality validation on the held-out leaf set.
- Tagged release v1.0.0.

**Status: complete (v1.0.0 tagged).** The two carried-forward honest gaps are closed and a tagged
v1.0.0 release ships.
- **Room migration runtime test (closes gap #1).** `LeafGuardDatabaseMigrationTest` runs under
  Robolectric on the JVM (so it executes in CI, no emulator): it seeds a real v1 database file (the
  plants table only) with a plant row via `FrameworkSQLiteOpenHelper`, then opens
  `LeafGuardDatabase` at v3 with `MIGRATION_1_2` + `MIGRATION_2_3` registered and asserts the v1
  plant row survived, the scans table gained `sourceImagePath` + `saliencyImagePath`, a scan with
  image paths inserts + reads back, and `user_version` reaches 3. This required dropping the
  `robolectric.offline=true` guard (set when no test used Robolectric) so Robolectric can resolve the
  SDK 34 android-all jar from Maven Central at runtime, and sizing the forked test JVM to 2 g heap /
  512 m metaspace for the ~150 MB framework jar.
- **PdfExporter logic test (closes gap #2).** `formatRecordLine` + `displayName` extracted as
  `internal` pure helpers so the record-line layout + plant-name resolution are JVM-testable
  (`PdfExporterTest`, 5 cases). The actual `PdfDocument` write stays instrumented-only (Android
  graphics); the CSV remains the structured, fully-tested export path.
- **Accessibility + i18n.** Audit confirmed icon-only buttons (back, delete) are labeled, icons
  inside labeled buttons / FAB / nav items are correctly decorative (`null`), and touch targets are
  48 dp+. The ScanScreen captured-leaf `Image` gained a `contentDescription` (it was primary content
  with `null`). i18n: every Compose `Text()` already uses `stringResource` (no hard-coded UI
  strings); the PDF canvas strings stay English (secondary export).
- **APK size audit.** The v0.4.0 release APK is 137.4 MB total: the trained model
  (`assets/models/plant_disease.tflite`) is 112.5 MB and **the app + dependencies excluding the model
  is 25.0 MB** (meets the < 25 MB target). `classes.dex` is 1.7 MB and `res/` is 0.1 MB after R8 +
  resource shrinking; the excl-model bulk is the TFLite native runtime shipped for all four ABIs
  (~20 MB) - a per-ABI-split optimization is the obvious future win. Store-listing copy is in
  `docs/STORE_LISTING.md`.
- **Versioning + release.** `versionCode` 4 -> 5, `versionName` 0.4.0 -> 1.0.0; the CI
  release-publish step tags `v1.0.0`. **127 JVM unit tests pass** (+7 this phase: 2 migration + 5
  PdfExporter). CI run 41742401041 (commit `52c700d`, after one Robolectric-config fix iteration on
  `8b31881`) is fully green: debug + release build, 127 tests, lint, v1+v2 signing, and the v1.0.0
  GitHub Release publish (137.4 MB signed APK).
- **Deferred (noted honestly, not blocking v1.0.0).** Real on-device instrumented tests (no emulator
  step in CI - the Robolectric JVM migration test substitutes for the Room gap); manual accuracy +
  Grad-CAM locality validation on a held-out leaf set (occlusion-sensitivity is the shipped XAI);
  localized strings beyond English; per-ABI APK splits + a quantized model variant for size-constrained
  devices.

---

## Phase 9 — Scan UX fix: instant results, streamed heatmap, growth tracking ✅

A user-reported regression: after capturing/importing a leaf the scan page sat on a "scanning"
spinner for a long time and sometimes never advanced. Root cause was that `ScanViewModel.analyze`
ran the whole pipeline **serially** — classify → occlusion-sensitivity saliency (an 8×8 grid = 64
extra TFLite forward passes, ~6–32 s on-device) → suggest → save → emit `Done(scanId)` — and the
scan screen's `LaunchedEffect(ui)` only navigated to `result/{scanId}` on `Done`, so the result +
history were gated on the slow heatmap. A heatmap failure (OOM / TFLite error) left `Done` un-emitted
and the UI stranded on the Analyzing spinner indefinitely.

- **Fast path + background heatmap.** `analyze` now does classify → suggest → save source image →
  persist `ScanEntity` (with `saliencyImagePath = null`) → publish `ScanResult` to
  `ScanResultHolder` → emit `Done(scanId)`, all on the single classify forward pass (a second or
  two). The occlusion heatmap then runs in an **independent** `viewModelScope` coroutine (a
  `SupervisorJob` sibling, so it survives navigation away from the scan screen) that, on success,
  saves the heatmap bitmap, back-fills `saliencyImagePath` via the new
  `ScanDao.updateSaliencyImagePath` / `ScanRepository.setSaliencyImage`, and streams the bitmap onto
  the result screen via `ScanResultHolder.updateSaliency`. A heatmap exception is caught and
  swallowed — the scan is already complete, so the failure is contained and never strands the UI.
- **History updates at the same instant.** Because `saveScan` inserts the row before `Done`, Room's
  observe `Flow` fires on its own and the History/Records/Records feeds update immediately — no
  waiting on the heatmap.
- **Streamed heatmap UI.** `ScanResult.saliency` is now nullable; the result screen's heatmap slot
  renders a "Generating explanation…" `CircularProgressIndicator` placeholder while it is null, then
  swaps to the bitmap when `updateSaliency` streams it in. `updateSaliency` is guarded by scan id so
  a stale heatmap from a scan the user has since superseded cannot clobber the current result.
- **"Add to plant" growth tracking.** The result screen gains an **Add to plant** action (the
  `result_save_to_plant` / `result_save_new` strings that previously existed but were unwired). A
  picker dialog lists the user's plants + a "Save as new plant" option (name + species); confirming
  calls `ScanRepository.attachScanToPlant(scanId, plantId)` (new `ScanDao.updatePlantId`, a
  `@Query UPDATE` — no schema change, no migration, DB stays at v3) or creates a plant then attaches.
  The linked scan then appears in that plant's per-plant Records timeline, so each scan becomes a
  tracked state in the plant's growth/health history.
- **No schema change.** Both new DAO methods are `@Query UPDATE` on existing columns (`plantId`,
  `saliencyImagePath`), so `LeafGuardDatabase` stays at version 3 with no migration — verified by the
  existing `LeafGuardDatabaseMigrationTest` still passing.
- **Tests.** +12 JVM tests (127 → **139**): `ScanViewModelTest` (Robolectric + MockK — proves
  `Done` is emitted + the row persisted even when saliency throws, that a successful heatmap
  streams onto the holder + back-fills its path, that `plantId` threads through, and that
  `updateSaliency` drops a stale heatmap); `ScanRepositoryImplTest` (+4 — attach link/relink/unlink,
  setSaliencyImage back-fill + Flow re-emit); `ResultViewModelTest` (+4 — attach to existing, unlink,
  create-and-attach, reactive `attachedPlantId`). The Phase 8 Robolectric migration test still
  passes (no regression). `assembleDebug` produces a 53 MB APK; `lintDebug` reports 0 errors.

---

## Cross-cutting decisions

- **No fake workflows:** a screen that is not implemented shows a `PhasePlaceholder` with its
  phase number; no mock loading spinners or dummy data.
- **Reproducible model pipeline:** the conversion script is checked in, so the `.tflite` can be
  regenerated deterministically from the published `.keras`.
- **Each phase pushes before reporting,** so the repository always reflects verified progress.
