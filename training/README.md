# HeroLens neural classifier training

`train_hero_classifier.py` creates a 53-class MobileNetV3-Small ONNX model:
52 hero IDs plus `__unknown__`.

`train_scoreboard_detector.py` creates a separate compact anchor-free detector.
It consumes one localized scoreboard at `320x192`, detects up to 24 portrait boxes,
and exports pre-decoded `[center_x, center_y, width, height, confidence]` rows. The
Android app classifies all accepted boxes with the existing hero classifier in one
batch. This removes fixed portrait-column assumptions without asking a new detector
to relearn 52 hero identities from a small full-scoreboard dataset.

Default training uses 20 independent camera/TV augmentations per hero, producing
1,040 hero samples plus 220 unknown/background samples. It simulates perspective,
blur, brightness, tint, scanlines, JPEG compression, team-panel colors, role gutters
and partial portrait crops.

After training, a separate publish-gate benchmark generates 600 full 5v5 and 6v6
scoreboard scenes. The full-scoreboard validation floor is 65%. The independent
synthetic holdout and scoreboard-crop gates remain 72%, the combined scene benchmark
remains 62%, and both the 5v5 and 6v6 scene gates remain 58%. When reviewed real-TV
validation data is present, its best and selected-model gates both remain 65%.

This is a reproducible synthetic baseline, not a claim that 600 or 1,040 independent
real-world screenshots were scraped or collected.

Real reviewed crops are split so a capture used for training cannot also satisfy
the real-device publish gate:

```text
training/real_samples/train/<hero-id>/*.jpg
training/real_samples/validation/<hero-id>/*.jpg
```

The checked-in real-TV baseline was reviewed from two separate phone captures of
the same current 6v6 scoreboard. It contains 36 training crop variants and 36
held-out validation crops grouped into 12 slot decisions, all portrait cells only
(no player names), and covers Sierra, D.Va, Mercy, Genji, Widowmaker, Wrecking Ball,
Bastion, Junker Queen and Hanzo. Training repeats are augmented; validation images
remain unmodified. This small regression set prevents publishing a model that
passes generated scoreboards while failing the demonstrated TV-camera domain. It
is not presented as a statistically representative real-world accuracy score.

A legally licensed exported YOLO dataset can be converted with:

```bash
python training/import_yolo_dataset.py /path/to/yolo-dataset
```

The GitHub workflow **Train Hero AI Model** downloads the official portrait sources,
trains and validates both ONNX models, updates `hero_labels.txt`, and commits the
models to the selected branch. The normal Android workflow then runs when that commit
is on `main` or belongs to an open pull request. The detector has independent synthetic
IoU, recall, and precision gates; none of the classifier or 5v5/6v6 gates are lowered.

Public-data research found several CC BY 4.0 Overwatch datasets, including one with
about 2.4k images and 40 classes. Many public sets depict gameplay characters rather
than scoreboard portrait cells, or use one generic `hero` class. They must be audited
and mapped before importing; blindly training on them may hurt scoreboard recognition.
The app's opt-in reviewed crop export remains the preferred real-world data source.

## Stadium Competitive Draft is a separate domain

The normal full-scoreboard detector is not presented as a trained draft-grid
detector. Current blind-pick captures use the schema and leakage audit under
`training/stadium_draft/`; raw local captures and manifests are ignored by Git.
Run `audit_dataset.py --enforce-readiness` before starting any draft model work.
It requires current user-consented or explicitly licensed game-UI crops, disjoint
whole-match sessions, multiple validation devices, and enough revealed picks.
Synthetic or old snake-draft images cannot satisfy that real-screen readiness gate.
See `STADIUM_DRAFT_DATA_SOURCES.md` for the online source review.
