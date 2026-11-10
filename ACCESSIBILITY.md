# Accessibility

LeafGuard is an on-device Android app used in the field — a farmer or gardener framing a leaf in
bright sunlight, often one-handed. Accessibility is part of "every diagnosis is trustworthy": if a
user cannot read the diagnosis or the treatment advice, the model's accuracy never reaches them.
This document states the accessibility standard LeafGuard holds itself to, the design choices that
support it, the known gaps, and how to report a barrier. It is a public commitment and a reference
for contributors, not a completed audit — see [Testing](#testing) for how verification is
approached and [Reporting an accessibility barrier](#reporting-an-accessibility-barrier) for how to
flag a gap you hit.

## Commitment

LeafGuard targets [WCAG 2.1](https://www.w3.org/TR/WCAG21/) AA where it applies to a native
Android UI, and follows the [Android accessibility](https://developer.android.com/guide/topics/ui/accessibility)
guidelines. The UI is built with Jetpack Compose + Material 3, whose components carry sensible
defaults — 48 dp minimum touch targets, semantic roles, focus order, and a color scheme that
adapts to the system light/dark theme and to dynamic color on Android 12+. The bar this project
sets for itself is that the **primary diagnosis path is fully usable without sight**: the
diagnosis, confidence, and treatment advice are delivered as text, not only as a visual heatmap,
so a non-sighted user receives the complete result and the recommended action.

## Design choices that support accessibility

- **Text-first result screen.** The result surfaces the condition name, a qualitative confidence
  tier (High / Moderate / Low), the top-3 alternative conditions with their confidence, and
  stage-aware treatment + prevention advice, all as text. The occlusion heatmap streams in
  afterward as an explanation supplement — it does not carry the diagnosis. A user who cannot see
  the heatmap still gets the full result.
- **Numeric PIN entry.** First-launch registration and PIN login use a labeled numeric field on
  the system's accessible numeric keypad; the PIN is stored in `EncryptedSharedPreferences` backed
  by the Android Keystore, with no accessibility surface of its own.
- **Material 3 lists and navigation.** The plant list, per-plant scan timeline, history, and
  dashboard are Compose lists; the project's standard is that every selectable row carries a
  content description and meets the 48 dp touch-target minimum so TalkBack and Switch Access can
  drive them.
- **Theme and contrast.** Material 3 with dynamic color provides a high-contrast dark theme for
  low-light use; the color scheme is kept readable in field lighting rather than optimized only
  for a desk.
- **Text surfaces for trust signals.** The model-integrity banner (shown when the committed
  `.tflite` SHA is mismatched) and the model-info card (which model + TTA produced the diagnosis)
  are text, not icons-only, so a screen reader announces them rather than a sighted user alone
  noticing a glyph.

## Known limitations

- **Occlusion-sensitivity heatmap is visual-only.** The jet-colormap overlay marks which leaf
  regions raised the predicted-class score, but it is not currently described in text or audio. The
  diagnosis itself is not gated on the heatmap, so a non-sighted user does not lose the diagnosis —
  only this one explainability channel. A ranked, spoken summary of the most-influential regions is
  a tracked future enhancement.
- **Camera capture requires sight.** Framing a leaf in the live CameraX viewfinder is a visual
  task. The gallery-pick alternative lets a user (or an assistant) select a previously captured
  photo, but acquiring the photo in the first place still needs sighted help. There is no fully
  non-visual capture path today.
- **No localization.** The UI text and treatment advice are in English; the project does not ship
  localized `strings.xml` resources. Users in non-English-speaking regions may need a sighted
  translator. Localization is a future enhancement rather than a near-term fix.
- **The heatmap's color encoding is not colorblind-safe by default.** The jet colormap relies on
  hue, which is ambiguous for some color-vision deficiencies. The text confidence tier and the
  top-3 alternatives are the colorblind-safe path to the same information.

## Testing

Accessibility is verified by manual checklist rather than an automated gate, because the highest-
value checks (TalkBack reading order, real-leaf framing in field lighting) are not fully
automatable. The checklist for a release:

1. **TalkBack** — navigate every screen (register → PIN → scan → result → plant detail → history →
   dashboard → About) linearly and confirm the diagnosis, confidence tier, top-3 alternatives,
   treatment advice, model-info card, and integrity banner are all announced in a sensible order.
2. **[Accessibility Scanner](https://developer.android.com/guide/topics/ui/accessibility/testing#accessibility-scanner)**
   — run on each screen and resolve every contrast, touch-target, and content-description finding
   it flags.
3. **Switch Access** — confirm every primary action (scan, add-to-plant, export, open history row)
   is reachable with a single switch.
4. **Large text / font scaling** — set the system font to 200% and confirm the result screen and
   treatment advice reflow without truncation.
5. **Dark theme + bright-screen field check** — read the result and treatment text outdoors on a
   high-brightness screen.

If you are contributing a UI change, please run items 1 and 2 before opening a pull request and
note the result in the PR description (see [`CONTRIBUTING.md`](CONTRIBUTING.md)).

## Reporting an accessibility barrier

Accessibility barriers are bugs — please report them through the standard issue workflow rather
than the private security channel (which is reserved for vulnerabilities per
[`SECURITY.md`](SECURITY.md)).

1. Open a GitHub issue using the **Bug report** template
   (`.github/ISSUE_TEMPLATE/bug_report.yml`).
2. Prefix the title with `[a11y]` and name the screen + the barrier (e.g.
   `[a11y] result screen — TalkBack does not announce the confidence tier`).
3. State the assistive technology used (TalkBack / Switch Access / large text / colorblind mode)
   and the Android version + device.

Reports are handled on a best-effort, community-welcomed basis — see
[`PROJECT_STATUS.md`](PROJECT_STATUS.md) for the project's current maintenance level. A barrier
that blocks the diagnosis or treatment text from reaching a user is treated as higher priority
than a barrier on the heatmap alone, consistent with the text-first commitment above.
