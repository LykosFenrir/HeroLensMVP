# HeroLens V6.3 scanner validation report

Date: 31 July 2026

## Target setups

- Hisense 55-inch TV running Overwatch on PS5.
- ASUS ROG Strix G15 laptop.
- Android phone in portrait, landscape-left and landscape-right.

Physical hardware was not available in the build environment. Validation used the user's actual camera captures from those setups, converted to the same 960×540 landscape or 540×960 portrait stream size requested from CameraX.

## Scoreboard-locator fixture matrix

Complete scoreboards: **45/45 FOUND**.

- ASUS laptop, close landscape view: 9/9; confidence 82–99%.
- ASUS laptop, portrait phone view: 9/9; confidence 69–86%.
- Hisense TV, close 5v5 view: 9/9; confidence 90–98%.
- Hisense TV, close 6v6 view: 9/9; confidence 92–99%.
- Hisense TV, distant view: 9/9; confidence 73–93%.

Each set included the original capture plus darker, brighter, reduced-contrast, increased-contrast, blurred, warm-white-balance, cool-white-balance and mild-perspective variants.

Negative controls:

- Non-scoreboard/menu captures: **8/8 NOT_FOUND**.
- Incomplete lobby captures: **4/4 INCOMPLETE**, not falsely accepted as complete.

Overall locator confidence on complete scoreboards: minimum 69%, average 87.6%, maximum 99%.

## Packed camera-frame conversion

Pure conversion harness: **3/3 passed**.

- CameraX current RGBA packed order.
- Compatibility with legacy ARGB packed order.
- Row padding plus 90-degree pixel rotation.

## Source checks

- Pure Kotlin locator/signature core compiled successfully.
- Template detector compiled against a local Android-context/repository stub.
- Kotlin parser found no syntax-level errors in the updated Compose camera screen or template repository.
- Android XML and JSON assets were parsed successfully.

## Changes validated by source inspection

- `SCREEN_ORIENTATION_FULL_SENSOR` is applied only while the scanner is open and the previous orientation is restored when exiting.
- Camera preview and analysis target rotation update when the display rotates.
- Zoom is clamped to the camera-reported minimum and maximum and observed through CameraX `ZoomState`.
- Manual zoom suppresses auto-framing for four seconds.
- Back, Close and Exit Scan all use the same scanner-close path.

## Remaining boundary

Hero classification still uses multi-frame template matching rather than a production-trained neural model. The new release improves portrait crop alignment, uses blue/red team-background template variants and lowers the per-frame gate while retaining multi-frame consensus. Final classification accuracy must still be verified on the actual phone camera after the GitHub-built APK is installed.
