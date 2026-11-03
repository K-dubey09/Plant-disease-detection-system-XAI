# LeafGuard — Architecture

## Layered architecture (MVVM + Clean Architecture)

```
┌──────────────────────────────────────────────────────────────────┐
│  UI layer  (app/src/main/java/.../ui)                            │
│  Composable screens + ViewModels (Hilt-injected).               │
│  Observe StateFlow from ViewModels; dispatch user intents.      │
└───────────────▲──────────────────────────────────────────────────┘
                │ exposes UI state (StateFlow), accepts intents
┌───────────────┴──────────────────────────────────────────────────┐
│  Domain layer  (.../domain)                                      │
│  Pure Kotlin: use-cases (ScanLeaf, AttachScanToPlant,            │
│  GenerateSuggestions), model abstractions (Plant, ScanRecord,    │
│  Diagnosis, Explanation). No Android framework imports.          │
└───────────────▲──────────────────────────────────────────────────┘
                │ calls repository / inference interfaces
┌───────────────┴──────────────────────────────────────────────────┐
│  Data layer  (.../data)                                          │
│  Room (entities, DAOs, LeafGuardDatabase), repositories,         │
│  EncryptedSharedPreferences (PinStore), DiseaseClassifier (TFLite), │
│  SaliencyGenerator. Hilt modules bind these to interfaces.        │
└──────────────────────────────────────────────────────────────────┘
```

## Key components (target state across phases)

| Component | Layer | Phase | Responsibility |
| --- | --- | --- | --- |
| `LeafGuardNavHost` | UI | 1 | Single NavHost; gates auth ↔ main app |
| `AuthViewModel` / `PinStore` | UI / Data | 2 | Register, PIN login, Keystore-backed storage |
| `LeafGuardDatabase`, entities, DAOs | Data | 3 | Plants, Scans, Records persistence |
| `PlantRepository`, `ScanRepository` | Data | 3 | Single source of truth, Flow-based |
| `DiseaseClassifier` | Data | 4 | Load Swin-Tiny model, preprocess 224×224, run inference (softmax baked in) |
| `SaliencyGenerator` | Data | 4 | Occlusion-sensitivity heatmap (model-agnostic; TFLite exposes no conv gradients) |
| `ScanViewModel` + CameraX | UI | 5 | Live preview, capture, wire to inference → result |
| `SuggestionsEngine` | Domain | 7 | Map 38 classes → treatment steps + severity |

## Navigation graph

```
auth/register ──┐
                ├─ (gated by PinStore.hasRegistered / correct PIN) ─► dashboard
auth/login ─────┘

dashboard ─┬─► plants ─► plant/{id} ─► scan?plantId={id} ─► result/{scanId}
           ├─► scan ─► result/{scanId}
           └─► images

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

- TFLite interpreter footprint: ~120–180 MB resident (the 112 MB Swin-Tiny FP32 model + the
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
