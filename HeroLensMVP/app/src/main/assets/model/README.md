# Camera model slot

Place a trained TensorFlow Lite or MediaPipe model here only after you have:

1. A legally usable image dataset.
2. Training examples for PC and console scoreboards, multiple resolutions and UI scales.
3. A held-out test set and confidence calibration.
4. A manual correction path for low-confidence detections.

Expected output: hero ID, ally/enemy side, slot number, and confidence.

The manual picker remains the safe fallback.
