# HeroLens V8.2 — PS5 Open Queue / 6v6 fix

## What the recording proved

The PS5 Open Queue scoreboard contained six rows per team, including a possible
`WAITING FOR PLAYER` row. V8.1 could divide that panel into five taller rows and
still receive confident neural predictions. Every crop below the first row then
became vertically shifted.

## Scanner changes

- Added structural five-row versus six-row detection using repeated role/status
  glyph bands near the scoreboard edges.
- Added a persistent **Scoreboard team size** setting:
  - Auto
  - 5v5
  - 6v6 / Open Queue
- A forced 6v6 setting is passed to Auto Scan and Picture Scan and prevents the
  detector from falling back to five rows.
- Removed the old preference penalty against 6v6 results.
- Added upper-core neural crops for console portraits whose lower half is covered
  by a progression/level badge.
- Replaced single-crop maximum scoring with multi-crop consensus, reducing one
  confidently wrong crop from overriding the other views.
- Picture Scan now requests camera permission before launching Take Picture,
  preventing the first-use permission crash seen on the Galaxy S25 Ultra.

## Training changes

- Synthetic console portraits now include large progression-badge obstructions.
- Full-scoreboard publish testing is split between 5v5 and 6v6 scenes.
- Model publication requires:
  - combined benchmark accuracy >= 62%;
  - 5v5 benchmark accuracy >= 58%;
  - 6v6 benchmark accuracy >= 58%.

## Validation completed

- Pure Kotlin vision sources compiled successfully.
- The structural estimator selected 6v6 on the supplied PS5 Open Queue frame.
- The same estimator selected 5v5 on the supplied standard PC scoreboard image.
- Python training source passed syntax checks.
- Synthetic scene smoke tests returned 10 portrait boxes for 5v5 and 12 for 6v6.

A real Android build and final PS5 recognition accuracy must still be verified by
GitHub Actions and another phone test after retraining the ONNX model.
