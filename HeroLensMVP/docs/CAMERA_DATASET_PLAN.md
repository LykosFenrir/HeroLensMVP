# Camera dataset plan

## Labels

Each training sample should contain:

- scoreboard bounding polygon;
- platform and aspect ratio;
- ally/enemy side;
- slot index;
- hero ID or `unknown`;
- occlusion and glare flags;
- UI scale and language.

## Splits

Do not randomly split adjacent video frames. Split by recording session and physical display so near-duplicate frames cannot leak into validation.

Recommended split:

- 70% training
- 15% validation
- 15% held-out test

## Required robustness cases

- PC and console scoreboards
- 1080p, 1440p and 4K displays
- phone portrait and landscape capture
- off-axis perspective
- monitor glare and moiré
- color-blind settings
- UI scale variations
- newly released hero portraits
- dead/respawning visual states
- intentionally unknown or corrupted slots

## Acceptance targets

Before enabling automatic submission to the recommendation engine:

- scoreboard localization recall ≥ 99% on the held-out test set;
- hero top-1 accuracy ≥ 98% for clear slots;
- calibrated confidence threshold that routes ambiguous slots to correction;
- no silent substitution when the model is uncertain;
- median inference latency suitable for the target mid-range Android device.
