# HeroLens V8.3.2 — Full-scoreboard training fix

## Why V8.3.1 failed

The classifier reached 94.29% isolated-portrait validation and 73.08% row-crop validation, but the stricter mixed-layout benchmark scored 58.03% on 5v5 and 48.53% on 6v6. The model was being trained mostly on isolated portrait/row crops while the publish gate used crops taken after a complete scoreboard had been resized, blurred and compressed.

The earlier 80.1% result used the older single-layout benchmark, so it is not directly comparable to the new 5v5/6v6 benchmark.

## Fix

- Adds crops generated from complete 5v5 and 6v6 scoreboard scenes to the training set.
- Uses disjoint seeds for training, validation and the final 600-scene publish benchmark.
- Adds a full-scoreboard validation set and uses it heavily when selecting the best epoch.
- Keeps the existing quality gates. The thresholds were not lowered.
- Preserves support for arbitrary role combinations; team size and role composition remain independent.

## Workflow settings

Run `Train Hero AI Model` with:

- Synthetic variants per hero: `20`
- Domain-matched scoreboard crops per hero: `36`
- Epochs: `8`
- Benchmark scoreboards: `600`

The workflow internally adds 120 full-scoreboard training scenes per layout and 24 validation scenes per layout.
