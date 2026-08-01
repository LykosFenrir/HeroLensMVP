# Upload HeroLens V8.3 to GitHub

The V8.3 package includes the V8.2 five-row/six-row scanner corrections plus the
new mode-independent labels and mixed-role recommendation pool.

1. Extract the V8.3 fix ZIP.
2. Upload everything inside it into the repository root and overwrite matching files.
3. Keep your existing `app/src/main/assets/model/hero_classifier.onnx` until the new
   training run succeeds.
4. Commit with: `Add multi-layout scanning and mixed-role recommendations`.
5. Run **Train Hero AI Model** with the defaults:
   - portrait variants per hero: 20
   - scoreboard crops per hero: 36
   - epochs: 8
   - benchmark scoreboards: 600
6. When training is green, run **Build Android APK** manually.
7. Download and install the newest `HeroLens-debug-apk` artifact.

After installing:

- Leave **Settings > Scoreboard team size > Auto detect** for normal use.
- Use **Force 5v5** or **Force 6v6** only when automatic row counting is uncertain.
- On the Scan tab choose **Selected role** for role-locked modes or **All roles / mixed**
  when the mode permits flexible compositions.

Team size is determined from visible scoreboard rows, not from PC/console or the
name of the game mode.
