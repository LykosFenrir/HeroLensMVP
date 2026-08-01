# HeroLens V8.2 — six-row recorded-sample fix (superseded)

V8.2 fixed a specific recording where a six-row scoreboard was being divided into
five taller rows. The recording happened to be from a console Open Queue match;
that was evidence about that image only, not a platform-to-team-size rule.

V8.3 makes this explicit: team size is detected from visible row structure and is
independent of platform and named game mode. See `V8_3_MULTI_MODE_LAYOUT_FIX.md`.
