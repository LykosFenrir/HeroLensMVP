# Reference review and product decisions

This review was used to improve HeroLens without copying another product's brand or exact interface.

## OverHelper

Reference: https://overhelper.com/ and its current store listings.

Useful ideas observed:

- scan-first camera workflow;
- on-device recognition;
- rank-aware recommendations;
- synergy/counter drilldown;
- history, haptics, zoom and model/data versioning;
- fast best-pick and alternative presentation.

HeroLens additions beyond that baseline:

- personal hero-pool weighting;
- explicit ultimate-economy switch coaching;
- detailed mechanism explanations for counters and synergies;
- composition coverage before/after a pick;
- multi-frame consensus and frame-quality rejection;
- manual correction of uncertain detections;
- no premium lock on explanation details in the prototype;
- Arabic resources and RTL support.

## Overpicker

Reference: https://www.overpicker.com/

Useful ideas observed:

- explicit counter matrix;
- explicit synergy matrix;
- team-composition calculator;
- tier/rank context.

HeroLens uses those as product categories, but the included seed weights and explanation rules are independently defined in readable source code.

## Android and web platform references

- Android runtime camera permissions: https://developer.android.com/training/permissions/requesting
- CameraX image analysis and latest-frame backpressure: https://developer.android.com/media/camera/camerax/analyze
- Web camera secure-context rules: https://developer.mozilla.org/en-US/docs/Web/API/MediaDevices/getUserMedia

These platform rules explain why the downloaded local HTML page did not reliably open the camera and why the native Android app is the preferred live-scanning build.
