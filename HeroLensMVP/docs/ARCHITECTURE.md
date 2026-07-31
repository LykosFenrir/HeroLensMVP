# Architecture

## Current MVP

```text
Compose UI
  ├── setup state
  ├── hero picker
  ├── player preferences
  └── recommendation cards
          │
          ▼
RecommendationEngine (pure Kotlin)
  ├── direct counter seed
  ├── generic trait interactions
  ├── ally synergy
  ├── map fit
  ├── player comfort
  └── switching cost
          │
          ▼
HeroCatalog (versioned static seed)
```

The engine has no Android dependency. It can be tested on the JVM, reused by a backend, or migrated to Kotlin Multiplatform.

## Production target

```text
CameraX frame
  → scoreboard detector
  → perspective-normalized crop
  → slot classifier / portrait embedding model
  → confidence threshold and correction UI
  → detected MatchContext
  → RecommendationEngine

Remote patch service
  → signed version manifest
  → hero catalog + matchup weights + map metadata
  → local validation
  → atomic Room database update
```

## Suggested modules after MVP

- `:app` — Compose UI and dependency injection
- `:domain` — models and recommendation use cases
- `:data-local` — Room and DataStore
- `:data-remote` — signed patch configuration client
- `:vision` — CameraX and on-device inference
- `:benchmark` — macrobenchmark and model latency tests

## Security and privacy defaults

- Inference on-device by default
- No screenshot upload without explicit opt-in
- No game-process access
- No input automation
- Signed patch payloads
- DataStore encryption for account-linked preferences if accounts are added
- Analytics disabled until consent is captured
