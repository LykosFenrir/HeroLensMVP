# HeroLens V8 AI Scanner

HeroLens is an unofficial Android-first Overwatch hero-pick coach. It combines scoreboard recognition with explainable recommendations based on enemy counters, ally synergy, map fit, rank, platform, personal hero comfort and switching cost.

## V8.2 Open Queue support

V8.2 adds structural 5v5/6v6 row detection, a manual **6v6 / Open Queue** override,
console progression-badge crop handling, multi-crop neural consensus and a first-use
Picture Scan permission fix. The training publish gate now evaluates both 5v5 and
6v6 generated scoreboards. See `V8_2_OPEN_QUEUE_FIX.md`.

## Three scanner modes

### 1. Auto Scan — fastest

Aim at the scoreboard and HeroLens automatically starts a short four-frame scan after locating both team panels. It can open recommendations as soon as at least three enemies and two allies are confidently recognized. This mode is designed to reduce time spent on the scoreboard during a live match.

### 2. Picture Scan — most stable

Take a clear photo or choose an existing screenshot. HeroLens locates the scoreboard, recognizes portrait cells, and opens a review screen where every uncertain slot can be corrected before analysis.

### 3. Manual Selection — guaranteed fallback

Select allies and enemies directly without using the camera.

## Optional neural AI model

V8 adds an on-device MobileNetV3-Small hero classifier exported to ONNX. Android inference runs locally through ONNX Runtime and attempts NNAPI acceleration on supported phones. The app deliberately keeps the V7 signature matcher as a fallback when the model has not yet been trained or bundled.

The repository does not include fabricated pretrained weights. Run the included GitHub Actions workflow **Train Hero AI Model** to create and validate the model. The default job creates:

- 1,040 independently augmented hero portrait samples;
- 220 unknown/background samples;
- a separate publish-gate benchmark of 600 generated mixed 5v5/6v6 scoreboards with separately reported accuracy;
- optional real reviewed crops from `training/real_samples/<hero-id>/`.

These generated scenes simulate TV/laptop scaling, team colors, role gutters, blur, brightness, tint, moiré/scanlines, JPEG compression and partial portrait crops. They are a reproducible synthetic baseline, not a claim that 600 independent real screenshots were scraped from social media.

## Real-data improvement loop

The **Help train accurate detection** setting is off by default. After a reviewed scan, HeroLens can locally save only the cropped scoreboard and corrected portrait cells. Nothing is uploaded automatically. Samples can be exported as a ZIP or deleted from Settings.

Licensed YOLO datasets can be converted into HeroLens crops with:

```bash
python training/import_yolo_dataset.py /path/to/exported-dataset
```

Only use datasets and images whose licenses permit your intended use. Public Overwatch datasets were reviewed, but many focus on full gameplay characters or generic hero boxes rather than scoreboard portrait cells, so importing them blindly can reduce scoreboard accuracy.

## Train the model on GitHub

1. Open **Actions**.
2. Select **Train Hero AI Model**.
3. Select **Run workflow**.
4. Keep the defaults: 20 variants per hero, 7 epochs and 600 benchmark scoreboards.
5. The workflow trains, validates and commits `hero_classifier.onnx` to the repository.
6. That commit automatically triggers **Build Android APK**.
7. Download the newest `HeroLens-debug-apk` artifact.

If the model fails either validation gate, the workflow refuses to publish it.

## Build locally

Requirements:

- Android Studio / Android SDK 36
- JDK 17
- Internet access for the first Gradle sync

```powershell
.\gradlew.bat test assembleDebug
```

APK output:

```text
app\build\outputs\apk\debug\app-debug.apk
```

## Implemented

- Auto Scan, Picture Scan and Manual Selection
- Optional on-device neural classifier with dynamic batch inference
- Signature-matcher fallback
- TV/laptop scoreboard locator
- 5v5 and 6v6 geometry handling
- Fast partial-lineup recommendations
- Review/correct screens
- Camera zoom, focus, torch, exposure and full-sensor rotation
- Enemy-counter and ally-synergy explanations
- Map, rank, PC/console and personal hero-pool context
- Local scan history
- English and Arabic resources
- Opt-in local training-sample collection and ZIP export

## Accuracy note

Synthetic validation is not a substitute for a large, independently labeled real-device test set. Treat the V8 model as a trained beta until it is evaluated against hundreds of unseen Hisense TV, PS5, laptop and monitor captures. HeroLens is not affiliated with or endorsed by Blizzard Entertainment and contains no game-client hooks, memory readers, input automation or game binaries.
