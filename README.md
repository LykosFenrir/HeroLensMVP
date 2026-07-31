# HeroLens V5 Supreme

HeroLens is an **unofficial Android-first Overwatch hero-pick coach prototype**. V5 Supreme is redesigned around a scan-first mobile workflow: open the scanner, allow the rear camera, point at the scoreboard, review the detected lineup, and receive explainable picks.

## Important camera answer

Yes, the phone must grant **Camera permission**.

- **Native Android app:** `android.permission.CAMERA` is declared in `AndroidManifest.xml`, requested at runtime when the scanner opens, and the user can jump to App Settings if permission was denied permanently.
- **Web/PWA:** the browser also asks for camera permission, but live camera access must run from a secure origin. Opening `index.html` directly from Downloads as a `file://` page is unreliable on Samsung Internet and other Android browsers. Deploy the complete `prototype` folder over **HTTPS**, then allow Camera permission for that site.

## V5 Supreme improvements

### Scan experience

- Dedicated native CameraX scanner
- Rear-camera runtime permission flow and App Settings recovery
- Latest-frame-only image analysis to keep the preview responsive
- Automatic left/right scoreboard-layout selection
- Multi-frame consensus before accepting a hero
- Blur, darkness and glare quality checks in both Android and web scanners
- 1×–3× zoom, tap-to-focus, alignment guides and confidence values
- Haptic confirmation when the lineup locks
- Tap any uncertain Android detection to correct it manually
- Auto import and immediate recommendations after a stable scan

### Recommendation experience

- Best pick plus two alternatives
- Tap alternatives to compare their complete explanations
- Detailed **how this hero counters the enemy** descriptions
- Detailed **why this hero synergizes with an ally** descriptions
- Map-profile, rank-profile and personal hero-pool factors
- Ultimate-charge switch warning
- Switch coach: stay, switch after ultimate, or switch after a safe reset
- Team-composition coverage before and after the suggested pick
- Fit scores are contextual indicators, not claimed win probabilities

### Product features

- History, Scan and Settings navigation
- Local scan history
- English and Arabic resources with RTL support
- Hero portraits with fallback initials
- Light/dark system theme
- Privacy-first defaults: no account and no snapshot uploads
- Versioned hero data, recommendation weights and detector interface
- PWA manifest, service worker, install prompt, screen wake lock and best-effort landscape orientation
- GitHub Actions workflows for one-click APK builds and HTTPS GitHub Pages deployment

## Android requirements

- Android Studio
- JDK 17
- Android SDK 36
- Android 8.0 or newer
- Internet access for the first Gradle sync
- Internet access on first run while portrait templates are cached

## Build the Android APK

1. Open the `HeroLensMVP` folder in Android Studio.
2. Allow Gradle sync to finish.
3. Connect an Android phone with USB debugging enabled, or start an emulator.
4. Run the `app` configuration.

Windows command line:

```powershell
.\gradlew.bat test assembleDebug
```

Linux/macOS:

```bash
./gradlew test assembleDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Web/PWA test

The `prototype` folder is a complete static PWA package. Do **not** expect reliable live camera access by tapping the downloaded HTML file directly. Deploy the whole folder over HTTPS and open the generated HTTPS address on the phone.

See:

```text
prototype/DEPLOY-HTTPS.md
```


## Build without installing Android Studio

The repository includes `.github/workflows/android-build.yml`. Put the project in a GitHub repository, open **Actions → Build Android APK → Run workflow**, then download the `HeroLens-debug-apk` artifact after the workflow succeeds.

## Put the web scanner online with HTTPS

The repository includes `.github/workflows/pages.yml`. In the GitHub repository, enable **Settings → Pages → Source: GitHub Actions**, then run the **Deploy HeroLens PWA** workflow. Open the generated HTTPS Pages address on the phone, tap **Allow & start camera**, and approve Camera permission.

The web scanner now also uses concurrent portrait preparation, a frame-quality gate, `requestVideoFrameCallback` when available, a screen wake lock, best-effort landscape orientation and an installable-PWA prompt.

## Recognition accuracy status

The camera pipeline is real, but the supplied recognizer is still an **experimental multi-frame portrait-template matcher**. It is more stable than single-frame matching, but production accuracy across monitor glare, console/PC layouts, UI scales, languages and future patches requires:

1. a legally usable scoreboard dataset;
2. trained hero, map and mode detection models;
3. confidence calibration with an unknown class;
4. testing across phones, monitors and console capture conditions;
5. over-the-air model/data delivery.

The detector contract is isolated under `vision/HeroDetector.kt`, so a LiteRT/TensorFlow Lite or MediaPipe model can replace the template matcher without redesigning the app.

## Validation performed

- Pure Kotlin recommendation engine compiled
- Smoke scenario passed
- Detailed counter/synergy reason generation compiled
- Browser JavaScript passed `node --check`
- Browser DOM ID references were validated
- Android XML files parsed successfully
- PWA manifest parsed successfully
- ZIP integrity and SHA-256 checks performed

An APK is not included because this execution environment does not contain the Android SDK or cached Google Maven dependencies.

## Legal and product notes

- HeroLens is not affiliated with or endorsed by Blizzard Entertainment.
- Recommendations are advisory and must never automate game input.
- The project contains no memory reader, injection, controller automation or game-client hook.
- Before public release, review the name, store listing, portraits, model-training dataset, monetization and trademark usage.
