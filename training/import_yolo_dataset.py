#!/usr/bin/env python3
"""Import licensed YOLO annotations as reviewed HeroLens portrait crops.

This tool does not download or scrape anything. Point it at an exported dataset
that you are legally allowed to use. Bounding boxes whose class names map to a
HeroLens hero ID are cropped into training/real_samples/<hero-id>/.
"""
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_OUTPUT = ROOT / "training/real_samples"
IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp", ".bmp"}


def canonical(value: str) -> str:
    value = value.strip().lower().replace("d.va", "dva").replace("soldier: 76", "soldier-76")
    value = re.sub(r"[^a-z0-9]+", "-", value).strip("-")
    aliases = {
        "ball": "wrecking-ball",
        "hammond": "wrecking-ball",
        "junkerqueen": "junker-queen",
        "life-weaver": "lifeweaver",
        "lucio": "lucio",
        "rammatra": "ramattra",
        "soldier76": "soldier-76",
        "torb": "torbjorn",
        "torbjron": "torbjorn",
        "widow": "widowmaker",
    }
    return aliases.get(value, value)


def read_class_names(dataset: Path) -> list[str]:
    for candidate in (dataset / "data.yaml", dataset / "data.yml"):
        if not candidate.exists():
            continue
        text = candidate.read_text(errors="ignore")
        inline = re.search(r"(?m)^names:\s*\[(.*?)\]\s*$", text)
        if inline:
            return [item.strip().strip("'\"") for item in inline.group(1).split(",")]
        block = re.search(r"(?ms)^names:\s*\n((?:\s+\d+:.*\n?)+)", text)
        if block:
            pairs = []
            for line in block.group(1).splitlines():
                match = re.match(r"\s*(\d+):\s*(.*)", line)
                if match:
                    pairs.append((int(match.group(1)), match.group(2).strip().strip("'\"")))
            return [name for _, name in sorted(pairs)]
    for candidate in dataset.rglob("classes.txt"):
        return [line.strip() for line in candidate.read_text().splitlines() if line.strip()]
    raise SystemExit("Could not find class names in data.yaml/data.yml/classes.txt")


def find_label(image: Path) -> Path | None:
    candidates = [
        image.with_suffix(".txt"),
        Path(str(image).replace("/images/", "/labels/")).with_suffix(".txt"),
        Path(str(image).replace("\\images\\", "\\labels\\")).with_suffix(".txt"),
    ]
    return next((path for path in candidates if path.exists()), None)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("dataset", type=Path, help="Root of an exported YOLO dataset")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--minimum-pixels", type=int, default=20)
    parser.add_argument("--padding", type=float, default=0.08)
    parser.add_argument("--limit", type=int, default=0, help="Maximum imported crops; 0 means no limit")
    args = parser.parse_args()

    catalog = set(json.loads((ROOT / "app/src/main/assets/hero_portraits.json").read_text()).keys())
    names = read_class_names(args.dataset)
    class_to_hero = {index: canonical(name) for index, name in enumerate(names) if canonical(name) in catalog}
    if not class_to_hero:
        raise SystemExit("No dataset class names map to the current HeroLens hero IDs")

    imported = 0
    ignored = 0
    args.output.mkdir(parents=True, exist_ok=True)
    for image_path in sorted(path for path in args.dataset.rglob("*") if path.suffix.lower() in IMAGE_EXTENSIONS):
        label_path = find_label(image_path)
        if label_path is None:
            continue
        with Image.open(image_path) as source:
            image = source.convert("RGB")
            for annotation_index, line in enumerate(label_path.read_text().splitlines()):
                fields = line.split()
                if len(fields) < 5:
                    continue
                try:
                    class_id = int(float(fields[0]))
                    cx, cy, width, height = map(float, fields[1:5])
                except ValueError:
                    continue
                hero_id = class_to_hero.get(class_id)
                if hero_id is None:
                    ignored += 1
                    continue
                box_width = width * image.width
                box_height = height * image.height
                if min(box_width, box_height) < args.minimum_pixels:
                    ignored += 1
                    continue
                pad_x = box_width * args.padding
                pad_y = box_height * args.padding
                left = max(0, int((cx - width / 2) * image.width - pad_x))
                top = max(0, int((cy - height / 2) * image.height - pad_y))
                right = min(image.width, int((cx + width / 2) * image.width + pad_x))
                bottom = min(image.height, int((cy + height / 2) * image.height + pad_y))
                if right <= left or bottom <= top:
                    continue
                folder = args.output / hero_id
                folder.mkdir(parents=True, exist_ok=True)
                destination = folder / f"{image_path.stem}_{annotation_index:03d}.jpg"
                image.crop((left, top, right, bottom)).save(destination, "JPEG", quality=94)
                imported += 1
                if args.limit and imported >= args.limit:
                    print(f"Imported {imported} crops; ignored {ignored}; stopped at limit")
                    return
    print(f"Imported {imported} licensed hero crops; ignored {ignored} unsupported/small boxes")


if __name__ == "__main__":
    main()
