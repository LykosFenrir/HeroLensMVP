# Stadium Draft source review (August 2026)

HeroLens does not silently scrape social media, streams, or YouTube frames.

## Authoritative layout and rules references

- Blizzard's current Stadium page describes 5v5 Draft Mode and the live Stadium
  roster: <https://overwatch.blizzard.com/en-us/stadium/>
- Blizzard's November 2025 patch notes changed Stadium to simultaneous blind pick
  and assign tanks to slot five: <https://overwatch.blizzard.com/en-gb/news/patch-notes/live/2025/11/>
- Blizzard-hosted Season 18 launch artwork shows the two five-slot team columns and
  hero roster, but predates blind pick. It is a layout reference only:
  <https://bnetcmsus-a.akamaihd.net/cms/gallery/vg/VGQCD8D9IZ3C1756140035453.png>

## Online datasets reviewed

- Roboflow Universe has several CC BY 4.0 Overwatch object-detection sets. The
  reviewed projects depict gameplay characters and cover incomplete/older rosters;
  they are not Stadium draft-grid or scoreboard-cell datasets.
- Public Kaggle match-stat tables can seed aggregate UI tests, but they contain no
  screen pixels and cannot train the scanner.
- News sites, Twitch thumbnails, Reddit posts and YouTube videos expose useful edge
  cases, but ordinary copyright applies unless the owner grants training rights.
  They may be inspected locally for failure analysis; they are not copied into the
  repository or counted toward a publish gate.

## Promotion policy

Use current, user-consented game-UI crops or explicitly licensed images. Hold out
whole matches/sessions and at least two capture devices. Old snake-draft artwork,
synthetic scenes, adjacent video frames and the user's existing normal-scoreboard
recording cannot satisfy the current blind-pick real-screen gate.
