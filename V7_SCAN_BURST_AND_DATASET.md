# HeroLens V7 — Scan Burst and Dataset Collector

V7 changes the scanner workflow from endless automatic template matching to an explicit capture-and-review flow:

1. Aim one large guide at the complete blue/red scoreboard.
2. Tap **Scan**.
3. Hold steady while HeroLens analyzes a fixed burst of 6, 9 or 12 frames.
4. Review detected allies and enemies.
5. Tap uncertain slots to correct them.
6. Choose your own ally row when useful.
7. Use the reviewed lineup for recommendations.

## Why this workflow

A short burst is faster to understand, avoids endless background classification, and creates a clear review point before a recommendation is trusted. The existing scoreboard locator remains active while aiming, but expensive hero classification runs only during the burst.

## Opt-in training samples

The new **Help train accurate detection** setting is disabled by default. When enabled, HeroLens saves a sample only after the user reviews a scan and taps **Use Results**.

Saved locally:

- one cropped scoreboard image;
- the detected portrait-cell crops;
- corrected hero IDs, team, slot and confidence;
- PC/console and TV/laptop metadata;
- detector geometry and app version.

Not saved:

- the full camera frame;
- the surrounding room;
- player account details;
- names or scoreboard statistics as structured data.

Nothing is uploaded automatically. Settings provides **Export ZIP** and **Delete** controls. Export uses Android FileProvider with temporary read permission.

## Model status

V7 still ships the experimental template matcher as a fallback. The collected, reviewed samples are intended to support training and validating a separate LiteRT/TensorFlow Lite detector in a future release. Model accuracy must be measured on held-out TV, laptop, PC and console captures before being called production-ready.
