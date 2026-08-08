#!/usr/bin/env python3
"""Train HeroLens' optional on-device hero portrait classifier.

The default job creates 20 independently augmented samples per hero (1,040 hero
samples for the 52-hero catalog) plus an unknown/background class. It also checks
the trained classifier against a mixed set of generated 5v5 and 6v6 scoreboard
scenes before publishing. It downloads only the portrait URLs already
listed in app/src/main/assets/hero_portraits.json. Real, user-reviewed crops can
be added under training/real_samples/<hero-id>/.

The output model accepts dynamic NCHW float32 batches [N, 3, 96, 96] normalized
with ImageNet mean/std and returns logits [N, class_count].
"""
from __future__ import annotations

import argparse
import io
import json
import math
import random
import time
from pathlib import Path
from typing import Iterable

import numpy as np
import requests
from PIL import Image, ImageEnhance, ImageFilter, ImageOps, ImageDraw
import torch
from torch import nn
from torch.utils.data import DataLoader, Dataset, ConcatDataset
from torchvision import transforms
from torchvision.models import MobileNet_V3_Small_Weights, mobilenet_v3_small

ROOT = Path(__file__).resolve().parents[1]
PORTRAIT_MANIFEST = ROOT / "app/src/main/assets/hero_portraits.json"
MODEL_DIR = ROOT / "app/src/main/assets/model"
CACHE_DIR = ROOT / "training/cache/portraits"
REAL_DIR = ROOT / "training/real_samples"
REAL_MANIFEST = REAL_DIR / "manifest.json"
INPUT_SIZE = 96
UNKNOWN = "__unknown__"
MEAN = (0.485, 0.456, 0.406)
STD = (0.229, 0.224, 0.225)


class RandomLowResolution:
    """Reproduce the tiny portrait raster seen when a TV occupies part of a frame."""

    def __init__(self, minimum: int = 16, maximum: int = 42, probability: float = 0.85):
        self.minimum = minimum
        self.maximum = maximum
        self.probability = probability

    def __call__(self, image: Image.Image) -> Image.Image:
        if random.random() >= self.probability:
            return image
        short_side = random.randint(self.minimum, self.maximum)
        scale = short_side / max(1, min(image.size))
        small = image.resize(
            (max(8, round(image.width * scale)), max(8, round(image.height * scale))),
            Image.Resampling.BILINEAR,
        )
        return small.resize(image.size, Image.Resampling.BILINEAR)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--samples-per-class", type=int, default=20)
    parser.add_argument("--validation-per-class", type=int, default=5)
    parser.add_argument("--scoreboard-samples-per-class", type=int, default=36)
    parser.add_argument("--scoreboard-validation-per-class", type=int, default=6)
    parser.add_argument("--full-scoreboard-train-scenes-per-layout", type=int, default=120)
    parser.add_argument("--full-scoreboard-validation-scenes-per-layout", type=int, default=24)
    parser.add_argument(
        "--real-samples-per-image",
        type=int,
        default=54,
        help="Augmented training variants generated from each reviewed real-TV crop",
    )
    parser.add_argument("--unknown-samples", type=int, default=220)
    parser.add_argument("--epochs", type=int, default=7)
    parser.add_argument("--batch-size", type=int, default=64)
    parser.add_argument("--benchmark-scoreboards", type=int, default=600)
    parser.add_argument("--seed", type=int, default=20260731)
    parser.add_argument("--output", type=Path, default=MODEL_DIR / "hero_classifier.onnx")
    parser.add_argument("--enforce-gates", action="store_true", help="Exit non-zero when publish thresholds are missed")
    return parser.parse_args()


def download_portraits(manifest: dict[str, str]) -> dict[str, Image.Image]:
    CACHE_DIR.mkdir(parents=True, exist_ok=True)
    session = requests.Session()
    session.headers.update({"User-Agent": "HeroLens-training/0.8"})
    images: dict[str, Image.Image] = {}
    failures: list[str] = []
    for index, (hero_id, url) in enumerate(manifest.items(), start=1):
        destination = CACHE_DIR / f"{hero_id}.png"
        try:
            if not destination.exists() or destination.stat().st_size < 2_000:
                response = session.get(url, timeout=35)
                response.raise_for_status()
                destination.write_bytes(response.content)
            image = Image.open(destination).convert("RGBA")
            alpha = image.getchannel("A")
            bbox = alpha.getbbox()
            if bbox:
                image = image.crop(bbox)
            images[hero_id] = image
            print(f"[{index:02d}/{len(manifest)}] {hero_id}")
        except Exception as exc:  # noqa: BLE001
            failures.append(f"{hero_id}: {exc}")
    if failures:
        raise RuntimeError("Portrait downloads failed:\n" + "\n".join(failures))
    return images


def random_panel_background(rng: random.Random) -> Image.Image:
    palettes = [
        ((18, 172, 221), (8, 92, 150)),
        ((220, 59, 70), (126, 24, 36)),
        ((45, 50, 65), (10, 14, 22)),
        ((80, 105, 130), (25, 34, 48)),
    ]
    start, end = rng.choice(palettes)
    image = Image.new("RGB", (INPUT_SIZE, INPUT_SIZE))
    px = image.load()
    horizontal = rng.random() < 0.6
    for y in range(INPUT_SIZE):
        for x in range(INPUT_SIZE):
            t = (x if horizontal else y) / (INPUT_SIZE - 1)
            noise = rng.randint(-7, 7)
            px[x, y] = tuple(max(0, min(255, int(a * (1 - t) + b * t) + noise)) for a, b in zip(start, end))
    draw = ImageDraw.Draw(image, "RGBA")
    for _ in range(rng.randint(1, 5)):
        x = rng.randint(0, INPUT_SIZE - 1)
        draw.rectangle((x, 0, min(INPUT_SIZE, x + rng.randint(1, 5)), INPUT_SIZE), fill=(255, 255, 255, rng.randint(3, 18)))
    return image


def add_screen_artifacts(image: Image.Image, rng: random.Random) -> Image.Image:
    image = image.convert("RGB")
    # Scanlines / TV moire.
    overlay = Image.new("RGBA", image.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(overlay)
    spacing = rng.choice([2, 3, 4, 5])
    alpha = rng.randint(4, 22)
    for y in range(rng.randrange(spacing), image.height, spacing):
        draw.line((0, y, image.width, y), fill=(0, 0, 0, alpha), width=1)
    image = Image.alpha_composite(image.convert("RGBA"), overlay).convert("RGB")
    if rng.random() < 0.7:
        image = image.filter(ImageFilter.GaussianBlur(rng.uniform(0.0, 1.4)))
    if rng.random() < 0.55:
        image = ImageEnhance.Contrast(image).enhance(rng.uniform(0.72, 1.35))
    if rng.random() < 0.55:
        image = ImageEnhance.Brightness(image).enhance(rng.uniform(0.62, 1.35))
    if rng.random() < 0.45:
        image = ImageEnhance.Color(image).enhance(rng.uniform(0.55, 1.45))
    # JPEG compression is one of the largest differences between clean portraits and
    # a phone camera aimed at a TV/laptop.
    if rng.random() < 0.8:
        buffer = io.BytesIO()
        image.save(buffer, "JPEG", quality=rng.randint(36, 88), optimize=False)
        image = Image.open(io.BytesIO(buffer.getvalue())).convert("RGB")
    return image


def synthesize_hero(base: Image.Image, seed: int) -> Image.Image:
    rng = random.Random(seed)
    canvas = random_panel_background(rng).convert("RGBA")
    portrait = base.copy()
    # Scoreboard icons frequently crop the shoulders and have a narrow visible area.
    scale = rng.uniform(0.82, 1.48)
    target_h = int(INPUT_SIZE * scale)
    target_w = max(10, int(portrait.width * target_h / max(1, portrait.height)))
    portrait = portrait.resize((target_w, target_h), Image.Resampling.LANCZOS)
    x = rng.randint(-int(target_w * 0.35), int(INPUT_SIZE - target_w * 0.58))
    y = rng.randint(-int(target_h * 0.18), int(INPUT_SIZE - target_h * 0.72))
    canvas.alpha_composite(portrait, (x, y))
    draw = ImageDraw.Draw(canvas, "RGBA")
    # Simulate role gutter, row borders, ult ring and selection highlights.
    if rng.random() < 0.65:
        gutter_w = rng.randint(3, 15)
        draw.rectangle((0, 0, gutter_w, INPUT_SIZE), fill=(240, 248, 255, rng.randint(15, 80)))
    if rng.random() < 0.55:
        draw.line((0, 1, INPUT_SIZE, 1), fill=(255, 255, 255, rng.randint(50, 150)), width=rng.randint(1, 3))
    if rng.random() < 0.35:
        radius = rng.randint(11, 26)
        cx, cy = rng.randint(5, 91), rng.randint(5, 91)
        draw.ellipse((cx - radius, cy - radius, cx + radius, cy + radius), outline=(255, 255, 255, rng.randint(25, 100)), width=2)
    image = canvas.convert("RGB")
    return add_screen_artifacts(image, rng)


def synthesize_unknown(seed: int) -> Image.Image:
    rng = random.Random(seed)
    image = random_panel_background(rng)
    draw = ImageDraw.Draw(image, "RGBA")
    kind = rng.randrange(5)
    if kind == 0:  # role icon-like blocks
        for _ in range(rng.randint(1, 4)):
            x, y = rng.randint(0, 70), rng.randint(0, 70)
            draw.rectangle((x, y, x + rng.randint(8, 26), y + rng.randint(8, 26)), fill=(255, 255, 255, rng.randint(50, 180)))
    elif kind == 1:  # text/stat-like lines
        for _ in range(rng.randint(3, 9)):
            y = rng.randint(0, 94)
            draw.rectangle((rng.randint(0, 25), y, rng.randint(45, 95), min(95, y + rng.randint(1, 4))), fill=(255, 255, 255, rng.randint(50, 180)))
    elif kind == 2:
        draw.ellipse((15, 15, 80, 80), outline=(255, 255, 255, 150), width=rng.randint(2, 7))
    elif kind == 3:
        image = Image.effect_noise((INPUT_SIZE, INPUT_SIZE), rng.uniform(15, 70)).convert("RGB")
    else:
        draw.polygon([(48, 5), (90, 85), (5, 75)], fill=(255, 255, 255, rng.randint(20, 130)))
    return add_screen_artifacts(image, rng)



def render_scoreboard_icon(base: Image.Image, width: int, height: int, seed: int) -> Image.Image:
    """Render a clean portrait inside a small scoreboard cell.

    Screen blur/compression is applied once to the complete row or scoreboard later.
    This avoids the unrealistic double-compression used by the first V8 trainer.
    """
    rng = random.Random(seed)
    palettes = [
        ((18, 172, 221), (8, 92, 150)),
        ((220, 59, 70), (126, 24, 36)),
        ((45, 50, 65), (10, 14, 22)),
        ((80, 105, 130), (25, 34, 48)),
    ]
    start, end = rng.choice(palettes)
    canvas = Image.new("RGBA", (max(1, width), max(1, height)), (*end, 255))
    draw = ImageDraw.Draw(canvas, "RGBA")
    for x in range(max(1, width)):
        t = x / max(1, width - 1)
        color = tuple(int(a * (1 - t) + b * t) for a, b in zip(start, end))
        draw.line((x, 0, x, height), fill=(*color, 255))

    portrait = base.copy().convert("RGBA")
    scale = rng.uniform(1.02, 1.62)
    target_h = max(2, int(height * scale))
    target_w = max(2, int(portrait.width * target_h / max(1, portrait.height)))
    portrait = portrait.resize((target_w, target_h), Image.Resampling.LANCZOS)
    x = rng.randint(-max(1, int(target_w * 0.34)), max(0, int(width - target_w * 0.58)))
    y = rng.randint(-max(1, int(target_h * 0.18)), max(0, int(height - target_h * 0.72)))
    canvas.alpha_composite(portrait, (x, y))

    draw = ImageDraw.Draw(canvas, "RGBA")
    if rng.random() < 0.65:
        draw.line((0, 1, width, 1), fill=(255, 255, 255, rng.randint(45, 135)), width=1)
    if rng.random() < 0.35:
        radius = max(3, int(min(width, height) * rng.uniform(0.22, 0.42)))
        cx, cy = rng.randint(0, max(0, width - 1)), rng.randint(0, max(0, height - 1))
        draw.ellipse((cx - radius, cy - radius, cx + radius, cy + radius), outline=(255, 255, 255, rng.randint(20, 90)), width=1)
    # Some console and event layouts can place a large progression badge over the
    # lower portrait. The Android scanner now also tries an upper-core crop, but
    # training on the obstruction prevents confident false hero matches.
    if rng.random() < 0.58:
        radius = max(4, int(min(width, height) * rng.uniform(0.24, 0.38)))
        cx = int(width * rng.uniform(0.42, 0.60))
        cy = int(height * rng.uniform(0.58, 0.76))
        draw.ellipse(
            (cx - radius, cy - radius, cx + radius, cy + radius),
            fill=(28, 33, 52, rng.randint(105, 185)),
            outline=(245, 248, 255, rng.randint(120, 230)),
            width=max(1, int(radius * 0.14)),
        )
        inner = max(1, int(radius * 0.62))
        draw.ellipse(
            (cx - inner, cy - inner, cx + inner, cy + inner),
            outline=(255, 90, 175, rng.randint(80, 200)),
            width=max(1, int(radius * 0.10)),
        )
    return canvas.convert("RGB")


def synthesize_scoreboard_crop(base: Image.Image, seed: int) -> Image.Image:
    """Create a portrait crop that matches what the Android locator sends to the model."""
    rng = random.Random(seed)
    row_height = rng.randint(33, 48)
    portrait_width = max(24, int(row_height * rng.uniform(0.82, 1.08)))
    role_gutter = rng.randint(8, 20)
    row_width = role_gutter + portrait_width + rng.randint(50, 135)
    team = rng.choice(["blue", "red", "neutral"])
    colors = {
        "blue": ((20, 178, 231), (8, 88, 160)),
        "red": ((232, 67, 80), (127, 25, 42)),
        "neutral": ((80, 105, 130), (25, 34, 48)),
    }[team]
    row = Image.new("RGB", (row_width, row_height), colors[1])
    draw = ImageDraw.Draw(row, "RGBA")
    draw.rectangle((0, 0, row_width, row_height), fill=(*colors[0], rng.randint(190, 235)))
    draw.rectangle((0, 0, role_gutter, row_height), fill=(245, 248, 255, rng.randint(18, 85)))

    icon = render_scoreboard_icon(base, portrait_width, row_height, seed + 7_919)
    row.paste(icon, (role_gutter, 0))
    name_left = role_gutter + portrait_width + rng.randint(7, 14)
    draw.rectangle((name_left, 7, min(row_width - 1, name_left + rng.randint(32, 95)), 12), fill=(255, 255, 255, rng.randint(75, 185)))
    for column in range(rng.randint(1, 3)):
        x = max(name_left + 10, row_width - 48 + column * 16)
        draw.rectangle((x, 8, min(row_width - 1, x + rng.randint(6, 14)), 13), fill=(255, 255, 255, rng.randint(65, 165)))

    hero_left = role_gutter
    hero_right = role_gutter + portrait_width
    pad_x = max(1, int(portrait_width * rng.uniform(0.02, 0.14)))
    pad_y = max(1, int(row_height * rng.uniform(0.01, 0.10)))
    crop = row.crop((
        max(0, hero_left - pad_x),
        max(0, 0 - pad_y),
        min(row.width, hero_right + pad_x),
        min(row.height, row_height + pad_y),
    ))

    # A small scoreboard cell is often only 25-45 pixels tall in the camera frame.
    # Downsample and restore before adding one final set of screen artifacts.
    downscale = rng.uniform(0.42, 0.94)
    small = crop.resize((max(12, int(crop.width * downscale)), max(12, int(crop.height * downscale))), Image.Resampling.BILINEAR)
    crop = small.resize(crop.size, Image.Resampling.BILINEAR)
    return add_screen_artifacts(crop, rng)


def make_scoreboard_scene(
    portraits: dict[str, Image.Image],
    hero_ids: list[str],
    seed: int,
    team_size: int = 5,
    include_region: bool = False,
) -> (
    tuple[Image.Image, list[tuple[tuple[int, int, int, int], str]]]
    | tuple[
        Image.Image,
        list[tuple[tuple[int, int, int, int], str]],
        tuple[int, int, int, int],
    ]
):
    """Build one full 16:9 scoreboard scene and return its known portrait boxes.

    This is not claimed as real gameplay data. It is a repeatable stress benchmark
    for screen scaling, TV/laptop colour shifts, compression, blur and row gutters.
    Real-device accuracy is measured separately with reviewed app exports.
    """
    rng = random.Random(seed)
    width, height = 960, 540
    background = Image.new("RGB", (width, height), (rng.randint(5, 22), rng.randint(7, 28), rng.randint(12, 38)))
    draw = ImageDraw.Draw(background, "RGBA")
    for _ in range(rng.randint(4, 14)):
        x1, y1 = rng.randint(0, width), rng.randint(0, height)
        x2, y2 = rng.randint(0, width), rng.randint(0, height)
        draw.line((x1, y1, x2, y2), fill=(255, 255, 255, rng.randint(2, 15)), width=rng.randint(1, 3))

    panel_width = rng.randint(470, 650)
    team_size = 6 if team_size == 6 else 5

    # Keep both panels fully inside the 960x540 benchmark canvas. The original
    # V8.3 generator chose a 33-43 px row height before accounting for twelve
    # rows. Some 6v6 combinations were taller than the canvas, which produced
    # negative portrait boxes and made PIL reject a crop whose lower coordinate
    # was above its upper coordinate.
    top_margin = rng.randint(28, 55)
    bottom_margin = rng.randint(18, 32)
    gap = rng.randint(24, 48) if team_size == 6 else rng.randint(30, 60)
    panel_extra = rng.randint(3, 11)
    available_for_panels = height - top_margin - bottom_margin - gap
    max_row_height = max(22, (available_for_panels // 2 - panel_extra) // team_size)
    preferred_min = 27 if team_size == 6 else 33
    preferred_max = 37 if team_size == 6 else 43
    row_high = max(22, min(preferred_max, max_row_height))
    row_low = min(preferred_min, row_high)
    row_height = rng.randint(row_low, row_high)
    panel_height = row_height * team_size + panel_extra

    left = rng.randint(70, max(71, width - panel_width - 55))
    used_height = panel_height * 2 + gap
    latest_top = max(top_margin, height - bottom_margin - used_height)
    top = rng.randint(top_margin, latest_top)
    blue_top = top
    red_top = blue_top + panel_height + gap

    total_players = team_size * 2
    chosen = rng.sample(hero_ids, total_players) if len(hero_ids) >= total_players else [rng.choice(hero_ids) for _ in range(total_players)]
    boxes: list[tuple[tuple[int, int, int, int], str]] = []
    portrait_width = int(row_height * rng.uniform(0.82, 1.05))
    role_gutter = rng.randint(8, 20)

    for team_index, (panel_top, colors) in enumerate([
        (blue_top, ((20, 178, 231, 232), (8, 88, 160, 230))),
        (red_top, ((232, 67, 80, 232), (127, 25, 42, 230))),
    ]):
        draw.rounded_rectangle(
            (left, panel_top, left + panel_width, panel_top + panel_height),
            radius=8,
            fill=colors[1],
            outline=(255, 255, 255, rng.randint(30, 110)),
            width=2,
        )
        for slot in range(team_size):
            row_top = panel_top + slot * row_height + 2
            row_bottom = min(panel_top + panel_height - 1, row_top + row_height - 2)
            row_alpha = 190 if slot % 2 else 225
            draw.rectangle((left, row_top, left + panel_width, row_bottom), fill=(*colors[0][:3], row_alpha))
            hero_id = chosen[team_index * team_size + slot]
            cell_left = left + role_gutter
            cell_top = row_top
            cell_right = cell_left + portrait_width
            cell_bottom = row_bottom
            icon = render_scoreboard_icon(
                portraits[hero_id],
                max(1, cell_right - cell_left),
                max(1, cell_bottom - cell_top),
                seed + team_index * 50_000 + slot * 991,
            )
            background.paste(icon, (cell_left, cell_top))
            boxes.append(((cell_left, cell_top, cell_right, cell_bottom), hero_id))
            # Fake player name/stat columns force the classifier to tolerate nearby UI.
            name_left = cell_right + rng.randint(7, 14)
            draw.rectangle((name_left, row_top + 7, name_left + rng.randint(80, 150), row_top + 12), fill=(255, 255, 255, rng.randint(90, 190)))
            stat_x = left + int(panel_width * 0.63)
            for column in range(4):
                x = stat_x + column * int(panel_width * 0.075)
                draw.rectangle((x, row_top + 8, x + rng.randint(8, 22), row_top + 13), fill=(255, 255, 255, rng.randint(80, 175)))

    # Simulate a distant TV/laptop capture by resampling the entire screen and then
    # restoring its original dimensions. Known portrait coordinates remain valid.
    downscale = rng.uniform(0.42, 0.92)
    small = background.resize((max(320, int(width * downscale)), max(180, int(height * downscale))), Image.Resampling.BILINEAR)
    background = small.resize((width, height), Image.Resampling.BILINEAR)
    background = add_screen_artifacts(background, rng)
    if include_region:
        return background, boxes, (left, blue_top, left + panel_width, red_top + panel_height)
    return background, boxes


class ScoreboardBenchmarkDataset(Dataset):
    """Known crops from generated full scoreboards, evaluated only after training."""

    def __init__(self, portraits: dict[str, Image.Image], labels: list[str], scenes: int, seed: int, team_size: int):
        self.portraits = portraits
        self.labels = labels
        self.hero_ids = labels[1:]
        self.scenes = max(1, scenes)
        self.team_size = 6 if team_size == 6 else 5
        self.slots_per_scene = self.team_size * 2
        self.seed = seed + 30_000_000 + self.team_size * 1_000_000
        self.by_label = {label: index for index, label in enumerate(labels)}
        self._cached_scene_index: int | None = None
        self._cached_scene: tuple[Image.Image, list[tuple[tuple[int, int, int, int], str]]] | None = None
        self.transform = transforms.Compose([
            transforms.Resize((INPUT_SIZE, INPUT_SIZE), antialias=True),
            transforms.ToTensor(),
            transforms.Normalize(MEAN, STD),
        ])

    def __len__(self) -> int:
        return self.scenes * self.slots_per_scene

    def __getitem__(self, index: int):
        scene_index = index // self.slots_per_scene
        slot = index % self.slots_per_scene
        if self._cached_scene_index != scene_index or self._cached_scene is None:
            self._cached_scene_index = scene_index
            self._cached_scene = make_scoreboard_scene(
                self.portraits,
                self.hero_ids,
                self.seed + scene_index * 100_019,
                team_size=self.team_size,
            )
        image, boxes = self._cached_scene
        (left, top, right, bottom), hero_id = boxes[slot]
        rng = random.Random(self.seed + scene_index * 1_003 + slot)
        pad_x = max(1, int((right - left) * rng.uniform(0.02, 0.13)))
        pad_y = max(1, int((bottom - top) * rng.uniform(0.01, 0.10)))
        crop_left = max(0, min(image.width - 1, left - pad_x))
        crop_top = max(0, min(image.height - 1, top - pad_y))
        crop_right = max(crop_left + 1, min(image.width, right + pad_x))
        crop_bottom = max(crop_top + 1, min(image.height, bottom + pad_y))
        crop = image.crop((crop_left, crop_top, crop_right, crop_bottom))
        return self.transform(crop), self.by_label[hero_id]


class FullScoreboardCropDataset(Dataset):
    """Crops generated from complete 5v5/6v6 scoreboard scenes.

    V8.3 trained mostly on isolated row crops, while its publish gate cropped from
    a complete screen after global resize, blur and JPEG degradation.  That domain
    gap produced strong row-crop validation but weak full-scoreboard accuracy.
    This dataset uses the same full-scene path as the benchmark with disjoint seeds.
    """

    def __init__(
        self,
        portraits: dict[str, Image.Image],
        labels: list[str],
        scenes_per_layout: int,
        seed: int,
        train: bool,
    ):
        self.by_label = {label: index for index, label in enumerate(labels)}
        self.items: list[tuple[Image.Image, int]] = []
        hero_ids = labels[1:]
        split_seed = seed + (40_000_000 if train else 50_000_000)
        scene_count = max(1, scenes_per_layout)

        for team_size in (5, 6):
            for scene_index in range(scene_count):
                scene_seed = split_seed + team_size * 1_000_000 + scene_index * 100_019
                image, boxes = make_scoreboard_scene(
                    portraits, hero_ids, scene_seed, team_size=team_size
                )
                for slot, ((left, top, right, bottom), hero_id) in enumerate(boxes):
                    rng = random.Random(scene_seed + slot * 1_003 + 17)
                    pad_x = max(1, int((right - left) * rng.uniform(0.02, 0.13)))
                    pad_y = max(1, int((bottom - top) * rng.uniform(0.01, 0.10)))
                    crop_left = max(0, min(image.width - 1, left - pad_x))
                    crop_top = max(0, min(image.height - 1, top - pad_y))
                    crop_right = max(crop_left + 1, min(image.width, right + pad_x))
                    crop_bottom = max(crop_top + 1, min(image.height, bottom + pad_y))
                    crop = image.crop((crop_left, crop_top, crop_right, crop_bottom))
                    self.items.append((crop, self.by_label[hero_id]))

        if train:
            self.transform = transforms.Compose([
                transforms.RandomPerspective(distortion_scale=0.08, p=0.30),
                transforms.RandomAffine(
                    degrees=1.8, translate=(0.025, 0.025), scale=(0.97, 1.03), shear=1.0
                ),
                transforms.Resize((INPUT_SIZE, INPUT_SIZE), antialias=True),
                transforms.ToTensor(),
                transforms.Normalize(MEAN, STD),
            ])
        else:
            self.transform = transforms.Compose([
                transforms.Resize((INPUT_SIZE, INPUT_SIZE), antialias=True),
                transforms.ToTensor(),
                transforms.Normalize(MEAN, STD),
            ])

    def __len__(self) -> int:
        return len(self.items)

    def __getitem__(self, index: int):
        image, target = self.items[index]
        return self.transform(image), target


class SyntheticHeroDataset(Dataset):
    def __init__(self, portraits: dict[str, Image.Image], labels: list[str], per_class: int, unknown_count: int, seed: int, train: bool):
        self.portraits = portraits
        self.labels = labels
        self.per_class = per_class
        self.unknown_count = unknown_count
        self.seed = seed + (0 if train else 10_000_000)
        self.hero_ids = labels[1:]
        self.length = len(self.hero_ids) * per_class + unknown_count
        if train:
            self.transform = transforms.Compose([
                transforms.RandomPerspective(distortion_scale=0.19, p=0.65),
                transforms.RandomAffine(degrees=4.0, translate=(0.06, 0.06), scale=(0.90, 1.10), shear=2.5),
                transforms.Resize((INPUT_SIZE, INPUT_SIZE), antialias=True),
                transforms.ToTensor(),
                transforms.Normalize(MEAN, STD),
            ])
        else:
            self.transform = transforms.Compose([
                transforms.Resize((INPUT_SIZE, INPUT_SIZE), antialias=True),
                transforms.ToTensor(),
                transforms.Normalize(MEAN, STD),
            ])

    def __len__(self) -> int:
        return self.length

    def __getitem__(self, index: int):
        if index < len(self.hero_ids) * self.per_class:
            class_offset = index // self.per_class
            sample_offset = index % self.per_class
            hero_id = self.hero_ids[class_offset]
            image = synthesize_hero(self.portraits[hero_id], self.seed + class_offset * 100_003 + sample_offset)
            target = class_offset + 1
        else:
            sample_offset = index - len(self.hero_ids) * self.per_class
            image = synthesize_unknown(self.seed + 90_000_000 + sample_offset)
            target = 0
        return self.transform(image), target



class ScoreboardCropDataset(Dataset):
    """Synthetic scoreboard-cell crops used for domain-matched training/validation."""

    def __init__(self, portraits: dict[str, Image.Image], labels: list[str], per_class: int, seed: int, train: bool):
        self.portraits = portraits
        self.labels = labels
        self.per_class = max(1, per_class)
        self.hero_ids = labels[1:]
        self.seed = seed + (20_000_000 if train else 25_000_000)
        self.length = len(self.hero_ids) * self.per_class
        if train:
            self.transform = transforms.Compose([
                transforms.RandomPerspective(distortion_scale=0.12, p=0.45),
                transforms.RandomAffine(
                    degrees=2.5,
                    translate=(0.04, 0.04),
                    scale=(0.94, 1.06),
                    shear=1.5,
                ),
                transforms.Resize((INPUT_SIZE, INPUT_SIZE), antialias=True),
                transforms.ToTensor(),
                transforms.Normalize(MEAN, STD),
            ])
        else:
            self.transform = transforms.Compose([
                transforms.Resize((INPUT_SIZE, INPUT_SIZE), antialias=True),
                transforms.ToTensor(),
                transforms.Normalize(MEAN, STD),
            ])

    def __len__(self) -> int:
        return self.length

    def __getitem__(self, index: int):
        class_offset = index // self.per_class
        sample_offset = index % self.per_class
        hero_id = self.hero_ids[class_offset]
        image = synthesize_scoreboard_crop(
            self.portraits[hero_id],
            self.seed + class_offset * 100_003 + sample_offset,
        )
        return self.transform(image), class_offset + 1


class RealCropDataset(Dataset):
    """Reviewed real-device portrait crops kept separate by train/validation split."""

    def __init__(self, labels: list[str], split: str, repeats: int = 1):
        if split not in {"train", "validation"}:
            raise ValueError(f"Unsupported real-sample split: {split}")
        self.items: list[tuple[Path, int]] = []
        self.repeats = max(1, repeats) if split == "train" else 1
        by_label = {label: index for index, label in enumerate(labels)}
        split_dir = REAL_DIR / split
        if split_dir.exists():
            for folder in split_dir.iterdir():
                if not folder.is_dir() or folder.name not in by_label:
                    continue
                for path in folder.rglob("*"):
                    if path.suffix.lower() in {".jpg", ".jpeg", ".png", ".webp"}:
                        self.items.append((path, by_label[folder.name]))
        self.transform = transforms.Compose(
            [
                transforms.RandomPerspective(distortion_scale=0.08, p=0.35),
                transforms.RandomAffine(
                    degrees=2.0, translate=(0.07, 0.07), scale=(0.86, 1.12), shear=1.0
                ),
                RandomLowResolution(),
                transforms.ColorJitter(brightness=0.18, contrast=0.18, saturation=0.12, hue=0.02),
                transforms.RandomApply([transforms.GaussianBlur(3, sigma=(0.1, 0.8))], p=0.25),
                transforms.Resize((INPUT_SIZE, INPUT_SIZE), antialias=True),
                transforms.ToTensor(),
                transforms.Normalize(MEAN, STD),
            ]
            if split == "train"
            else [
                transforms.Resize((INPUT_SIZE, INPUT_SIZE), antialias=True),
                transforms.ToTensor(),
                transforms.Normalize(MEAN, STD),
            ]
        )

    def __len__(self):
        return len(self.items) * self.repeats

    def __getitem__(self, index):
        path, target = self.items[index % len(self.items)]
        return self.transform(Image.open(path).convert("RGB")), target


def evaluate(model: nn.Module, loader: DataLoader, device: torch.device) -> tuple[float, float]:
    model.eval()
    correct = 0
    total = 0
    loss_sum = 0.0
    criterion = nn.CrossEntropyLoss()
    with torch.no_grad():
        for images, targets in loader:
            images, targets = images.to(device), targets.to(device)
            logits = model(images)
            loss_sum += criterion(logits, targets).item() * targets.size(0)
            correct += (logits.argmax(1) == targets).sum().item()
            total += targets.size(0)
    return correct / max(1, total), loss_sum / max(1, total)


def evaluate_real_groups(
    model: nn.Module, dataset: RealCropDataset, device: torch.device
) -> tuple[float, float, int]:
    """Average held-out crop logits per scoreboard slot, matching app consensus."""
    model.eval()
    grouped: dict[tuple[str, str], list[tuple[torch.Tensor, int]]] = {}
    for index, (path, target) in enumerate(dataset.items):
        parts = path.stem.rsplit("-", 1)
        group_name = parts[0] if len(parts) == 2 and parts[1] in {"left", "core", "right"} else path.stem
        image, _ = dataset[index]
        grouped.setdefault((path.parent.name, group_name), []).append((image, target))

    correct = 0
    loss_sum = 0.0
    criterion = nn.CrossEntropyLoss()
    slot_results: list[tuple[str, int, torch.Tensor]] = []
    with torch.no_grad():
        for (_, group_name), variants in grouped.items():
            images = torch.stack([image for image, _ in variants]).to(device)
            target = torch.tensor([variants[0][1]], device=device)
            mean_logits = model(images).mean(dim=0, keepdim=True)
            loss_sum += criterion(mean_logits, target).item()
            slot_results.append((group_name.split("-", 1)[0], target.item(), mean_logits[0]))

    for team in {team for team, _, _ in slot_results}:
        team_slots = [item for item in slot_results if item[0] == team]
        edges: list[tuple[float, int, int]] = []
        for slot_index, (_, _, logits) in enumerate(team_slots):
            candidates = logits.argsort(descending=True).tolist()
            candidates = [index for index in candidates if index != 0][:7]
            edges.extend((logits[index].item(), slot_index, index) for index in candidates)
        assigned_slots: set[int] = set()
        assigned_classes: set[int] = set()
        assignments: dict[int, int] = {}
        for _, slot_index, class_index in sorted(edges, reverse=True):
            if slot_index not in assigned_slots and class_index not in assigned_classes:
                assignments[slot_index] = class_index
                assigned_slots.add(slot_index)
                assigned_classes.add(class_index)
        correct += sum(
            assignments.get(slot_index) == target
            for slot_index, (_, target, _) in enumerate(team_slots)
        )
    groups = len(grouped)
    return correct / max(1, groups), loss_sum / max(1, groups), groups


def main() -> None:
    args = parse_args()
    random.seed(args.seed)
    np.random.seed(args.seed)
    torch.manual_seed(args.seed)

    manifest: dict[str, str] = json.loads(PORTRAIT_MANIFEST.read_text())
    labels = [UNKNOWN] + list(manifest.keys())
    portraits = download_portraits(manifest)
    print(f"Training labels: {len(labels)}; synthetic hero images: {len(manifest) * args.samples_per_class}")

    train = SyntheticHeroDataset(portraits, labels, args.samples_per_class, args.unknown_samples, args.seed, train=True)
    valid = SyntheticHeroDataset(
        portraits,
        labels,
        args.validation_per_class,
        max(55, args.unknown_samples // 4),
        args.seed,
        train=False,
    )
    scoreboard_train = ScoreboardCropDataset(
        portraits, labels, args.scoreboard_samples_per_class, args.seed, train=True
    )
    scoreboard_valid = ScoreboardCropDataset(
        portraits, labels, args.scoreboard_validation_per_class, args.seed, train=False
    )
    full_scoreboard_train = FullScoreboardCropDataset(
        portraits, labels, args.full_scoreboard_train_scenes_per_layout, args.seed, train=True
    )
    full_scoreboard_valid = FullScoreboardCropDataset(
        portraits, labels, args.full_scoreboard_validation_scenes_per_layout, args.seed, train=False
    )
    real = RealCropDataset(labels, "train", repeats=args.real_samples_per_image)
    real_valid = RealCropDataset(labels, "validation")
    real_manifest = json.loads(REAL_MANIFEST.read_text()) if REAL_MANIFEST.exists() else None
    if real_manifest is not None:
        expected = real_manifest.get("expected_images", {})
        if len(real.items) != expected.get("train"):
            raise RuntimeError(
                f"Real training manifest expects {expected.get('train')} images; found {len(real.items)}"
            )
        if len(real_valid.items) != expected.get("validation"):
            raise RuntimeError(
                f"Real validation manifest expects {expected.get('validation')} images; "
                f"found {len(real_valid.items)}"
            )
    train_parts: list[Dataset] = [train, scoreboard_train, full_scoreboard_train]
    if len(real):
        train_parts.append(real)
    train_dataset: Dataset = ConcatDataset(train_parts)

    train_loader = DataLoader(train_dataset, batch_size=args.batch_size, shuffle=True, num_workers=2, persistent_workers=True)
    valid_loader = DataLoader(valid, batch_size=args.batch_size, shuffle=False, num_workers=2, persistent_workers=True)
    scoreboard_valid_loader = DataLoader(scoreboard_valid, batch_size=args.batch_size, shuffle=False, num_workers=2, persistent_workers=True)
    full_scoreboard_valid_loader = DataLoader(
        full_scoreboard_valid, batch_size=args.batch_size, shuffle=False, num_workers=2, persistent_workers=True
    )
    real_valid_loader = DataLoader(
        real_valid, batch_size=args.batch_size, shuffle=False, num_workers=0
    ) if len(real_valid) else None
    benchmark_5v5_scenes = max(1, args.benchmark_scoreboards // 2)
    benchmark_6v6_scenes = max(1, args.benchmark_scoreboards - benchmark_5v5_scenes)
    scoreboard_benchmark_5v5 = ScoreboardBenchmarkDataset(
        portraits, labels, benchmark_5v5_scenes, args.seed, team_size=5
    )
    scoreboard_benchmark_6v6 = ScoreboardBenchmarkDataset(
        portraits, labels, benchmark_6v6_scenes, args.seed, team_size=6
    )
    scoreboard_loader_5v5 = DataLoader(scoreboard_benchmark_5v5, batch_size=args.batch_size, shuffle=False, num_workers=0)
    scoreboard_loader_6v6 = DataLoader(scoreboard_benchmark_6v6, batch_size=args.batch_size, shuffle=False, num_workers=0)

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    weights = MobileNet_V3_Small_Weights.DEFAULT
    model = mobilenet_v3_small(weights=weights)
    model.classifier[3] = nn.Linear(model.classifier[3].in_features, len(labels))
    model.to(device)

    for parameter in model.features.parameters():
        parameter.requires_grad = False
    optimizer = torch.optim.AdamW(model.classifier.parameters(), lr=2.5e-3, weight_decay=1e-4)
    criterion = nn.CrossEntropyLoss(label_smoothing=0.04)
    best_accuracy = 0.0
    best_scoreboard_crop_accuracy = 0.0
    best_full_scoreboard_validation_accuracy = 0.0
    best_real_scoreboard_validation_accuracy = 0.0
    best_selection_score = 0.0
    best_state = None
    started = time.time()

    for epoch in range(args.epochs):
        if epoch == max(2, args.epochs // 2):
            for block in list(model.features.children())[-3:]:
                for parameter in block.parameters():
                    parameter.requires_grad = True
            optimizer = torch.optim.AdamW(filter(lambda p: p.requires_grad, model.parameters()), lr=4e-4, weight_decay=1e-4)

        model.train()
        running = 0.0
        seen = 0
        for images, targets in train_loader:
            images, targets = images.to(device), targets.to(device)
            optimizer.zero_grad(set_to_none=True)
            logits = model(images)
            loss = criterion(logits, targets)
            loss.backward()
            optimizer.step()
            running += loss.item() * targets.size(0)
            seen += targets.size(0)

        accuracy, validation_loss = evaluate(model, valid_loader, device)
        crop_accuracy, crop_validation_loss = evaluate(model, scoreboard_valid_loader, device)
        full_crop_accuracy, full_crop_validation_loss = evaluate(model, full_scoreboard_valid_loader, device)
        if real_valid_loader is not None:
            real_accuracy, real_validation_loss, _ = evaluate_real_groups(model, real_valid, device)
            selection_score = (
                0.20 * accuracy + 0.20 * crop_accuracy +
                0.30 * full_crop_accuracy + 0.30 * real_accuracy
            )
        else:
            real_accuracy, real_validation_loss = 0.0, 0.0
            selection_score = 0.25 * accuracy + 0.25 * crop_accuracy + 0.50 * full_crop_accuracy
        print(
            f"epoch {epoch + 1}/{args.epochs} train_loss={running/max(1,seen):.4f} "
            f"val_loss={validation_loss:.4f} val_acc={accuracy:.4f} "
            f"score_crop_loss={crop_validation_loss:.4f} score_crop_acc={crop_accuracy:.4f} "
            f"full_crop_loss={full_crop_validation_loss:.4f} full_crop_acc={full_crop_accuracy:.4f} "
            f"real_tv_loss={real_validation_loss:.4f} real_tv_acc={real_accuracy:.4f} "
            f"selection={selection_score:.4f}"
        )
        best_accuracy = max(best_accuracy, accuracy)
        best_scoreboard_crop_accuracy = max(best_scoreboard_crop_accuracy, crop_accuracy)
        best_full_scoreboard_validation_accuracy = max(
            best_full_scoreboard_validation_accuracy, full_crop_accuracy
        )
        best_real_scoreboard_validation_accuracy = max(
            best_real_scoreboard_validation_accuracy, real_accuracy
        )
        if selection_score > best_selection_score:
            best_selection_score = selection_score
            best_state = {key: value.detach().cpu() for key, value in model.state_dict().items()}

    if best_state is not None:
        model.load_state_dict(best_state)
    model = model.cpu().eval()
    selected_real_scoreboard_validation_accuracy = 0.0
    if real_valid_loader is not None:
        selected_real_scoreboard_validation_accuracy, _, _ = evaluate_real_groups(
            model, real_valid, torch.device("cpu")
        )
    scoreboard_5v5_accuracy, scoreboard_5v5_loss = evaluate(model, scoreboard_loader_5v5, torch.device("cpu"))
    scoreboard_6v6_accuracy, scoreboard_6v6_loss = evaluate(model, scoreboard_loader_6v6, torch.device("cpu"))
    total_crops = len(scoreboard_benchmark_5v5) + len(scoreboard_benchmark_6v6)
    scoreboard_accuracy = (
        scoreboard_5v5_accuracy * len(scoreboard_benchmark_5v5) +
        scoreboard_6v6_accuracy * len(scoreboard_benchmark_6v6)
    ) / max(1, total_crops)
    scoreboard_loss = (
        scoreboard_5v5_loss * len(scoreboard_benchmark_5v5) +
        scoreboard_6v6_loss * len(scoreboard_benchmark_6v6)
    ) / max(1, total_crops)
    print(
        f"full_scoreboard_benchmark 5v5_scenes={benchmark_5v5_scenes} "
        f"5v5_accuracy={scoreboard_5v5_accuracy:.4f} 6v6_scenes={benchmark_6v6_scenes} "
        f"6v6_accuracy={scoreboard_6v6_accuracy:.4f} crops={total_crops} "
        f"combined_loss={scoreboard_loss:.4f} combined_accuracy={scoreboard_accuracy:.4f}"
    )

    MODEL_DIR.mkdir(parents=True, exist_ok=True)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    dummy = torch.zeros(1, 3, INPUT_SIZE, INPUT_SIZE)
    torch.onnx.export(
        model,
        dummy,
        args.output,
        input_names=["image"],
        output_names=["logits"],
        dynamic_axes={"image": {0: "batch"}, "logits": {0: "batch"}},
        opset_version=17,
        do_constant_folding=True,
        dynamo=False,
    )
    (MODEL_DIR / "hero_labels.txt").write_text("\n".join(labels) + "\n")
    metrics = {
        "model": "MobileNetV3-Small",
        "classes": len(labels),
        "hero_classes": len(labels) - 1,
        "synthetic_train_images": len(train),
        "scoreboard_crop_train_images": len(scoreboard_train),
        "full_scoreboard_crop_train_images": len(full_scoreboard_train),
        "real_train_images": len(real),
        "real_dataset_id": real_manifest.get("dataset_id") if real_manifest else None,
        "real_train_source_images": len(real.items),
        "real_scoreboard_validation_images": len(real_valid),
        "real_scoreboard_validation_groups": evaluate_real_groups(
            model, real_valid, torch.device("cpu")
        )[2] if len(real_valid) else 0,
        "validation_images": len(valid),
        "scoreboard_crop_validation_images": len(scoreboard_valid),
        "full_scoreboard_crop_validation_images": len(full_scoreboard_valid),
        "best_synthetic_validation_accuracy": round(best_accuracy, 6),
        "best_scoreboard_crop_validation_accuracy": round(best_scoreboard_crop_accuracy, 6),
        "best_full_scoreboard_validation_accuracy": round(best_full_scoreboard_validation_accuracy, 6),
        "best_real_scoreboard_validation_accuracy": round(best_real_scoreboard_validation_accuracy, 6),
        "selected_real_scoreboard_validation_accuracy": round(
            selected_real_scoreboard_validation_accuracy, 6
        ),
        "best_model_selection_score": round(best_selection_score, 6),
        "scoreboard_benchmark_scenes": args.benchmark_scoreboards,
        "scoreboard_benchmark_5v5_scenes": benchmark_5v5_scenes,
        "scoreboard_benchmark_6v6_scenes": benchmark_6v6_scenes,
        "scoreboard_benchmark_crops": total_crops,
        "scoreboard_benchmark_5v5_accuracy": round(scoreboard_5v5_accuracy, 6),
        "scoreboard_benchmark_6v6_accuracy": round(scoreboard_6v6_accuracy, 6),
        "scoreboard_benchmark_accuracy": round(scoreboard_accuracy, 6),
        "input_shape": ["N", 3, INPUT_SIZE, INPUT_SIZE],
        "elapsed_seconds": round(time.time() - started, 2),
        "seed": args.seed,
    }
    (MODEL_DIR / "model_metrics.json").write_text(json.dumps(metrics, indent=2) + "\n")
    print(json.dumps(metrics, indent=2))
    if args.enforce_gates:
        if best_accuracy < 0.72:
            raise SystemExit("Validation accuracy below 72%; refusing to publish model")
        if best_scoreboard_crop_accuracy < 0.72:
            raise SystemExit("Scoreboard-crop validation below 72%; refusing to publish model")
        if best_full_scoreboard_validation_accuracy < 0.65:
            raise SystemExit("Full-scoreboard crop validation below 65%; refusing to publish model")
        if len(real_valid) and best_real_scoreboard_validation_accuracy < 0.65:
            raise SystemExit("Real-TV scoreboard validation below 65%; refusing to publish model")
        if len(real_valid) and selected_real_scoreboard_validation_accuracy < 0.65:
            raise SystemExit("Selected model real-TV validation below 65%; refusing to publish model")
        if scoreboard_accuracy < 0.62:
            raise SystemExit("Combined full-scoreboard synthetic benchmark below 62%; refusing to publish model")
        if scoreboard_5v5_accuracy < 0.58:
            raise SystemExit("5v5 scoreboard benchmark below 58%; refusing to publish model")
        if scoreboard_6v6_accuracy < 0.58:
            raise SystemExit("6v6 scoreboard benchmark below 58%; refusing to publish model")


if __name__ == "__main__":
    main()
