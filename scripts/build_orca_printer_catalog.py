#!/usr/bin/env python3
"""Build a compact Android printer catalog from OrcaSlicer's system presets."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any


def load_json(path: Path) -> dict[str, Any]:
    with path.open(encoding="utf-8") as source:
        return json.load(source)


def first(value: Any, default: str = "") -> str:
    if isinstance(value, list):
        return str(value[0]) if value else default
    return str(value) if value is not None else default


def bed_size(value: Any) -> tuple[float, float]:
    if not isinstance(value, list):
        return 0.0, 0.0
    points: list[tuple[float, float]] = []
    for item in value:
        try:
            x_value, y_value = str(item).lower().split("x", 1)
            points.append((float(x_value), float(y_value)))
        except (TypeError, ValueError):
            continue
    if not points:
        return 0.0, 0.0
    xs, ys = zip(*points)
    return max(xs) - min(xs), max(ys) - min(ys)


def resolve_profile(
    name: str,
    profiles: dict[str, dict[str, Any]],
    stack: frozenset[str] = frozenset(),
) -> dict[str, Any]:
    if name in stack:
        raise ValueError(f"Cyclic printer profile inheritance: {name}")
    profile = profiles.get(name, {})
    parent_name = first(profile.get("inherits"))
    resolved = (
        resolve_profile(parent_name, profiles, stack | {name})
        if parent_name and parent_name in profiles
        else {}
    )
    resolved.update(profile)
    return resolved


def build_catalog(profile_root: Path, commit: str) -> dict[str, Any]:
    vendors: list[dict[str, Any]] = []
    total = 0
    for index_path in sorted(profile_root.glob("*.json")):
        index = load_json(index_path)
        machine_list = index.get("machine_list")
        if not isinstance(machine_list, list) or not machine_list:
            continue
        vendor_dir = profile_root / index_path.stem
        if not vendor_dir.is_dir():
            continue

        profile_by_name: dict[str, dict[str, Any]] = {}
        for path in (vendor_dir / "machine").glob("*.json"):
            try:
                profile = load_json(path)
            except (OSError, json.JSONDecodeError):
                continue
            name = first(profile.get("name"), path.stem)
            profile_by_name[name] = profile

        models: dict[str, dict[str, Any]] = {}
        for item in index.get("machine_model_list", []):
            if not isinstance(item, dict):
                continue
            sub_path = item.get("sub_path")
            if not sub_path:
                continue
            model_path = vendor_dir / str(sub_path)
            if model_path.is_file():
                model = load_json(model_path)
                models[first(model.get("name"), first(item.get("name")))] = model

        printers: list[dict[str, Any]] = []
        for item in machine_list:
            if not isinstance(item, dict):
                continue
            name = first(item.get("name"))
            profile = resolve_profile(name, profile_by_name)
            if profile.get("type") != "machine" or first(profile.get("instantiation")).lower() != "true":
                continue
            width, depth = bed_size(profile.get("printable_area"))
            if width <= 0 or depth <= 0:
                continue
            model_name = first(profile.get("printer_model"))
            model = models.get(model_name, {})
            nozzle = first(profile.get("nozzle_diameter"), first(model.get("nozzle_diameter"), "0.4"))
            printers.append(
                {
                    "name": name,
                    "model": model_name or name,
                    "family": first(model.get("family"), first(index.get("name"), index_path.stem)),
                    "nozzle": nozzle,
                    "bed_width": round(width, 3),
                    "bed_depth": round(depth, 3),
                    "printable_height": float(first(profile.get("printable_height"), "0") or 0),
                    "gcode_flavor": first(profile.get("gcode_flavor"), "marlin"),
                    "default_print_profile": first(profile.get("default_print_profile")),
                }
            )
        if printers:
            printers.sort(key=lambda item: (item["model"].lower(), float(item["nozzle"] or 0), item["name"].lower()))
            vendors.append(
                {
                    "name": first(index.get("name"), index_path.stem),
                    "version": first(index.get("version")),
                    "printers": printers,
                }
            )
            total += len(printers)

    vendors.sort(key=lambda item: item["name"].lower())
    return {
        "source": "OrcaSlicer resources/profiles",
        "source_commit": commit,
        "printer_count": total,
        "vendors": vendors,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("profile_root", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--commit", default="unknown")
    args = parser.parse_args()
    catalog = build_catalog(args.profile_root, args.commit)
    if catalog["printer_count"] < 100:
        raise SystemExit("Generated catalog is unexpectedly small")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(catalog, ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8",
    )
    print(f"vendors={len(catalog['vendors'])} printers={catalog['printer_count']}")


if __name__ == "__main__":
    main()
