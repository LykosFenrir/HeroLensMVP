# HeroLens V6.4 confidence and portrait geometry correction

V6.4 is based on the real-device screenshots captured from the user's ASUS ROG Strix G15 and Hisense/PS5 testing.

## What the screenshots proved

- The blue/red scoreboard locator and CameraX zoom now work.
- V6.3 could still divide a 5v5 table as 6v6.
- Some portrait boxes started in the header or landed on the role-icon gutter.
- Neutral image correlations were incorrectly displayed around 50%, allowing random wrong heroes to stabilize.

## V6.4 changes

1. Searches 40 portrait geometries per layout/team size, including wider horizontal offsets and panel-header trims.
2. Uses a cheap texture/hash pre-pass and fully classifies only the strongest four geometries.
3. Calibrates hero confidence from positive evidence instead of mapping correlation zero to 50%.
4. Requires a meaningful lead over the second-best hero before accepting a slot.
5. Raises multi-frame confidence requirements and prefers 5v5 unless a 6v6 result has strong evidence.
6. Hides the duplicate in-preview exit control in portrait so it no longer covers ally rows.
7. Rebuilds the signature cache under a V6.4 cache key.

## Validation performed in this environment

- The modified detector, locator, signature math and stabilizer compile together with the Kotlin compiler.
- The existing colour-order and locator logic was retained from the V6.3 build that reached real-device scoreboard localization.
- Candidate geometry now covers header-trimmed and role-gutter layouts observed in the supplied screenshots.

A physical Hisense/PS5 and ASUS camera test is still required for final classification validation because this environment cannot operate those devices or access the app's downloaded runtime portrait cache.
