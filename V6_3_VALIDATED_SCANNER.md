# HeroLens V6.3 validated scanner correction

V6.3 addresses the repeated `0/10` issue observed on the user's Hisense 55-inch TV with PS5 and ASUS ROG Strix G15 laptop.

## Root causes corrected

1. CameraX packed analysis bytes were treated as a single fixed channel order. V6.3 samples the opaque alpha channel and safely decodes current RGBA as well as legacy ARGB layouts.
2. The old detector guessed where the scoreboard was. V6.3 anchors on the red enemy panel, finds the blue ally panel immediately above it, and derives the portrait rows from those real bounds.
3. Transparent portrait templates were composited only over grey. V6.3 adds cyan/blue and red team-background variants.
4. Portrait scanning was too narrow in portrait orientation. The preview now uses a larger sensor-matched viewfinder.
5. Zoom feedback was unclear. V6.3 adds 1x/2x/3x presets, camera maximum zoom reporting, and a four-second manual override before auto-framing resumes.
6. `0 frames` offered little diagnosis. The scan screen now shows template count, locator state/confidence, analysis-frame dimensions, quality and camera zoom capability.

## Local validation

The locator was evaluated on the user's supplied TV/laptop captures after conversion to realistic CameraX analysis streams (960x540 landscape or 540x960 portrait):

- 45/45 complete-scoreboard variants classified as `FOUND`.
- 8/8 non-scoreboard menu variants classified as `NOT_FOUND`.
- 4/4 incomplete lobby variants classified as `INCOMPLETE`.

The complete variants included the original captures plus brightness, contrast, blur, warm/cool colour-temperature and mild-perspective changes. The set includes close and distant Hisense TV views, ASUS laptop views, portrait phone framing, landscape phone framing, 5v5 and 6v6 tables.

Packed camera conversion tests:

- Current RGBA byte order: passed.
- Legacy ARGB compatibility: passed.
- Row padding plus 90-degree rotation: passed.

Pure Kotlin locator/signature sources compiled successfully. The full Android APK still must be built by the repository's GitHub Actions workflow because the working environment does not include the Android SDK.

## Honest accuracy boundary

This release materially improves localisation and template matching, but it is still not a production-trained neural detector. Multi-frame confirmation and manual correction remain enabled. Real-device validation on the Hisense/PS5 and ASUS laptop is still required after installing the APK.
