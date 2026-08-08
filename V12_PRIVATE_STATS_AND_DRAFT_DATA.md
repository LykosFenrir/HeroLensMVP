# HeroLens V12 — Private performance and current Draft data

V12 adds the first honest account-like performance loop without pretending that
Blizzard exposes an Overwatch match-history API.

## Private performance

- The Stats tab accepts an optional BattleTag as an OCR row-matching hint only.
- A final scoreboard can be photographed or selected from the device.
- Bundled ML Kit text recognition runs on device; the image is never uploaded or
  copied into match history, and temporary camera captures are deleted on close.
- HeroLens parses the player's E/A/D, damage, healing and mitigation, then requires
  an editable review of result, mode, map and hero before saving.
- Saved matches and aggregates live in app-private, schema-versioned storage.
- Home shows actual reviewed matches when available; coaching plans remain a
  separate history because recommendations are not match telemetry.

HeroLens stores no Battle.net password, OAuth token, cookie or session. A BattleTag
does not connect an account and the UI says so explicitly.

## Stadium Competitive Draft

The live mode is modeled as official 5v5 blind pick. Draft photos may contain
partial reveals; unknown slots remain editable and only revealed picks are imported.
Draft pictures open with ten review slots and never run through the standard
stacked-scoreboard detector.

Automatic draft-grid promotion is deliberately not claimed yet. The new manifest
auditor requires current `blind-pick-2025-11+` images, explicit training rights,
privacy-safe game-UI crops, whole-session split isolation, multiple sessions and
multiple validation devices. Old snake-draft launch art and ordinary copyrighted
web images cannot satisfy that gate.

The standard cropped-scoreboard collector does not export Draft review images.
Training stays disabled until a separate privacy-masked Draft collector is ready.

## Model gates

The classifier gates remain unchanged: 72% synthetic, 72% scoreboard-crop, 65%
full-scoreboard, 62% combined scenes, 58% 5v5 and 58% 6v6. Real-TV validation
remains 65%. Draft readiness is additive and cannot lower any existing gate.
