# HeroLens V8.3.1 fix-only upload

Upload the contents of this ZIP into the root of the HeroLensMVP GitHub repository and overwrite the matching file.

This changes only `training/train_hero_classifier.py`. It fixes the invalid 6v6 benchmark crop that caused `Coordinate 'lower' is less than 'upper'` after training.
