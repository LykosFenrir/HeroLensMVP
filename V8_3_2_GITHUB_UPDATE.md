# Upload V8.3.2 to GitHub

Extract the fix ZIP and upload its contents into the root of the HeroLensMVP repository, replacing matching files.

Files replaced:

- `training/train_hero_classifier.py`
- `.github/workflows/train-ai-model.yml`

Commit message:

`Train on complete 5v5 and 6v6 scoreboard crops`

Then run **Actions → Train Hero AI Model → Run workflow** using 20 / 36 / 8 / 600. When it succeeds, run **Build Android APK** manually.
