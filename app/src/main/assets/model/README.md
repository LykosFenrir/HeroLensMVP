HeroLens loads model/hero_classifier.onnx and model/hero_labels.txt when present.

When model/scoreboard_detector.onnx is also present, HeroLens first detects portrait
boxes from the complete localized scoreboard, then classifies those boxes as one
batch. If the detector is absent, invalid, or uncertain, the existing geometry
profiles and portrait classifier remain the automatic fallback.
Run the GitHub Actions workflow "Train Hero AI Model" once. It creates and commits
both files automatically. Until then, the app uses the legacy template matcher.
