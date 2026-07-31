# HeroLens V6 Coach

HeroLens is an unofficial Android-first Overwatch hero-pick coach. It can scan a scoreboard with the rear camera, recommend three heroes and explain the enemy counters, ally synergies, map fit, player-comfort impact and switch timing behind every suggestion.

## What V6 adds

- Fast, Balanced and Accurate live-scan modes.
- Automatic or fixed scoreboard portrait-side detection.
- Multi-frame stability voting, frame-quality rejection and confidence review.
- Pinch zoom, exposure, torch, tap-to-focus and autofocus reset.
- Improved portrait matcher using luminance, edges, colour histogram and perceptual hash.
- Cached detector signatures after first preparation.
- PC/console context and persistent player hero pool.
- Recommendation score breakdown, first-fight playbook and risk warning.
- Onboarding, richer scan history and improved mobile layout.
- Matching V6 browser/PWA prototype.

See `V6_RELEASE_NOTES.md` for the complete list.

## Build the Android APK with GitHub Actions

The repository contains `.github/workflows/android-build.yml`.

1. Upload the project contents to the root of your GitHub repository.
2. Open **Actions**.
3. Select **Build Android APK**.
4. Select **Run workflow**.
5. After the green check mark, download **HeroLens-debug-apk** from Artifacts.
6. Extract the ZIP and install `app-debug.apk` on Android.

Local Windows build:

```powershell
.\gradlew.bat test assembleDebug
```

The APK is written to:

```text
app\build\outputs\apk\debug\app-debug.apk
```

## Browser/PWA

The web version is in `prototype/`. Camera access generally requires HTTPS; a downloaded local HTML file may be blocked by mobile browsers. Deploy the complete `prototype` folder through GitHub Pages or another HTTPS host.

## Scanner behaviour

The first recognition preparation needs internet access to obtain the portrait templates. Android then stores compact detector signatures locally. Camera frames are processed on-device and are not uploaded by the supplied code.

The V6 recognizer remains experimental template matching, not a trained production AI detector. Review detections and correct uncertain slots before relying on a recommendation.

## Data and legal notes

- HeroLens is not affiliated with or endorsed by Blizzard Entertainment.
- The repository does not include game binaries, memory readers, input automation, injection or game-client hooks.
- Recommendations are advisory and never automate gameplay.
- Obtain legal review before public distribution, monetisation or using visual training datasets.
