# V8 AI + Three-Mode Scanner

## Goals

V8 addresses the two biggest V7 limitations:

1. template matching was too inconsistent across screen types;
2. live scanning could take too long during an active match.

## User modes

- **Auto Scan:** short four-frame capture, partial-lineup acceptance, fastest path to recommendations.
- **Picture Scan:** external camera or gallery image, full review and correction.
- **Manual Selection:** camera-free fallback.

## AI architecture

1. Locate the blue/red scoreboard panels.
2. Evaluate candidate 5v5/6v6 portrait geometry.
3. Batch up to five jittered crops for every slot.
4. Run one local ONNX inference batch per candidate geometry.
5. Apply uniqueness, confidence and margin checks.
6. Confirm across frames in Auto Scan.
7. Show a correction screen whenever confidence is insufficient.

The neural classifier is optional. When the ONNX asset is absent, HeroLens automatically uses the existing template/signature detector.

## Training and validation

The default GitHub training workflow generates 1,040 per-hero samples plus unknowns and evaluates the resulting model on 600 generated complete 5v5 scoreboard scenes (6,000 portrait cells). It refuses to commit the model if:

- synthetic holdout accuracy is below 72%; or
- full-scoreboard synthetic crop accuracy is below 62%.

Real reviewed app exports can be added under `training/real_samples/<hero-id>/` and are included in the next training run.

## Privacy

- No full-room camera image is uploaded automatically.
- Dataset collection is opt-in.
- Only cropped scoreboard/portrait data is stored locally after user review.
- Users can export or delete all collected samples.
