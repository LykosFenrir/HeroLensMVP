# HeroLens MVP

HeroLens is a mobile-first, on-device Overwatch match companion. It supports the full session loop: pre-match draft and lineup setup, a glanceable Live Coach, saved coaching-session review, and offline hero intelligence for all 52 catalog heroes.

Current V12 capabilities include:

- a mobile companion dashboard for pre-match, live-coach, review and hero-guide workflows;
- live camera, picture and manual lineup input;
- ONNX hero classification plus a full-scoreboard geometry detector and signature fallback;
- explicit two-team 3v3, 4v4, 5v5 and 6v6 layouts;
- Unranked, Competitive, Stadium, Arcade and Custom mode tagging with conservative review policies;
- searchable hero guides with play plans, counters, threats and synergies;
- private, on-device saved coaching sessions that can be reopened with their mode, bans and team size;
- offline post-match OCR with player review, private BattleTag row matching, actual E/A/D, damage, healing and mitigation history, and on-device performance aggregates;
- opt-in, local-only reviewed training samples with cropped scoreboard data and no automatic upload.

Automatic row detection is currently validated for standard 5v5 and 6v6 scoreboards. Current Stadium Competitive Draft photos can be reviewed as partial five-slot lineups even when automatic geometry misses, but automatic draft-grid recognition remains gated on a multi-session current blind-pick dataset. Forced 3v3/4v4, Stadium, Arcade and Custom layouts remain experimental; free-for-all and duplicate-hero modes are not yet supported.

The standard cropped-scoreboard collector does not export Stadium Draft review images. Draft training remains disabled until a separate privacy-masked collector and independently licensed, session-grouped dataset satisfy the readiness audit.

HeroLens does not inject into the game or automate gameplay. Live Coach is a player-controlled second-screen plan built from reviewed lineup information.

See [V12_PRIVATE_STATS_AND_DRAFT_DATA.md](V12_PRIVATE_STATS_AND_DRAFT_DATA.md), [V11_MOBILE_COMPANION.md](V11_MOBILE_COMPANION.md), [V10_CHAMPIONS_AND_GAME_MODES.md](V10_CHAMPIONS_AND_GAME_MODES.md), [V9_FULL_SCOREBOARD_DETECTOR.md](V9_FULL_SCOREBOARD_DETECTOR.md), and [training/README.md](training/README.md) for implementation and model details.
