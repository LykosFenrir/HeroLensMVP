# HeroLens V6.1 TV Scan Fix

This update addresses field-testing feedback from a Samsung phone pointed at a TV:

- Scanner requests sensor landscape and rebuilds the CameraX preview after rotation.
- Preview uses FIT_CENTER so the analysis frame and overlay share the same 16:9 area.
- The Android system Back button now exits the scanner.
- An always-visible EXIT SCAN control is shown over the preview.
- Landscape no longer leaves the portrait control panel below the camera.
- The scanner tries both TV/full-screen and close-crop scoreboard geometry profiles.
- The primary TV profile moves hero boxes to the actual far-left/far-right scoreboard portrait columns.
- Wider crop jitter handles TV bezels, UI scaling and minor framing error.
- Portrait templates are alpha-trimmed and background-normalized for better matching against TV scoreboard icons.
- Template cache version was changed so the improved signatures regenerate automatically.

This remains a template-matching detector. It is materially more tolerant of TV capture, but a trained model is still required for production-level accuracy across every display and camera condition.
