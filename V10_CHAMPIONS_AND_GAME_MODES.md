# HeroLens V10 — Champions and game-mode scanning

## Champions database

- A fourth bottom-navigation tab exposes all 52 heroes.
- Search ignores punctuation, spacing and diacritics (`dva`, `soldier76`, and `torbjorn` work).
- Role filters cover Tank, Damage and Support.
- Each guide explains the hero's archetype, positioning, engage plan, cooldown discipline and reset plan.
- Counter, threat and synergy cards explain both why a relationship matters and how to execute it.
- Authored relationships are distinguished from trait-inferred style matchups; neutral filler is never presented as a counter.
- Tapping a related hero opens that hero's guide, and Android Back returns to the catalog.

## Scanner compatibility

- Mode metadata: Auto, Unranked, Competitive + Hero Bans, Stadium, Stadium Competitive Draft, Arcade and Custom.
- Explicit two-team 3v3, 4v4, 5v5 and 6v6 slot layouts.
- Automatic structural row detection remains validated for 5v5 and 6v6.
- Auto, Stadium, Arcade and Custom profiles always keep the review screen open; only explicit Unranked or Competitive profiles may auto-import a complete high-confidence lineup.
- Auto layout now compares left- and right-side portraits instead of accepting the first minimally useful side.
- Sparse full-board detections are compared with geometry fallbacks instead of returning early.
- A six-row panel on either team correctly selects 6v6 unless the user explicitly forced another size.
- Training-sample metadata now records game mode and team size.

Arcade free-for-all and duplicate-hero scoreboards are not claimed as supported because the current analyzer models two unique-hero teams.

## Draft assistant

- Stadium Competitive Draft is a dedicated conservative 5v5 profile with automatic review and the 33-hero official roster snapshot dated 2026-08-07.
- Banned or otherwise unavailable heroes can be marked before analysis and are then hard-excluded from recommendations.
- Core Competitive is modeled as simultaneous ranked-choice Hero Ban voting, not a team-pick draft.
- Stadium Draft is modeled as blind simultaneous pair picks and reveal; Tanks occupy the fifth slot, mirror picks across opposing teams remain legal, and revealed picks can be entered after each round.
- Same-team duplicate picks, unavailable heroes, and lineups above the active 3v3–6v6 capacity are rejected or reconciled before analysis.
- Scan history restores the saved mode, unavailable heroes, roster policy, and legal team capacities before recalculating recommendations.
- Partial revealed picks can be imported when Stadium presents recognizable two-team panels; direct draft-grid recognition is not trained. Scan a two-team panel and correct uncertain slots, or enter revealed picks and unavailable heroes manually.
