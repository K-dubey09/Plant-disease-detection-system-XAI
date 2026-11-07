# LeafGuard — Architecture

## Layered architecture (MVVM + Clean Architecture)
```
┌──────────────────────────────────────────────────────────────────────┐
│  UI layer  (app/src/main/java/.../ui)                                │
│  Composable screens + ViewModels (Hilt-injected).                    │
│  Observe StateFlow from ViewModels; dispatch user intents.           │
└──────────────────────────────────────────────────────────────────────┘
                │ exposes UI state (StateFlow), accepts intents
┌──────────────────────────────────────────────────────────────────────┐
│  Domain layer  (.../domain)                                          │
│  IMPLEMENTED: repository interfaces (PlantRepository,                │
│  AuthRepository, ScanRepository incl. attachScanToPlant) +           │
│  domain models (DiseaseLabel, Diagnosis, Severity,                   │
│  TreatmentStage, Suggestion, AuthState, AuthOutcome).                │
│  PROPOSED (not yet built): standalone use-case classes               │
│  (ScanLeaf, AttachScanToPlant, GenerateSuggestions) —                │
│  today the scan→diagnose→persist orchestration lives in              │
│  the ViewModels. No Android framework imports.                       │
└──────────────────────────────────────────────────────────────────────┘
                │ calls repository / inference interfaces
┌──────────────────────────────────────────────────────────────────────┐
│  Data layer  (.../data)                                              │
│  Room (entities, DAOs, LeafGuardDatabase), repositories,             │
│  EncryptedSharedPreferences (PinStore), DiseaseClassifier (TFLite),  │
│  SaliencyGenerator. Hilt modules bind these to interfaces.           │
└──────────────────────────────────────────────────────────────────────┘
```


> **Implemented vs proposed.** The diagram shows the *target* layered architecture. The Domain box marks what is built today versus proposed. The use-case classes `ScanLeaf`, `AttachScanToPlant`, and `GenerateSuggestions` are **not yet implemented** as standalone classes — the actual `domain/` tree contains only repository interfaces (`PlantRepository`, `AuthRepository`, `ScanRepository`) and domain models (`DiseaseLabel`, `Diagnosis`, `Severity`, `TreatmentStage`, `Suggestion`, `AuthState`, `AuthOutcome`). `AttachScanToPlant` exists today as a `ScanRepository.attachScanToPlant(scanId, plantId)` method, not a class. The scan→classify→saliency→persist orchestration currently lives inside the ViewModels (for example `ScanViewModel.analyze` calls `DiseaseClassifier.classify` and then `ScanRepository.saveScan` directly). Extracting these into discrete use-case classes is a proposed Clean-Architecture refactor tracked in the improvement roadmap, not a component that ships in the current build.
## Key components (implemented)

| Component | Layer | Phase | Responsibility |
| --- | --- | --- | --- |
| `LeafGuardNavHost` | UI | 1 | Single NavHost; gates auth ↔ main app |
| `AuthViewModel` / `PinStore` | UI / Data | 2 | Register, PIN login, Keystore-backed storage |
| `LeafGuardDatabase` (Plant), `PlantDao`, `PlantRepository` | Data | 3 | Plants CRUD; single source of truth, Flow-based |
| `ScanEntity`, `ScanDao`, `ScanRepository` | Data | 5/6 | Persisted scans; nullable plant FK (ON DELETE SET NULL); v3 adds `sourceImagePath`/`saliencyImagePath` for the gallery; v1→v2→v3 migrations |
| `DiseaseClassifier` | Data | 4 | DINOv2 v3.5 primary classifier: preprocess 224×224, run inference (softmax baked in), 3-view TTA (orig + hflip + scale0.875) on the headline path; single-pass `classifyForSaliency` for the heatmap |
| `SaliencyGenerator` | Data | 4 | Occlusion-sensitivity heatmap (model-agnostic; TFLite exposes no conv gradients) |
| `ScanViewModel` + CameraX (`PreviewView`/`ImageCapture`) | UI | 5/6 | Live capture → classify → saliency → save bitmaps to `filesDir/scans/` → persist `ScanEntity` (with image paths) → navigate `result/{scanId}` |
| `DashboardViewModel` | UI | 6 | Live scan + plant lists → stats (counts, most-recent diagnosis) |
| `HistoryViewModel` | UI | 6 | Join scans ↔ plant name in-memory; per-plant filter → `HistoryItem` list |
| `RecordsViewModel` + `CsvGenerator` / `PdfExporter` | UI | 6 | Group scans by plant → per-plant disease log; pure RFC-4180 CSV (tested) + on-device `PdfDocument` PDF |
| `ImagesViewModel` | UI | 6 | Group image-bearing scans (`sourceImagePath != null`) by plant → Coil `AsyncImage` gallery |
| `AboutScreen` | UI | v2.6.0.0 | Read-only transparency screen: app version (2.6.0.0), shipped model SHA-256 prefix, model/code availability (GitHub + Kaggle), and the AI-assisted-development disclosure. Reached from the Dashboard; static — no data- or domain-layer types, does not touch the inference path. |
| `SuggestionsEngine`, `SuggestionParser`, `Severity`/`TreatmentStage` | Domain | 7 | Map 38 classes → severity tier + staged treatment (Immediate/Short-term/Long-term) + prevention; parser reads the production JSON (org.json), engine exposes `suggest`/`severityFor` |
| `LeafGuardDatabaseMigrationTest`, `PdfExporter.formatRecordLine`/`displayName` | Test | 8 | Robolectric JVM test seeds a real v1 DB → opens at v3 with both migrations, asserting the plant survives + scans gains the image-path columns + a scan with paths round-trips; PdfExporter record-line logic extracted as `internal` pure helpers + JVM-tested (the `PdfDocument` write stays instrumented-only). Tagged v1.0.0 release; APK excl-model 25.0 MB. |


## Proposed architecture (not yet implemented)

The Clean-Architecture diagram above lists three standalone use-case classes in the Domain layer. These are the intended target, not the current source tree:

- `ScanLeaf` — a domain use-case that would own the scan→classify→saliency→persist flow,   encapsulating the multi-step orchestration currently inlined in `ScanViewModel.analyze`.
- `AttachScanToPlant` — a domain use-case that would link a scan to a plant. Today this   exists only as `ScanRepository.attachScanToPlant(scanId, plantId)`; a use-case class would   let the ViewModel call a single domain entry point and keep the repository a pure data   interface.
- `GenerateSuggestions` — a domain use-case that would map a `Diagnosis` to staged   `Suggestion`s. Today the suggestion logic lives in `SuggestionEngine`   (`data/inference`), called directly from the result flow.

The implemented `domain/` package today holds only `domain/model/` (`AuthOutcome`, `AuthState`, `DiseaseModels`) and `domain/repository/` (`AuthRepository`, `PlantRepository`, `ScanRepository`). Adopting the use-case classes is ranked in the improvement roadmap; until then, ViewModels are the orchestration layer and the proposed classes should not be read as shipped components.
## Navigation graph

```
auth/register ──┐
                ├─ (gated by PinStore.hasRegistered / correct PIN) ─► dashboard
auth/login ─────┘

dashboard ─┬─► plants ─► plant/{id} ─► scan?plantId={id} ─► result/{scanId}
           ├─► scan ─► result/{scanId}
           ├─► images
           └─► about   (v2.6.0.0: version, model SHA-256, model/code availability, AI disclosure)

(bottom nav: dashboard | plants | scan | history | records)
history        records        images        result/{scanId}
```

Start destination is `auth/register` until the user registers, then `auth/login`, then the
bottom-nav graph. Gating is implemented in Phase 2 via an auth-state `StateFlow` observed in
`MainActivity`/`LeafGuardNavHost`.

## Threading

- All Room, TFLite, and CameraX work runs on `Dispatchers.IO` (or a dedicated
  `Interpreter` single-thread dispatcher for TFLite, which is not thread-safe).
- ViewModels expose `StateFlow<UiState>`; UI collects with `collectAsStateWithLifecycle`.
- Inference is wrapped in a suspending `withContext(Dispatchers.Default)` call so the UI never
  blocks; the occlusion-sensitivity second pass shares the same dispatcher.

## Memory budget (6 GB target)

- TFLite interpreter footprint: ~100–160 MB resident (the 86.8 MB DINOv2 v3.5 FP32 model + the
  224×224 activation buffers and the occlusion grid's per-cell re-inference). This is why the
  app targets 6 GB+ RAM devices; a smaller float16/INT8 variant is feasible but not built yet.
- CameraX preview: ImageAnalysis with `STRATEGY_KEEP_ONLY_LATEST` and `BACKPRESSURE` — one
  frame in flight, processed on a background thread, dropped if slow.
- Room cursor windows paginated via `LIMIT/OFFSET` in list DAOs.
- Captured scan images stored as compressed JPEG (quality 85) under
  `context.filesDir/scans/`, not held in memory long-term.

## Security

- PIN stored as a Keystore-keyed HMAC (never the raw PIN) inside `EncryptedSharedPreferences`.
- Database is not encrypted in Phase 1–3; file-based encryption (FBE) is on by default on
  API 26+. SQLCipher can be layered in Phase 8 if a stronger threat model is required.
- No network calls: the app is fully offline. `android:allowBackup="false"` for the secure
  prefs file (see backup rules).

## Dependency injection

Hilt generates the graph from `@HiltAndroidApp LeafGuardApplication`. Feature modules install
into `SingletonComponent::class` (repositories, inference engine) or `ViewModelComponent::class`
(ViewModels). KSP processes Hilt + Room in a single pass.
