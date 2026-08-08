# HeroLens V9 — Full-scoreboard detector

V9 adds an optional one-pass portrait-box detector ahead of the existing hero
classifier. The detector consumes the localized scoreboard at 320x192 and emits
up to 24 pre-decoded portrait candidates. Hero identity classification remains a
single batched ONNX call using the validated 52-hero classifier.

The design is intentionally fail-safe:

- The detector model is optional and loaded entirely on-device.
- Low-confidence or incomplete detector results fall back to the V8 geometry
  profiles and classifier.
- Team/row assignment, class-agnostic NMS, duplicate hero resolution, and temporal
  stabilization remain local.
- No OverHelper code, assets, model weights, or proprietary data are included.

The bundled detector was trained from 400 generated full scoreboards and validated
on 100 disjoint generated scoreboards:

- mean IoU: 64.03%
- portrait detection recall: 90.44%
- portrait detection precision: 89.65%

These figures are synthetic bootstrap metrics. A broad, reviewed real-scoreboard
dataset is still required before treating them as real-camera accuracy claims.
