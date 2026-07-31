# HeroLens V6.3 Validated Scanner

HeroLens is an unofficial Android-first Overwatch hero-pick coach. It can scan a scoreboard with the rear camera, recommend three heroes and explain the enemy counters, ally synergies, map fit, player-comfort impact and switch timing behind every suggestion.

## What V6.3 includes

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

See `V6_RELEASE_NOTES.md`, `V6_2_SCANNER_REBUILD.md`, `V6_3_VALIDATED_SCANNER.md` and `docs/V6_3_SCANNER_TEST_REPORT.md` for details.


### V6.2 scanner rebuild

- Dynamic blue/red scoreboard localisation instead of fixed overlay coordinates.
- Automatic camera framing and working PreviewView pinch zoom.
- Portrait and both landscape rotations while scanning.
- Dynamic 5v5/6v6 hero slots aligned to the detected TV scoreboard.
- Faster multi-crop portrait matching and a fresh V6.2 detector cache.

### V6.3 validated scanner correction

- Adaptive CameraX packed-byte decoding for current RGBA and legacy ARGB camera streams.
- Scoreboard locator validated against supplied Hisense TV and ASUS laptop captures.
- Correct distinction between full scoreboards, incomplete lobbies and non-scoreboard menus.
- Team-colour portrait templates for cyan/blue and red scoreboard backgrounds.
- Wider portrait viewfinder, explicit zoom presets and live detector diagnostics.
- Auto-frame can use a partially visible team panel while waiting for the full scoreboard.
- Manual zoom remains in control for four seconds before auto-frame can adjust it.

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
