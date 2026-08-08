#!/usr/bin/env python3
"""Audit privacy, licensing and split integrity for real Stadium Draft screens."""

from __future__ import annotations

import argparse
import json
from collections import Counter, defaultdict
from pathlib import Path

VALID_SPLITS = {"train", "validation"}
VALID_TEAMS = {"ally", "enemy"}
VALID_STATES = {"locked", "active", "pending", "empty"}
CURRENT_UI_REVISION = "blind-pick-2025-11+"
SUPPORTED_IMAGES = {".jpg", ".jpeg", ".png", ".webp"}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--enforce-readiness", action="store_true")
    return parser.parse_args()


def fail(errors: list[str], message: str) -> None:
    errors.append(message)


def valid_box(value: object) -> bool:
    if not isinstance(value, list) or len(value) != 4:
        return False
    if not all(isinstance(number, (int, float)) for number in value):
        return False
    x, y, width, height = (float(number) for number in value)
    return (
        0.0 <= x <= 1.0
        and 0.0 <= y <= 1.0
        and 0.0 < width <= 1.0
        and 0.0 < height <= 1.0
        and x + width <= 1.000001
        and y + height <= 1.000001
    )


def audit(manifest_path: Path, enforce_readiness: bool = False) -> dict[str, object]:
    errors: list[str] = []
    payload = json.loads(manifest_path.read_text(encoding="utf-8"))
    if payload.get("schemaVersion") != 1:
        fail(errors, "schemaVersion must be 1")
    if not str(payload.get("datasetId", "")).strip():
        fail(errors, "datasetId is required")
    samples = payload.get("samples")
    if not isinstance(samples, list):
        raise ValueError("samples must be a list")

    ids: set[str] = set()
    session_splits: dict[str, set[str]] = defaultdict(set)
    split_sessions: dict[str, set[str]] = defaultdict(set)
    split_devices: dict[str, set[str]] = defaultdict(set)
    revealed = Counter()
    current_revealed = Counter()
    file_count = Counter()
    root = manifest_path.parent.resolve()

    for index, sample in enumerate(samples):
        label = f"samples[{index}]"
        if not isinstance(sample, dict):
            fail(errors, f"{label} must be an object")
            continue
        sample_id = str(sample.get("id", "")).strip()
        if not sample_id:
            fail(errors, f"{label}.id is required")
        elif sample_id in ids:
            fail(errors, f"duplicate sample id: {sample_id}")
        ids.add(sample_id)

        split = str(sample.get("split", "")).strip().lower()
        session_id = str(sample.get("sessionId", "")).strip()
        device_id = str(sample.get("deviceId", "")).strip()
        if split not in VALID_SPLITS:
            fail(errors, f"{label}.split must be train or validation")
        if not session_id:
            fail(errors, f"{label}.sessionId is required")
        if not device_id:
            fail(errors, f"{label}.deviceId is required")
        if split in VALID_SPLITS and session_id:
            session_splits[session_id].add(split)
            split_sessions[split].add(session_id)
        if split in VALID_SPLITS and device_id:
            split_devices[split].add(device_id)

        if sample.get("mode") != "STADIUM_DRAFT" or sample.get("teamSize") != 5:
            fail(errors, f"{label} must be a 5v5 STADIUM_DRAFT sample")
        if sample.get("privacy") != "game-ui-crop-only":
            fail(errors, f"{label}.privacy must be game-ui-crop-only")
        if not str(sample.get("source", "")).strip():
            fail(errors, f"{label}.source is required")
        if not str(sample.get("license", "")).strip():
            fail(errors, f"{label}.license is required")
        if split == "train" and sample.get("trainingAllowed") is not True:
            fail(errors, f"{label} cannot enter train without explicit trainingAllowed=true")
        if sample.get("coordinateFormat") != "normalized-xywh":
            fail(errors, f"{label}.coordinateFormat must be normalized-xywh")
        resolution = sample.get("resolution")
        if (
            not isinstance(resolution, list)
            or len(resolution) != 2
            or not all(isinstance(number, int) and number > 0 for number in resolution)
        ):
            fail(errors, f"{label}.resolution must contain positive pixel width and height")
        for required_label in ("patch", "platform", "language"):
            if not str(sample.get(required_label, "")).strip():
                fail(errors, f"{label}.{required_label} is required")

        relative = Path(str(sample.get("path", "")))
        if relative.is_absolute() or ".." in relative.parts:
            fail(errors, f"{label}.path must stay inside the dataset directory")
        elif relative.suffix.lower() not in SUPPORTED_IMAGES:
            fail(errors, f"{label}.path must be jpg, png or webp")
        else:
            resolved = (root / relative).resolve()
            if root not in resolved.parents:
                fail(errors, f"{label}.path escapes the dataset directory")
            if resolved.is_file():
                file_count[split] += 1
            elif enforce_readiness:
                fail(errors, f"missing image: {relative.as_posix()}")

        ui_revision = str(sample.get("uiRevision", "")).strip()
        slots = sample.get("slots")
        if not isinstance(slots, list):
            fail(errors, f"{label}.slots must be a list")
            continue
        seen_slots: set[tuple[str, int]] = set()
        for slot_index, slot in enumerate(slots):
            slot_label = f"{label}.slots[{slot_index}]"
            if not isinstance(slot, dict):
                fail(errors, f"{slot_label} must be an object")
                continue
            team = str(slot.get("team", "")).lower()
            number = slot.get("slot")
            state = str(slot.get("state", "")).lower()
            hero_id = slot.get("heroId")
            if team not in VALID_TEAMS or not isinstance(number, int) or number not in range(5):
                fail(errors, f"{slot_label} needs ally/enemy and slot 0..4")
                continue
            key = (team, number)
            if key in seen_slots:
                fail(errors, f"{slot_label} duplicates {team} slot {number}")
            seen_slots.add(key)
            if state not in VALID_STATES:
                fail(errors, f"{slot_label}.state is invalid")
            if not valid_box(slot.get("box")):
                fail(errors, f"{slot_label}.box must be normalized xywh")
            if state in {"locked", "active"}:
                if not isinstance(hero_id, str) or not hero_id.strip():
                    fail(errors, f"{slot_label}.heroId is required for a {state} card")
                elif state == "locked" and split in VALID_SPLITS:
                    revealed[split] += 1
                    if ui_revision == CURRENT_UI_REVISION:
                        current_revealed[split] += 1
                if not valid_box(slot.get("portraitBox")):
                    fail(errors, f"{slot_label}.portraitBox must be normalized xywh for {state}")
            elif hero_id not in (None, ""):
                fail(errors, f"{slot_label}.heroId must be empty unless locked or active")

        ignore = sample.get("ignore", [])
        if not isinstance(ignore, list):
            fail(errors, f"{label}.ignore must be a list")
        else:
            for ignore_index, region in enumerate(ignore):
                region_label = f"{label}.ignore[{ignore_index}]"
                if not isinstance(region, dict) or not str(region.get("kind", "")).strip():
                    fail(errors, f"{region_label}.kind is required")
                elif not valid_box(region.get("box")):
                    fail(errors, f"{region_label}.box must be normalized xywh")

    leaked = sorted(session for session, splits in session_splits.items() if len(splits) > 1)
    if leaked:
        fail(errors, "session leakage across train/validation: " + ", ".join(leaked))

    if enforce_readiness:
        readiness = {
            "train sessions": (len(split_sessions["train"]), 6),
            "validation sessions": (len(split_sessions["validation"]), 4),
            "validation devices": (len(split_devices["validation"]), 2),
            "current train locked picks": (current_revealed["train"], 60),
            "current validation locked picks": (current_revealed["validation"], 40),
        }
        for name, (actual, minimum) in readiness.items():
            if actual < minimum:
                fail(errors, f"{name}: {actual} < required {minimum}")

    summary: dict[str, object] = {
        "datasetId": payload.get("datasetId"),
        "samples": len(samples),
        "sessions": {split: len(split_sessions[split]) for split in sorted(VALID_SPLITS)},
        "devices": {split: len(split_devices[split]) for split in sorted(VALID_SPLITS)},
        "lockedPicks": {split: revealed[split] for split in sorted(VALID_SPLITS)},
        "currentUiLockedPicks": {split: current_revealed[split] for split in sorted(VALID_SPLITS)},
        "filesPresent": {split: file_count[split] for split in sorted(VALID_SPLITS)},
        "ready": not errors and enforce_readiness,
        "errors": errors,
    }
    return summary


def main() -> None:
    args = parse_args()
    summary = audit(args.manifest, args.enforce_readiness)
    print(json.dumps(summary, indent=2))
    if summary["errors"]:
        raise SystemExit("Stadium Draft dataset audit failed")


if __name__ == "__main__":
    main()
