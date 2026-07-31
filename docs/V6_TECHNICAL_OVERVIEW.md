# V6 Technical Overview

## Android

- Kotlin and Jetpack Compose.
- CameraX Preview and ImageAnalysis.
- `STRATEGY_KEEP_ONLY_LATEST` prevents stale frame queues.
- `ResolutionSelector` requests a 16:9 1280×720 analysis stream with fallback.
- Bounded background portrait preparation and persistent signature caching.
- SharedPreferences persistence for scanner settings, profile and history.

## Recognition pipeline

1. Reject unusable frames using brightness, highlight ratio and edge detail.
2. Evaluate either the selected scoreboard layout or both layouts.
3. Crop ten portrait slots with small horizontal/vertical jitter.
4. Compare each crop using luminance correlation, edge correlation, colour-histogram intersection and a difference hash.
5. Prevent duplicate heroes within the same team where possible.
6. Combine recent frames and accept only heroes that meet the selected voting/confidence threshold.
7. Present confidence and correction choices before import.

## Recommendation pipeline

The recommendation engine uses readable, patch-versioned seed data. Each candidate accumulates contributions from:

- direct and trait-based enemy matchup;
- direct and composition-based ally synergy;
- map profile;
- saved comfort level;
- rank/input context;
- team coverage;
- current hero and ultimate economy.

The fit score is an ordering indicator, not a predicted win probability.
