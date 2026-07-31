# HeroLens V8 release notes

## New scanner choices

- **Auto Scan:** four-frame quick scan with partial-lineup acceptance for faster recommendations during live matches.
- **Picture Scan:** take a photo or choose a screenshot, scan it once, review every slot and use the corrected result.
- **Manual Selection:** direct camera-free hero selection.

## Neural recognition

- Optional 53-class MobileNetV3-Small classifier: 52 heroes plus `__unknown__`.
- ONNX Runtime Android inference with dynamic batching.
- NNAPI acceleration is attempted on supported Android 8.1+ devices.
- Up to five jittered crops per portrait slot are classified in one batch per geometry profile.
- Existing signature matching remains available when the ONNX model is missing or unavailable.

## Training workflow

- New **Train Hero AI Model** GitHub Actions workflow.
- Default training creates 1,040 synthetic hero variants and 220 unknown/background examples.
- Separate publish-gate test creates 600 complete synthetic 5v5 scoreboards and evaluates 6,000 portrait cells.
- Model publication is blocked below 72% synthetic holdout accuracy or 62% full-scoreboard benchmark accuracy.
- Reviewed real crops exported from HeroLens can be added to `training/real_samples/<hero-id>/`.
- Licensed YOLO datasets can be converted with `training/import_yolo_dataset.py`.

## Camera and UX

- Auto mode begins after the scoreboard locator confirms both panels.
- Quick mode can use a partial lineup when at least three enemies and two allies are recognized.
- Picture Scan supports external camera capture and Android document/gallery selection.
- Picture overlays account for image letterboxing and source aspect ratio.
- All uncertain detections can be corrected before recommendations.
- Onboarding and app version metadata updated to V8.

## Important limitation

The source package does not pretend to include a trained production model. The GitHub training workflow must run once to generate and commit `hero_classifier.onnx`. Until then, V8 uses the existing template/signature fallback.
