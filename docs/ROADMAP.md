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

## Phase 2 — PIN authentication & session gating 🔜

**Goal:** first launch forces registration; returning users unlock with their 6-digit PIN.

**Deliverables**
- `PinStore` over `EncryptedSharedPreferences` keyed to the Android Keystore: stores a
  salted, Keystore-HMAC'd PIN digest (never the raw PIN) + display name + registration timestamp.
- `AuthRepository`, `AuthViewModel` exposing `AuthState` (`Unregistered | Locked | Unlocked`).
- `AuthScreen` wired to the ViewModel: validation (6-digit, confirm-match, wrong-PIN).
- Start-destination gating in `LeafGuardNavHost`: register → login → main graph.
- Optional biometric (BiometricPrompt) unlock path behind a setting.
- Unit tests: PIN validation rules, `PinStore` HMAC round-trip with Robolectric.

---

## Phase 3 — Room persistence & Plants CRUD ⏳

**Goal:** the data backbone for plants, scans, history, and records.

**Deliverables**
- Entities: `PlantEntity`, `ScanEntity`, `RecordEntity`, image references.
- `LeafGuardDatabase` (versioned migrations from day one), DAOs with Flow return types.
- `PlantRepository`, `ScanRepository` (single source of truth).
- Hilt `DatabaseModule` + `RepositoryModule`.
- `PlantsScreen` + `PlantsViewModel`: list, add/edit plant (name, species, notes, photo).
- `PlantDetailScreen`: scan timeline (reverse-chronological), notes, metadata, "scan this plant".
- Instrumented tests: DAO queries against in-memory Room.

---

## Phase 4 — TFLite model + Explainable AI engine ⏳

**Goal:** on-device inference and Grad-CAM explanation, verified on real leaf inputs.

**Deliverables**
- `scripts/convert_model.py`: downloads the verified MobileNetV2 `.keras`
  (`huggingface.co/rarfileexe/Plant-Disease-Detector`, 24.0 MB), converts to float16 + INT8
  `.tflite`, attaches metadata, writes `labels.txt` (38 classes) and
  `disease_suggestions.json`.
- Model assets placed under `app/src/main/assets/models/` (≈12 MB float16, well under 50 MB).
- `TFLiteInferenceEngine`: load from assets (GPU delegate where safe), preprocess 224×224,
  run softmax, return top-k with confidence.
- `GradCamExplainer`: gradient-enabled second pass over `Conv_1` activations → 7×7 heatmap →
  224×224 jet-colormap overlay.
- `InferenceModule` (Hilt) binding a singleton `Interpreter`.
- Unit tests: label mapping, preprocessing tensor stats, Grad-CAM weight math against a fixture.
- Offline accuracy spot-check on a small held-out leaf set.

---

## Phase 5 — CameraX live scan → result flow ⏳

**Goal:** the user scans a leaf in real time and gets a diagnosis + explanation, attachable to
a plant.

**Deliverables**
- `ScanScreen` with CameraX `PreviewView` + `ImageAnalysis` (`KEEP_ONLY_LATEST`), capture button,
  import-from-gallery fallback.
- `ScanViewModel`: on capture, run `TFLiteInferenceEngine` + `GradCamExplainer`, persist a
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
