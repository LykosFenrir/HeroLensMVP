# HeroLens V6.2 Scanner Rebuild

V6.2 replaces the remaining fixed-coordinate scanner assumptions discovered during real TV testing.

## Fixed from field testing

- The scanner now uses full-sensor orientation and can move between portrait and either landscape direction while it is open.
- CameraX preview and analysis target rotation are refreshed when the display rotates.
- Pinch zoom is handled directly by `PreviewView`, with visible minus, plus and Frame controls as fallback.
- Automatic framing locates the scoreboard first and zooms until its blue/red panels occupy most of the preview.
- The ten/twelve overlay boxes are generated from the detected scoreboard, rather than being anchored to a screen edge.
- Blue/red panel localisation rejects non-scoreboard screens and tolerates TV bezels, distance and off-centre framing.
- Both 5v5 and 6v6 scoreboards are evaluated.
- Hero matching now uses three portrait crops per hero, a fast shortlist and reduced geometry search so live analysis can finish on a phone.
- A new V6.2 signature cache is generated automatically; old V6.1 detector signatures are not reused.
- Exit Scan and Android Back continue to leave the scanner safely.

## Validation performed

The scoreboard locator was exercised against the user's real TV-camera captures. It located the scoreboard in the upright gameplay captures, rejected a non-scoreboard menu capture, and located the sideways capture after orientation normalisation. The pure Kotlin locator, signature, detector and stabiliser sources compile in a JVM smoke build.

## Remaining limitation

Recognition is still an experimental on-device template matcher. It is now properly localised and substantially faster, but a production neural detector still requires a legally usable labelled scoreboard dataset and measured validation accuracy.
