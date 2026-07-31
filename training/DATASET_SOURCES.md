# Public dataset review

This note records the public sources reviewed for V8. No social-media images are
silently scraped by the project.

## Potentially useful

- **Overwatch / RickCs Workspace (Roboflow Universe)** — about 2.4k images, 40
  character/object classes, CC BY 4.0. Useful for older hero visual diversity, but
  samples appear oriented toward gameplay character detection rather than scoreboard
  portrait cells.
- **Overwatch 2 Character Detection / AppliedRobotics** — about 1.58k images and
  approximately 51 classes. Requires a license/content audit before use.
- **overwatch2 / overwatch1** — about 10k images and many legacy classes. Primarily
  older Overwatch/gameplay detection; class mapping and license review required.

## Not directly suitable

- **overwatch_hero / diyworld** — 17.2k images but only one generic `hero` class;
  cannot identify which hero is shown.
- Several 31k-image datasets classify only generic objects such as `Hero`, `Mega`
  and `Pack`; they do not provide hero identity labels.
- Small six-class or legacy-only datasets do not cover the current roster.

## Import policy

1. Confirm the dataset license and attribution requirements.
2. Inspect whether bounding boxes are actual scoreboard portraits.
3. Map class names to current HeroLens IDs.
4. Import with `training/import_yolo_dataset.py`.
5. Keep a separate real-device validation set that is never used for training.
6. Do not claim production accuracy from synthetic or in-domain training metrics.
