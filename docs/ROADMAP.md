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

## Phase 5 — CameraX live scan → result flow ⏳

**Goal:** the user scans a leaf in real time and gets a diagnosis + explanation, attachable to
a plant.

**Deliverables**
- `ScanScreen` with CameraX `PreviewView` + `ImageAnalysis` (`KEEP_ONLY_LATEST`), capture button,
  import-from-gallery fallback.
- `ScanViewModel`: on capture, run `DiseaseClassifier` + `SaliencyGenerator`, persist a
  `ScanEntity` (image + overlay + diagnosis + timestamp), expose result id.
- `ResultScreen`: top-1 class + confidence, Grad-CAM overlay image, "save to plant" /
  "save as new plant" actions; if `scan?plantId=` was used, auto-attach.
- Runtime permission flow (camera, media images) via Accompanist.
- Instrumented: scan → result navigation; capture produces a non-null scan id.

---

## Phase 6 — Dashboard, History, Images, Records ⏳

**Goal:** the information surfaces over accumulated scans.

**Deliverables**
- `DashboardScreen`: stats (scans this week, active plants, most-recent disease), quick actions.
- `HistoryScreen`: reverse-chronological feed, filter by plant/disease.
- `ImagesScreen`: gallery of originals + overlays, grouped by plant/date.
- `RecordsScreen`: structured per-plant disease log + CSV and PDF export.
- ViewModels + Flow queries backing each.
- Unit tests for aggregation queries; instrumented for list rendering.

---

## Phase 7 — Treatment suggestions engine ⏳

**Goal:** every diagnosis carries actionable, disease-specific guidance.

**Deliverables**
- `disease_suggestions.json`: per-class severity, immediate/weekly/seasonal actions, organic and
  chemical options, prevention notes — derived from public agronomy guidance (cited).
- `SuggestionsEngine` use-case: given a `Diagnosis`, return ranked suggestions.
- `ResultScreen` suggestions section + "add to plant care log" action.
- Unit tests: every one of the 38 classes resolves to a non-empty suggestion set.

---

## Phase 8 — Polish & release readiness ⏳

**Goal:** production-quality release artifact.

**Deliverables**
- Full instrumented test pass on a reference 6 GB+ device.
- R8 release build, resource shrinking, APK size audit (target < 25 MB excluding model).
- Accessibility pass (TalkBack, min touch targets, contrast).
- Localized strings (start with en; structure for translation).
- Versioning + signed release build (keystore kept out of VCS).
- Manual accuracy + Grad-CAM locality validation on the held-out leaf set.
- Tagged release v1.0.0.

---

## Cross-cutting decisions

- **No fake workflows:** a screen that is not implemented shows a `PhasePlaceholder` with its
  phase number; no mock loading spinners or dummy data.
- **Reproducible model pipeline:** the conversion script is checked in, so the `.tflite` can be
  regenerated deterministically from the published `.keras`.
- **Each phase pushes before reporting,** so the repository always reflects verified progress.
