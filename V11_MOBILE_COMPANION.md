# HeroLens V11 — Mobile Match Companion

HeroLens V11 reorganizes the Android app around a complete Overwatch session instead of treating scoreboard scanning as the whole product.

## Mobile-first loop

- **Home:** current competitive context, an active coaching plan, the four session phases, and recent saved sessions.
- **Match:** pre-match and draft setup through automatic camera scan, reviewed camera scan, picture scan, or manual lineup entry.
- **Live Coach:** explainable pick or switch guidance, a first-fight playbook, risks, team coverage, and alternative comparisons.
- **Heroes:** offline how-to-play guides, counters, threats, synergies, and coordination details for the full catalog.
- **Review:** private on-device coaching sessions that restore mode, bans, current hero, teams, scan confidence, and team size.

## Honest product boundary

The app is a second-screen companion. It does not inject an overlay into Overwatch, control the game, or claim access to live Blizzard account statistics. “Live” means a user-reviewed coaching plan kept open on the phone during play. Saved sessions currently contain lineup analysis, not eliminations, damage, healing, win/loss, or other post-match telemetry.

## Existing scanner guarantees retained

The V11 navigation change does not lower model gates or bypass review. Automatic row detection remains validated for standard 5v5 and 6v6 scoreboards. Experimental modes remain labeled, and uncertain or incomplete detections remain editable before import.
