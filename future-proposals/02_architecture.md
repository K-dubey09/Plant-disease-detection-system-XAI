# 02 — App & System Architecture

## Current state

LeafGuard is a single-activity Jetpack Compose app with MVVM + Clean Architecture layers (`ui` /
`domain` / `data`), wired by Hilt 2.52, persisted by Room 2.6.1 (v3, **unencrypted**), with a
singleton TFLite `Interpreter` running on a dedicated single-thread dispatcher. It is **fully
offline**: no network calls, `android:allowBackup="false"` on the secure prefs, all images stored
as JPEG/PNG under `filesDir/scans/`. The 86.8 MB model is committed in `app/src/main/assets/models/`
and shipped in one monolithic APK (~112 MB release). PIN auth is Keystore-backed. Navigation is a
single `LeafGuardNavHost`; the About screen (v2.6.0.0) is read-only transparency.

## Proposal

**A. Modularization + build logic.** Split `:app` into feature modules (`:feature:scan`,
`:feature:plants`, `:feature:records`, `:feature:about`) and a `:core:data`, `:core:domain`,
`:core:designsystem` set, with convention plugins in a `build-logic/` composite build. This cuts
incremental build times, enforces layer boundaries (today they are conventional, not compile-time),
and unlocks Play Feature Delivery of the model.

**B. Android App Bundle + per-ABI splits + on-demand model delivery.** The model is 86.8 MB of the
~112 MB installed footprint. An App Bundle delivers only the one ABI the device needs (saves ~60 MB
on most installs today), and the model itself can become an on-demand / install-time Play Asset
Pack so the initial install is small and the model is fetched only when first needed. Keep the
committed-model CI path as the fallback for sideloading.

**C. SQLCipher for Room.** The database is unencrypted by design (FBE on API 26+ is relied on).
For a stronger threat model (rooted devices, forensic extraction), layer SQLCipher in via the
`net.zetetic:android-database-sqlcipher` support library and the Room `SupportFactory`. Cost: a
one-time migration + a key derived from the existing Keystore-backed PIN key. Gated behind the
existing threat-model note in `docs/ARCHITECTURE.md`.

**D. Optional, privacy-preserving server-assist mode.** Keep on-device as the default and only
path; add an **opt-in** "ask the cloud model" affordance for low-confidence scans, where the user
explicitly approves uploading that one leaf to a server-side stronger model. This must be
explicit, auditable, and off by default — the "no leaf ever leaves the device" guarantee is a core
promise and must remain the default posture.

**E. Cross-device history sync (opt-in).** Today scans live only on the originating device. An
opt-in WorkManager-backed sync to the user's own cloud (WebDAV / Google Drive appdata) would let a
farmer carry history across phones. Pure user-data, end-to-end, no server learns the content.

**F. Model registry / runtime model selection.** Replace the single hardcoded
`plant_disease.tflite` with a `ModelRegistry` that selects among the committed FP32 model and any
future quantized variants (`01_model.md` §A) by device capability (RAM, ABI, NNAPI/GPU delegate
availability), with the same `ModelIntegrity` SHA-256 guard per model. This is the architectural
precondition for shipping a quantized model without forcing a single binary on everyone.

**G. Offline-first observability.** A bounded, privacy-preserving on-device metrics log
(low-confidence rate by crop, first-scan latency, crash signatures) that the user can optionally
export — no telemetry is sent without explicit consent.

## Feasibility & effort

| Sub-proposal | Build-system change? | User-facing? | Effort | Risk |
| --- | :---: | :---: | :---: | --- |
| A. Modularization | yes (composite build) | no | Med | large refactor; CI must stay green |
| B. App Bundle + asset pack | yes | yes (smaller install) | Med | Play Console setup; sideload path must still work |
| C. SQLCipher | no | no (transparent) | Low–Med | migration correctness |
| D. Server-assist (opt-in) | no | yes | Med | privacy-default regression risk |
| E. Cross-device sync | no | yes (opt-in) | Med | cloud-provider choice + auth UX |
| F. Model registry | no | no | Low–Med | precondition for quantized models |
| G. Observability | no | yes (export) | Low | must not become silent telemetry |

## Risks & open questions

The biggest architectural risk is **regressing the offline-by-default privacy guarantee** (D, E).
Any network path must be opt-in, surfaced in the About screen, and auditable in the navigation
graph — a user who reads "no leaf ever leaves the device" must not be surprised. Modularization
(A) is high-value but high-churn: it should be done in a dedicated branch with the full 152-test
JVM suite + CI as the gate, not incrementally on `main`. App Bundle delivery (B) requires a Play
Store listing decision (the app is currently sideloaded), so it is coupled to a distribution
decision. SQLCipher (C) and the model registry (F) are low-risk, high-value, and can proceed
independently.
