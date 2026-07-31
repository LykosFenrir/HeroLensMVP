# Camera recognition testing

## Android

1. Build and install the debug app.
2. Open Overwatch and hold the scoreboard open.
3. In HeroLens, tap **Open camera scanner**.
4. Select **Portraits left** or **Portraits right** to match the scoreboard.
5. Point the phone at the monitor in landscape framing. Make the monitor fill the 16:9 preview and align every hero portrait inside a colored box.
6. Tap **Scan scoreboard automatically**.
7. The first scan needs internet access to download and cache hero portrait templates. Later scans use the cache.
8. Review all ten detected heroes and their confidence values.
9. Tap a wrong or unknown row to correct it manually.
10. Select which ally row is your own highlighted scoreboard row.
11. Tap **Use detected teams**, then analyze the picks.

## Browser prototype

1. Open `prototype/index.html` in Chrome or Samsung Internet.
2. Under **Automatic camera scan**, take or choose a landscape scoreboard photo.
3. Choose the portrait side and your own ally row.
4. Confirm that the overlaid boxes sit on the portrait locations.
5. Tap **Recognize heroes from photo**.
6. Review the auto-filled manual selections before analyzing.

Some browsers block reading remote portrait pixels because of cross-origin rules. When that occurs, use the Android scanner.

## Accuracy expectations

The current recognizer is an experimental image-signature baseline. It is designed to prove the camera-to-team-selection workflow, not to claim production accuracy. Results are most reliable when:

- the scoreboard uses the standard 5v5 layout;
- the monitor fills the guide;
- the phone is level and nearly perpendicular to the display;
- glare, blur and moiré are minimal;
- UI scaling and color settings are close to the standard layout.

The next production step is to collect legally usable, labeled scoreboard crops and train a classifier with an explicit unknown class. The existing `HeroDetector` interface allows that model to replace the template detector without rewriting the camera UI.
