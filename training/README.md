# HeroLens neural classifier training

`train_hero_classifier.py` creates a 53-class MobileNetV3-Small ONNX model:
52 hero IDs plus `__unknown__`.

Default training uses 20 independent camera/TV augmentations per hero, producing
1,040 hero samples plus 220 unknown/background samples. It simulates perspective,
blur, brightness, tint, scanlines, JPEG compression, team-panel colors, role gutters
and partial portrait crops.

After training, a separate publish-gate benchmark generates 600 full 5v5 scoreboard
scenes and evaluates all 6,000 known portrait cells. The model is not published when
holdout accuracy is below 72% or the full-scoreboard benchmark is below 62%.

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
trains, validates, exports `hero_classifier.onnx`, updates `hero_labels.txt`, commits
the model to the repository and triggers the normal APK build.

Public-data research found several CC BY 4.0 Overwatch datasets, including one with
about 2.4k images and 40 classes. Many public sets depict gameplay characters rather
than scoreboard portrait cells, or use one generic `hero` class. They must be audited
and mapped before importing; blindly training on them may hurt scoreboard recognition.
The app's opt-in reviewed crop export remains the preferred real-world data source.
