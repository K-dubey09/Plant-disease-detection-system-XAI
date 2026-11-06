# Security Policy

LeafGuard is an **offline Android application** — by design it makes no network calls and no leaf
image ever leaves the device. This policy covers how to report vulnerabilities and what threat
model the project currently assumes.

## Supported versions

Only the latest release on `main` is supported with security fixes. The current release is
**v2.7.0.0** (`versionCode` 12); older tagged releases (v2.6.0.0, v2.5.x, v2.0.0.0, v1.5.0.0) are
superseded and will not receive backports.

## Reporting a vulnerability

**Please do not open a public GitHub issue for a security vulnerability.** Instead, report it
privately through one of:

1. **GitHub Private Security Advisories** (preferred): go to the repository's **Security** tab →
   "Report a vulnerability". This keeps the report private to the maintainer until a fix is
   published.
2. **Email**: `kushagra.23b1531207@abes.ac.in` with the subject `LeafGuard security report`.

Please include, where applicable:

- The app version (About screen → app version) and the device's Android version.
- The shipped model SHA-256 prefix shown on the About screen (to confirm whether the model
  buffer was modified).
- A minimal reproduction (steps, or a small code snippet / crafted input).
- The impact you observed or expect.

I will acknowledge receipt within **5 working days**, aim to provide an assessment and a fix or
mitigation within **30 days**, and coordinate public disclosure with you once a fix is released.
Reporters who follow coordinated disclosure will be credited in the release notes unless they ask
to remain anonymous.

## Threat model

The application's current security posture, honestly stated:

- **On-device data.** Scans, plant records, and captured images are stored in the app's internal
  storage (`filesDir/scans/`) and a Room database. The database is **not** encrypted in the
  current release; it relies on Android's file-based encryption (FBE) on API 26+. See
  `docs/ARCHITECTURE.md` → Security. SQLCipher encryption is a tracked future improvement
  (`future-proposals/02_architecture.md` §C).
- **PIN authentication.** The 6-digit PIN is stored as a salted PBKDF2-HMAC-SHA256 derivation
  (100k iterations) inside `EncryptedSharedPreferences` keyed to the Android Keystore. The raw
  PIN is never persisted. This protects casual extraction but is not resistant to a determined
  attacker with physical access to a rooted device.
- **Model integrity.** The shipped `plant_disease.tflite` is guarded by `ModelIntegrity`, which
  compares the executed byte buffer's SHA-256 against the pinned `EXPECTED_SHA256`
  (`3660cfdb…`). A mismatch surfaces a banner on the result screen. This detects tampering after
  the fact; it does not prevent a sufficiently privileged attacker from replacing both the buffer
  and the pinned digest.
- **Network.** The app declares no INTERNET permission and makes no network calls. There is no
  server-side attack surface from the app itself. (Note: the public Kaggle training notebooks and
  the conversion pipeline run on Kaggle/your own machine, not in the app.)

## Out of scope

- Attacks requiring physical device access to a rooted phone with the bootloader unlocked —
  Android's hardware-backed security is the appropriate defense there, not the app.
- Vulnerabilities in third-party dependencies (TFLite, CameraX, Hilt, Room, Compose) — report
  those upstream. LeafGuard will still pin and patch affected versions promptly.
- Misclassifications by the model (an 86.7% field accuracy means ~1 in 8 field leaves is wrong).
  These are **model-quality** issues, not security issues; report them as GitHub issues, not
  security reports. See `MODEL_CARD.md` → Caveats.

## Disclosure policy

Coordinated disclosure is expected. Please do not publish details of an unpatched vulnerability
publicly before a fix is released or before the agreed disclosure window has elapsed.
