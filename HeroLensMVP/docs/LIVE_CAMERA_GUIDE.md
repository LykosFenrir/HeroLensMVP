# HeroLens V4 — live automatic scan

## How it works

1. Select your role in HeroLens.
2. Tap **Open camera scanner**.
3. Turn the phone to landscape.
4. Open the Overwatch scoreboard.
5. Move closer until all ten hero portraits fit inside the guide boxes.
6. Hold steady. Do not press a capture or upload button.
7. The app locks the lineup after several matching frames and opens the recommendations automatically.

Tap the preview once if the monitor text or portraits look out of focus. Avoid strong reflections and make the scoreboard fill most of the camera view.

## Optional current-player row

Tap row 1–5 only when you want HeroLens to identify your current hero and account for ultimate-charge switching cost. Leaving it blank keeps the scan fully automatic and imports the first four stable allies.

## What V4 improves

- Continuous CameraX `ImageAnalysis`
- Latest-frame-only processing to prevent lag
- About three recognition passes per second
- Automatic left/right portrait-layout selection
- Dark, glare, and blur rejection
- Five-frame rolling history
- Three-frame minimum agreement per slot
- Automatic team import and recommendation display
- Tap-to-focus and tap-to-correct fallback

## Accuracy note

V4 uses multi-frame portrait matching. A production release still needs a trained on-device classifier using real, legally usable scoreboard examples from different displays, UI scales, resolutions, languages, camera angles, and lighting conditions.
