# Stadium Competitive Draft data

HeroLens treats the Stadium draft grid as a separate vision domain. The normal
scoreboard detector is not relabeled or promoted as a draft detector.

Local captures belong in `training/stadium_draft/local/` and are ignored by Git.
Copy `manifest.example.json` to `manifest.json`, then describe only reviewed,
game-UI crops. Do not include a room, face, player chat, credentials, or unrelated
screen content. Adjacent frames from one match must share one `sessionId`, so the
auditor can prevent train/validation leakage.

Run a structural audit with:

```bash
python training/stadium_draft/audit_dataset.py \
  --manifest training/stadium_draft/manifest.json
```

`--enforce-readiness` additionally requires the files, explicit training rights,
at least six train sessions, four held-out validation sessions, two validation
devices, 60 locked train picks and 40 locked validation picks. Every team card is
boxed and labeled `locked`, `active`, `pending`, or `empty`; the selectable roster,
player names and chat are explicit ignore regions. These are data
readiness checks, not an accuracy claim. A future draft model must add its own
held-out real-screen accuracy gates before it can ship.

Current Blizzard rules use 5v5 blind pick with tanks assigned to the fifth slot.
Use the `blind-pick-2025-11+` UI revision; older launch snake-draft screenshots
may be kept as `validation-only` references but must not satisfy the current gate.
