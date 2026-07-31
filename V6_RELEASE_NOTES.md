# HeroLens V6 Coach — Release Notes

HeroLens V6 is a broad product upgrade focused on live-scan speed, scan reliability, explainable recommendations and a smoother first-run experience.

## Live scanner

- Fast, Balanced and Accurate scan modes.
- Multi-frame voting thresholds change with the selected mode.
- Automatic, portraits-left and portraits-right layouts.
- 1280×720 CameraX resolution selection with latest-frame-only analysis.
- Pinch zoom, zoom buttons, exposure compensation, torch, tap-to-focus and autofocus reset.
- Brightness, blur/detail and quality diagnostics.
- Automatic results opening can be disabled for review-first workflows.
- Hero correction dialog now exposes the top recognition alternatives.
- Persistent binary signature cache reduces preparation time after the first run.
- Concurrent portrait preparation with bounded concurrency.
- Matching now combines luminance, edges, colour histogram and perceptual hash, plus small crop offsets for imperfect phone framing.

## Recommendation coach

- Top-three picks remain explainable instead of presenting a single unexplained answer.
- Separate score contributions for matchup, synergy, map, comfort, composition, rank/input and switching cost.
- Detailed enemy-counter and ally-synergy explanations.
- Best-overall, hero-pool and safer-alternative labels.
- PC or console context.
- Switch timing based on current hero and ultimate charge.
- First-fight three-step playbook.
- Pick-specific risk warning.
- Team coverage comparison before and after switching.

## Experience

- Three-page onboarding.
- Persistent role, map, current hero, ultimate charge and hero-pool profile.
- Scan history increased to 50 entries.
- Improved small-screen scanner layout.
- Edge-to-edge Android presentation.
- English and Arabic resources retained.
- PWA settings and player profile persist between sessions.
- PWA adds View Transitions when supported and honours reduced-motion preferences.

## Important accuracy note

V6 substantially improves the experimental template matcher, but it is not a trained production object-detection model. It can still be affected by monitor glare, UI scaling, display colour, camera angle, motion and future game UI changes. The review/correction path remains required. A genuinely production-grade scanner requires a legally usable labelled scoreboard dataset and a trained on-device model.
