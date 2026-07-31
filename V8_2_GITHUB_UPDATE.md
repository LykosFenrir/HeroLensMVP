# Upload HeroLens V8.2 to GitHub

Upload the contents of the fix-only ZIP into the repository root and overwrite the
matching files. Do not delete `app/src/main/assets/model/hero_classifier.onnx`.

After committing:

1. Open **Settings > Scoreboard team size** in HeroLens and use **6v6 / Open Queue**
   for the first PS5 retest. Auto mode remains available after validation.
2. Run **Actions > Train Hero AI Model** with:
   - Synthetic variants per hero: 20
   - Scoreboard crops per hero: 36
   - Epochs: 8
   - Benchmark scoreboards: 600
3. Confirm the log reports all three metrics:
   - `scoreboard_benchmark_5v5_accuracy`
   - `scoreboard_benchmark_6v6_accuracy`
   - `scoreboard_benchmark_accuracy`
4. After training succeeds and commits the new ONNX model, manually run
   **Actions > Build Android APK**.
5. Install the new APK and retest the same PS5 Open Queue scoreboard.
