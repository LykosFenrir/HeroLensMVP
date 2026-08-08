#!/usr/bin/env python3
"""Train and export HeroLens' optional one-pass full-scoreboard detector.

The model is a compact anchor-free, YOLO-style grid detector. It consumes one
localized scoreboard image and returns pre-decoded rows matching the Android
contract: [center_x, center_y, width, height, confidence]. The Android app
classifies all detected portrait crops with the existing hero classifier in one
batch, so this new model only has to learn robust scoreboard geometry.

Synthetic full-scoreboard scenes provide deterministic box labels. They are a
bootstrap dataset, not a substitute for independently reviewed real scoreboards.
"""
from __future__ import annotations

import argparse
import json
import random
import time
from pathlib import Path

import numpy as np
from PIL import Image, ImageEnhance
import torch
from torch import nn
from torch.nn import functional as F
from torch.utils.data import DataLoader, Dataset
from torchvision.transforms import functional as TF

from train_hero_classifier import (
    MODEL_DIR,
    PORTRAIT_MANIFEST,
    UNKNOWN,
    download_portraits,
    make_scoreboard_scene,
)

INPUT_WIDTH = 320
INPUT_HEIGHT = 192
GRID_WIDTH = 20
GRID_HEIGHT = 12
TOP_K = 24


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--train-scenes", type=int, default=1_200)
    parser.add_argument("--validation-scenes", type=int, default=240)
    parser.add_argument("--epochs", type=int, default=8)
    parser.add_argument("--batch-size", type=int, default=12)
    parser.add_argument("--seed", type=int, default=20260801)
    parser.add_argument("--output", type=Path, default=MODEL_DIR / "scoreboard_detector.onnx")
    parser.add_argument(
        "--metrics-output",
        type=Path,
        default=MODEL_DIR / "scoreboard_detector_metrics.json",
    )
    parser.add_argument("--enforce-gates", action="store_true")
    return parser.parse_args()


class ScoreboardDetectionDataset(Dataset):
    def __init__(
        self,
        portraits: dict[str, Image.Image],
        labels: list[str],
        scenes: int,
        seed: int,
        train: bool,
    ):
        self.portraits = portraits
        self.hero_ids = labels[1:]
        self.class_by_id = {hero_id: index for index, hero_id in enumerate(self.hero_ids)}
        self.scenes = max(1, scenes)
        self.seed = seed + (70_000_000 if train else 80_000_000)
        self.train = train
        self.items = [self._generate(index) for index in range(self.scenes)]

    def __len__(self) -> int:
        return self.scenes

    def __getitem__(self, index: int):
        return self.items[index]

    def _generate(self, index: int):
        seed = self.seed + index * 100_019
        rng = random.Random(seed)
        team_size = 5 if index % 2 == 0 else 6
        scene, boxes, panel_region = make_scoreboard_scene(
            self.portraits,
            self.hero_ids,
            seed,
            team_size=team_size,
            include_region=True,
        )
        left, top, right, bottom = panel_region
        margin_x = rng.randint(4, 28)
        margin_y = rng.randint(3, 18)
        left = max(0, left - margin_x)
        top = max(0, top - margin_y)
        right = min(scene.width, right + margin_x)
        bottom = min(scene.height, bottom + margin_y)
        image = scene.crop((left, top, right, bottom))
        normalized: list[tuple[float, float, float, float, int]] = []
        for (box_left, box_top, box_right, box_bottom), hero_id in boxes:
            cx = ((box_left + box_right) / 2 - left) / max(1, right - left)
            cy = ((box_top + box_bottom) / 2 - top) / max(1, bottom - top)
            width = (box_right - box_left) / max(1, right - left)
            height = (box_bottom - box_top) / max(1, bottom - top)
            normalized.append((cx, cy, width, height, self.class_by_id[hero_id]))

        if self.train and rng.random() < 0.5:
            image = image.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
            normalized = [(1.0 - cx, cy, width, height, cls) for cx, cy, width, height, cls in normalized]
        if self.train:
            image = ImageEnhance.Brightness(image).enhance(rng.uniform(0.82, 1.18))
            image = ImageEnhance.Contrast(image).enhance(rng.uniform(0.84, 1.18))

        image = image.resize((INPUT_WIDTH, INPUT_HEIGHT), Image.Resampling.BILINEAR)
        tensor = TF.pil_to_tensor(image)
        objectness = torch.zeros((GRID_HEIGHT, GRID_WIDTH), dtype=torch.float32)
        box_targets = torch.zeros((4, GRID_HEIGHT, GRID_WIDTH), dtype=torch.float32)
        for cx, cy, width, height, class_index in normalized:
            grid_x = min(GRID_WIDTH - 1, max(0, int(cx * GRID_WIDTH)))
            grid_y = min(GRID_HEIGHT - 1, max(0, int(cy * GRID_HEIGHT)))
            objectness[grid_y, grid_x] = 1.0
            box_targets[:, grid_y, grid_x] = torch.tensor(
                [cx * GRID_WIDTH - grid_x, cy * GRID_HEIGHT - grid_y, width, height]
            )
        return tensor, objectness, box_targets


class ConvBlock(nn.Sequential):
    def __init__(self, in_channels: int, out_channels: int, stride: int):
        super().__init__(
            nn.Conv2d(in_channels, out_channels, 3, stride, 1, bias=False),
            nn.BatchNorm2d(out_channels),
            nn.SiLU(inplace=True),
            nn.Conv2d(out_channels, out_channels, 3, 1, 1, groups=out_channels, bias=False),
            nn.BatchNorm2d(out_channels),
            nn.SiLU(inplace=True),
            nn.Conv2d(out_channels, out_channels, 1, bias=False),
            nn.BatchNorm2d(out_channels),
            nn.SiLU(inplace=True),
        )


class TinyScoreboardDetector(nn.Module):
    def __init__(self):
        super().__init__()
        self.backbone = nn.Sequential(
            ConvBlock(3, 16, 2),
            ConvBlock(16, 28, 2),
            ConvBlock(28, 48, 2),
            ConvBlock(48, 72, 2),
            ConvBlock(72, 96, 1),
        )
        self.head = nn.Sequential(
            nn.Conv2d(96, 96, 3, 1, 1, bias=False),
            nn.BatchNorm2d(96),
            nn.SiLU(inplace=True),
            nn.Conv2d(96, 5, 1),
        )
        with torch.no_grad():
            self.head[-1].bias[:] = torch.tensor([0.0, 0.0, -2.8, -2.8, -4.0])

    def forward(self, images: torch.Tensor) -> torch.Tensor:
        return self.head(self.backbone(images))


class ExportDetector(nn.Module):
    def __init__(self, model: TinyScoreboardDetector):
        super().__init__()
        self.model = model
        grid_y, grid_x = torch.meshgrid(
            torch.arange(GRID_HEIGHT, dtype=torch.float32),
            torch.arange(GRID_WIDTH, dtype=torch.float32),
            indexing="ij",
        )
        self.register_buffer("grid_x", grid_x.reshape(1, -1))
        self.register_buffer("grid_y", grid_y.reshape(1, -1))

    def forward(self, images: torch.Tensor) -> torch.Tensor:
        raw = self.model(images)
        boxes = raw[:, :4].sigmoid().flatten(2)
        center_x = (boxes[:, 0] + self.grid_x) / GRID_WIDTH
        center_y = (boxes[:, 1] + self.grid_y) / GRID_HEIGHT
        width = boxes[:, 2]
        height = boxes[:, 3]
        objectness = raw[:, 4].sigmoid().flatten(1)
        score = objectness
        top_score, top_index = score.topk(TOP_K, dim=1)
        decoded = torch.stack(
            [center_x, center_y, width, height, score],
            dim=2,
        )
        gather_index = top_index.unsqueeze(-1).expand(-1, -1, 5)
        output = decoded.gather(1, gather_index)
        output[:, :, 4] = top_score
        return output


def detector_loss(raw, objectness, box_targets):
    positive = objectness > 0.5
    object_loss = F.binary_cross_entropy_with_logits(
        raw[:, 4], objectness, pos_weight=raw.new_tensor(28.0)
    )
    predicted_boxes = raw[:, :4].sigmoid().permute(0, 2, 3, 1)
    target_boxes = box_targets.permute(0, 2, 3, 1)
    box_loss = F.smooth_l1_loss(predicted_boxes[positive], target_boxes[positive])
    return object_loss + box_loss * 8.0, object_loss, box_loss


def validation_metrics(model, loader, device):
    model.eval()
    positives = 0
    iou_sum = 0.0
    recalled = 0
    false_positives = 0
    with torch.no_grad():
        for images, objectness, box_targets in loader:
            images = images.to(device).float().div_(255.0)
            objectness = objectness.to(device)
            box_targets = box_targets.to(device)
            raw = model(images)
            positive = objectness > 0.5
            positives += positive.sum().item()
            predicted_map = raw[:, :4].sigmoid().permute(0, 2, 3, 1)
            target_map = box_targets.permute(0, 2, 3, 1)
            positive_indices = positive.nonzero(as_tuple=False)
            predicted = predicted_map[positive]
            target = target_map[positive]
            grid_y = positive_indices[:, 1].float()
            grid_x = positive_indices[:, 2].float()
            predicted = torch.stack(
                [
                    (predicted[:, 0] + grid_x) / GRID_WIDTH,
                    (predicted[:, 1] + grid_y) / GRID_HEIGHT,
                    predicted[:, 2],
                    predicted[:, 3],
                ],
                dim=1,
            )
            target = torch.stack(
                [
                    (target[:, 0] + grid_x) / GRID_WIDTH,
                    (target[:, 1] + grid_y) / GRID_HEIGHT,
                    target[:, 2],
                    target[:, 3],
                ],
                dim=1,
            )
            pred_x1 = predicted[:, 0] - predicted[:, 2] / 2
            pred_y1 = predicted[:, 1] - predicted[:, 3] / 2
            pred_x2 = predicted[:, 0] + predicted[:, 2] / 2
            pred_y2 = predicted[:, 1] + predicted[:, 3] / 2
            target_x1 = target[:, 0] - target[:, 2] / 2
            target_y1 = target[:, 1] - target[:, 3] / 2
            target_x2 = target[:, 0] + target[:, 2] / 2
            target_y2 = target[:, 1] + target[:, 3] / 2
            intersection = (
                (torch.minimum(pred_x2, target_x2) - torch.maximum(pred_x1, target_x1)).clamp_min(0)
                * (torch.minimum(pred_y2, target_y2) - torch.maximum(pred_y1, target_y1)).clamp_min(0)
            )
            pred_area = (pred_x2 - pred_x1).clamp_min(0) * (pred_y2 - pred_y1).clamp_min(0)
            target_area = (target_x2 - target_x1).clamp_min(0) * (target_y2 - target_y1).clamp_min(0)
            iou = intersection / (pred_area + target_area - intersection).clamp_min(1e-6)
            iou_sum += iou.sum().item()
            confidence = raw[:, 4].sigmoid()
            recalled += ((confidence[positive] >= 0.32) & (iou >= 0.40)).sum().item()
            false_positives += ((confidence >= 0.32) & ~positive).sum().item()
    total = max(1, positives)
    recall = recalled / total
    precision = recalled / max(1, recalled + false_positives)
    return iou_sum / total, recall, precision


def main() -> None:
    args = parse_args()
    random.seed(args.seed)
    np.random.seed(args.seed)
    torch.manual_seed(args.seed)
    manifest: dict[str, str] = json.loads(PORTRAIT_MANIFEST.read_text())
    labels = [UNKNOWN] + list(manifest.keys())
    portraits = download_portraits(manifest)
    train = ScoreboardDetectionDataset(portraits, labels, args.train_scenes, args.seed, True)
    valid = ScoreboardDetectionDataset(portraits, labels, args.validation_scenes, args.seed, False)
    train_loader = DataLoader(train, batch_size=args.batch_size, shuffle=True, num_workers=0)
    valid_loader = DataLoader(valid, batch_size=args.batch_size, shuffle=False, num_workers=0)
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    model = TinyScoreboardDetector().to(device)
    optimizer = torch.optim.AdamW(model.parameters(), lr=2e-3, weight_decay=1e-4)
    scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=max(1, args.epochs))
    best_score = -1.0
    best_state = None
    best_metrics = (0.0, 0.0, 0.0)
    started = time.time()
    for epoch in range(args.epochs):
        model.train()
        running = 0.0
        batches = 0
        for images, objectness, box_targets in train_loader:
            images = images.to(device).float().div_(255.0)
            objectness = objectness.to(device)
            box_targets = box_targets.to(device)
            optimizer.zero_grad(set_to_none=True)
            loss, _, _ = detector_loss(model(images), objectness, box_targets)
            loss.backward()
            optimizer.step()
            running += loss.item()
            batches += 1
        scheduler.step()
        mean_iou, recall, precision = validation_metrics(model, valid_loader, device)
        score = mean_iou * 0.40 + recall * 0.35 + precision * 0.25
        print(
            f"epoch {epoch + 1}/{args.epochs} loss={running / max(1, batches):.4f} "
            f"mean_iou={mean_iou:.4f} recall={recall:.4f} precision={precision:.4f}"
        )
        if score > best_score:
            best_score = score
            best_metrics = (mean_iou, recall, precision)
            best_state = {key: value.detach().cpu() for key, value in model.state_dict().items()}

    if best_state is not None:
        model.load_state_dict(best_state)
    export = ExportDetector(model.cpu().eval())
    args.output.parent.mkdir(parents=True, exist_ok=True)
    torch.onnx.export(
        export,
        torch.zeros(1, 3, INPUT_HEIGHT, INPUT_WIDTH),
        args.output,
        input_names=["scoreboard"],
        output_names=["detections"],
        dynamic_axes={"scoreboard": {0: "batch"}, "detections": {0: "batch"}},
        opset_version=17,
        do_constant_folding=True,
        dynamo=False,
    )
    mean_iou, recall, precision = best_metrics
    metrics = {
        "model": "tiny-anchor-free-scoreboard-detector",
        "detected_object": "scoreboard-portrait",
        "train_scenes": len(train),
        "validation_scenes": len(valid),
        "synthetic_mean_iou": round(mean_iou, 6),
        "synthetic_detection_recall": round(recall, 6),
        "synthetic_detection_precision": round(precision, 6),
        "input_shape": ["N", 3, INPUT_HEIGHT, INPUT_WIDTH],
        "output_shape": ["N", TOP_K, 5],
        "elapsed_seconds": round(time.time() - started, 2),
        "seed": args.seed,
        "real_full_scoreboard_gate": "pending-reviewed-dataset",
    }
    args.metrics_output.parent.mkdir(parents=True, exist_ok=True)
    args.metrics_output.write_text(json.dumps(metrics, indent=2) + "\n")
    print(json.dumps(metrics, indent=2))
    if args.enforce_gates:
        if mean_iou < 0.50:
            raise SystemExit("Detector synthetic mean IoU below 50%")
        if recall < 0.45:
            raise SystemExit("Detector synthetic recall below 45%")
        if precision < 0.70:
            raise SystemExit("Detector synthetic precision below 70%")


if __name__ == "__main__":
    main()
