<!-- Thanks for contributing! Please fill in the sections below. -->

## Summary

<!-- One or two sentences: what does this PR do? -->

## Motivation

<!-- Why is this change needed? Link an issue or a future-proposals/ section if relevant. -->

## Changes

<!-- Bullet list of the substantive changes. Note any breaking changes. -->

## Testing

- [ ] `./gradlew test` passes (152 JVM unit tests)
- [ ] `./gradlew lintDebug` passes (0 errors)
- [ ] `./gradlew assembleDebug` builds
- [ ] Added/updated unit tests for the new behavior
- [ ] Manually verified on a device (if the change touches camera, TFLite, or UI)

## Model & data impact

<!-- If this PR does NOT touch the model or data, leave the boxes unchecked and write "N/A". -->
- [ ] N/A — no model or data change
- [ ] If the `.tflite` changed: `ModelIntegrity.EXPECTED_SHA256` updated to the new SHA-256
- [ ] If the `.tflite` changed: `MODEL_CARD.md` updated (size, SHA-256, metrics)
- [ ] If labels changed: label order in `labels.txt` preserved (or a retrained model + new card version)
- [ ] If data/conversion changed: `DATASHEET.md` and `REPRODUCIBILITY.md` updated

## Checklist

- [ ] Commit messages follow Conventional Commits (`feat:`, `fix(scope):`, `docs:`, …)
- [ ] No secrets, tokens, or private keys committed
- [ ] `domain/` layer has no Android framework imports
- [ ] Licensed under MIT — no incompatible third-party code introduced
