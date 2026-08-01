# HeroLens V8.3 — Multi-layout and mixed-role update

## Why this update exists

Team size, platform and role rules are separate concepts. A console match can use
five or six rows, and different modes can use locked, flexible or unusual role
compositions. HeroLens must therefore inspect the scoreboard itself instead of
mapping a platform or mode name to a fixed format.

## Scanner changes

- Renamed the team-size choices to **Auto detect**, **Force 5v5** and **Force 6v6**.
- Removed UI and runtime wording that equated 6v6 with Open Queue or with PS5.
- Auto detection continues to compare five-row and six-row geometry using visible
  row structure only.
- PC/console remains optional scan metadata and a small recommendation context;
  it does not force team size, layout or role composition.
- The portrait detector does not reject unusual Tank/Damage/Support mixes.

## Recommendation changes

- Added **Recommendation pool**:
  - **Selected role**: compare heroes only from the chosen role.
  - **All roles / mixed**: compare every hero, useful when the mode permits role
    switching or mixed compositions.
- Hero-pool and current-hero selectors follow the selected recommendation pool.
- Scan history records whether a result used a selected role or all roles.

## Compatibility

Existing settings default to Auto detect and Selected role. Existing history and
trained ONNX model files remain compatible. Retraining is not required solely for
these rule and wording changes, although retraining remains useful after scanner
geometry or dataset updates.
