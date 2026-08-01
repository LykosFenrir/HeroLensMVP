# HeroLens V8.3.1 — 6v6 benchmark crop fix

## Failure fixed

V8.3 could finish all eight training epochs and then fail while evaluating the generated 6v6 benchmark with:

```text
ValueError: Coordinate 'lower' is less than 'upper'
```

The 960×540 scene generator selected row heights before checking whether two six-row panels plus their gap fit inside the image. Some generated blue-team portrait boxes therefore started above the image and produced an invalid PIL crop.

## Changes

- Calculates a safe row-height range from the available canvas height.
- Keeps both 5v5 and 6v6 panels fully inside the benchmark image.
- Adds defensive crop-coordinate clamping so every crop is at least 1×1 pixels.
- Does not reduce or bypass any accuracy gate.

## Validation

- Python syntax compilation passed.
- A 600-scene-equivalent smoke test processed 3,000 5v5 crops and 3,600 6v6 crops without an invalid coordinate.

## GitHub update

Replace only:

```text
training/train_hero_classifier.py
```

Then rerun **Train Hero AI Model** with the same values:

```text
Portrait variants: 20
Scoreboard crops per hero: 36
Epochs: 8
Benchmark scoreboards: 600
```
