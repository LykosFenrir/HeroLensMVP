# HeroLens V7 Scan Burst

HeroLens is an unofficial Android-first Overwatch hero-pick coach. It combines scoreboard recognition with explainable recommendations based on enemy counters, ally synergy, map fit, rank, platform, personal hero comfort and switching cost.

## V7 scanner workflow

V7 replaces endless live classification and ten fixed alignment boxes with a simpler flow:

- one large scoreboard guide;
- tap **Scan**;
- a fixed 6/9/12-frame burst;
- pause and review;
- tap any uncertain slot to correct it;
- use the reviewed lineup for recommendations.

The scoreboard locator runs while aiming, while expensive portrait matching runs only during the burst. CameraX uses latest-frame-only analysis so old frames do not queue behind the current viewfinder.

## Opt-in dataset collector

The **Help train accurate detection** setting is off by default. After a reviewed scan, HeroLens can locally save only the cropped scoreboard and portrait cells with corrected labels. Nothing is uploaded automatically. Samples can be exported as a ZIP or deleted from Settings.

See `V7_SCAN_BURST_AND_DATASET.md` for privacy and dataset details.

## Android build

Requirements:

- Android Studio / Android SDK 36
- JDK 17
- Internet access for the first Gradle sync and first portrait-reference preparation

Build on Windows:

```powershell
.\gradlew.bat test assembleDebug
```

APK output:

```text
app\build\outputs\apk\debug\app-debug.apk
```

The included GitHub Actions workflow builds the same debug APK and uploads it as `HeroLens-debug-apk`.

## Implemented

- Tank, Damage and Support recommendations
- Enemy-counter and ally-synergy explanations
- Map, rank, PC/console and personal hero-pool context
- Scoreboard locator for TV/laptop captures
- 5v5 and 6v6 geometry handling
- Fast, Balanced and Accurate burst profiles
- Review/correct screen
- Zoom, focus, torch, exposure and rotation support
- Local history
- English and Arabic resources
- Optional local training-sample collection and ZIP export
- Replaceable detector interface for a future LiteRT model

## Accuracy note

The bundled portrait recognizer remains experimental template matching, not a production-trained neural detector. V7 improves usability and creates the reviewed dataset workflow needed to train and validate the future model honestly.

HeroLens is not affiliated with or endorsed by Blizzard Entertainment. The repository contains no game-client hooks, memory readers, input automation or game binaries.
