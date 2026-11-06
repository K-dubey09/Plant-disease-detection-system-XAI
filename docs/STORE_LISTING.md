# LeafGuard - Store Listing (v2.5.1.0)

Material for a Google Play / app-store listing. All copy is English (en); the UI string table
(`app/src/main/res/values/strings.xml`) is structured for translation as a follow-up.

## App name

LeafGuard

## Short description (<= 80 chars)

On-device plant disease diagnosis with AI explainability - 38 crops, no cloud.

## Long description

LeafGuard turns your phone's camera into an on-device plant pathologist. Point it at a leaf and it
classifies the condition across 38 PlantVillage crop classes - apple, tomato, potato, corn, grape,
pepper, strawberry and more - using a trained DINOv2 ViT-S/14 vision transformer (3-view
test-time augmentation) that runs entirely on the
device (no photo ever leaves your phone). Every diagnosis comes with a severity tier, stage-aware
treatment steps (immediate, short-term, long-term) and a prevention checklist, plus an
occlusion-sensitivity heatmap that shows which regions of the leaf drove the prediction - so you can
see why the model said what it said, not just what it said.

Built for gardeners, smallholders and students:

- Live CameraX capture or import a photo; the result screen shows the predicted condition, a
  confidence bar, the occlusion heatmap, and the staged treatment + prevention advice.
- Track your plants: add the plants you own, attach each scan to a plant, and build a per-plant
  disease history over time.
- A Dashboard summarizes your scans; History filters by plant; Records exports the per-plant log to
  CSV or PDF; an Images gallery shows every captured leaf and its heatmap side by side.
- Privacy-first: the app is PIN-gated (your choice of 6-digit PIN on first launch, stored as a
  salted, Keystore-backed derived key). All scans, plants and images stay on-device in a local Room
  database - there is no account, no server, no telemetry.

Model accuracy: 86.7% field / 99.3% lab / 96.1% mixed (DINOv2 v3.5, with TTA). The model is
committed in the APK and verified by a SHA-256 integrity guard at runtime. Advisory treatment
content is general integrated-pest-management guidance, not a prescription - always confirm rates
and products against your local extension service before treatment.

## Category

Tools (secondary: Education)

## Content rating

Everyone

## Minimum Android

Android 8.0 (API 26)

## Permissions

- Camera - live leaf capture (optional; you can import a photo instead).
- Photos / Storage (media) - import an existing leaf photo; save exported CSV/PDF.

No network permission is required or used for inference. The model + label set ship inside the APK.

## Privacy policy (summary)

LeafGuard is on-device only. It collects no personal data, sends nothing to any server, and includes
no analytics or advertising SDKs. Your PIN is stored as a salted PBKDF2-HMAC-SHA256 derived key in
EncryptedSharedPreferences keyed to the Android Keystore; the raw PIN is never persisted. Scan
images, plant records and diagnosis history live in a local Room database and internal storage on
your device; uninstalling the app removes all of it. The only outbound network use is the build-time
CI pull of the trained model from a private Kaggle dataset (that happens in CI, never on your phone).

## APK size (v2.5.1.0)

- Total: ~112 MB (signed release, R8 minified + resource-shrunk).
- Trained model (`assets/models/plant_disease.tflite`): 86.8 MB (the DINOv2 ViT-S/14 v3.5 38-class
  head, FP32; committed under GitHub's 100 MB file limit).
- App + dependencies excl. model: ~25 MB. Of that, ~20 MB is the
  TFLite native runtime shipped for all four ABIs (arm64-v8a, armeabi-v7a, x86, x86_64);
  `classes.dex` is ~1.7 MB and `res/` is ~0.1 MB after shrinking.
- Future size win: per-ABI APK splits would ship only the one matching native runtime per install,
  cutting ~20 MB of redundant native libs to ~5 MB. An INT8 quantized DINOv2 variant is a separate
  future option for size-constrained devices.

## Signing

The release APK is signed with v1 (JAR) + v2 + v3 schemes, so it installs on all Android 8.0+
devices including OEM installers that require v1. The v1+v2 assertion runs as a CI step on every
release build.

## Release asset

`LeafGuard-v2.5.1.0-release.apk` on the GitHub Release tagged `v2.5.1.0` - a raw `.apk` download
(no zip wrapping). Download and tap to install.

## Screenshots (capture targets)

1. Auth - PIN register screen (first launch).
2. Scan - live camera preview with the capture button.
3. Result - diagnosis + confidence bar + occlusion heatmap + severity badge + staged treatments.
4. Result - a HEALTHY result (the "no treatment needed" state).
5. Plants - the plant list with add dialog.
6. Dashboard - live stats + quick-action cards.
7. History - per-plant filter chips + scan cards.
8. Records - per-plant log + Export CSV/PDF.
9. Images - the Coil gallery (source leaf + heatmap per scan).

## Feature graphic (text draft)

"LeafGuard - on-device plant disease diagnosis. 38 crops. AI explainability. No cloud. Point, scan,
treat."
