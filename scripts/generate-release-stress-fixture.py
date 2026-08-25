#!/usr/bin/env python3
# SPDX-License-Identifier: AGPL-3.0-only
"""Generate Feresa's redistributable medium-complexity slicing regression STL."""

from __future__ import annotations

import argparse
import math
import struct
from pathlib import Path

Vertex = tuple[float, float, float]
Triangle = tuple[Vertex, Vertex, Vertex]


def normal(a: Vertex, b: Vertex, c: Vertex) -> Vertex:
    ux, uy, uz = (b[index] - a[index] for index in range(3))
    vx, vy, vz = (c[index] - a[index] for index in range(3))
    nx, ny, nz = uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx
    length = math.sqrt(nx * nx + ny * ny + nz * nz)
    return (0.0, 0.0, 0.0) if length == 0.0 else (nx / length, ny / length, nz / length)


def add_quad(triangles: list[Triangle], a: Vertex, b: Vertex, c: Vertex, d: Vertex) -> None:
    triangles.extend(((a, b, c), (a, c, d)))


def add_cylinder(
    triangles: list[Triangle],
    radius: float,
    bottom: float,
    top: float,
    segments: int,
) -> None:
    for index in range(segments):
        a0 = 2.0 * math.pi * index / segments
        a1 = 2.0 * math.pi * (index + 1) / segments
        p0 = (radius * math.cos(a0), radius * math.sin(a0), bottom)
        p1 = (radius * math.cos(a1), radius * math.sin(a1), bottom)
        q0 = (p0[0], p0[1], top)
        q1 = (p1[0], p1[1], top)
        add_quad(triangles, p0, p1, q1, q0)
        triangles.append(((0.0, 0.0, bottom), p1, p0))
        triangles.append(((0.0, 0.0, top), q0, q1))


def add_annulus(
    triangles: list[Triangle],
    inner: float,
    outer: float,
    bottom: float,
    top: float,
    segments: int,
) -> None:
    for index in range(segments):
        a0 = 2.0 * math.pi * index / segments
        a1 = 2.0 * math.pi * (index + 1) / segments
        inner0 = (inner * math.cos(a0), inner * math.sin(a0))
        inner1 = (inner * math.cos(a1), inner * math.sin(a1))
        outer0 = (outer * math.cos(a0), outer * math.sin(a0))
        outer1 = (outer * math.cos(a1), outer * math.sin(a1))
        ib0, ib1 = (*inner0, bottom), (*inner1, bottom)
        it0, it1 = (*inner0, top), (*inner1, top)
        ob0, ob1 = (*outer0, bottom), (*outer1, bottom)
        ot0, ot1 = (*outer0, top), (*outer1, top)
        add_quad(triangles, ob0, ob1, ot1, ot0)
        add_quad(triangles, ib1, ib0, it0, it1)
        add_quad(triangles, it0, ot0, ot1, it1)
        add_quad(triangles, ib1, ob1, ob0, ib0)


def add_torus(
    triangles: list[Triangle],
    center_x: float,
    major_radius: float,
    tube_radius: float,
    bottom: float,
    major_segments: int,
    minor_segments: int,
) -> None:
    def point(major_index: int, minor_index: int) -> Vertex:
        major = 2.0 * math.pi * major_index / major_segments
        minor = 2.0 * math.pi * minor_index / minor_segments
        radial = major_radius + tube_radius * math.cos(minor)
        return (
            center_x + radial * math.cos(major),
            radial * math.sin(major),
            bottom + tube_radius + tube_radius * math.sin(minor),
        )

    for major_index in range(major_segments):
        for minor_index in range(minor_segments):
            add_quad(
                triangles,
                point(major_index, minor_index),
                point(major_index + 1, minor_index),
                point(major_index + 1, minor_index + 1),
                point(major_index, minor_index + 1),
            )


def build_fixture() -> list[Triangle]:
    triangles: list[Triangle] = []
    add_cylinder(triangles, radius=25.0, bottom=0.0, top=3.2, segments=128)
    for center_radius in (4.0, 8.0, 12.0, 16.0, 20.0):
        add_annulus(
            triangles,
            inner=center_radius - 0.45,
            outer=center_radius + 0.45,
            bottom=3.2,
            top=4.8,
            segments=96,
        )
    add_torus(
        triangles,
        center_x=27.5,
        major_radius=4.0,
        tube_radius=1.5,
        bottom=0.5,
        major_segments=64,
        minor_segments=12,
    )
    return triangles


def write_binary_stl(path: Path, triangles: list[Triangle]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    header = b"Feresa Slicer generated preview stress fixture (AGPL-3.0-only)"
    with path.open("wb") as output:
        output.write(header.ljust(80, b"\0"))
        output.write(struct.pack("<I", len(triangles)))
        for triangle in triangles:
            output.write(struct.pack("<3f", *normal(*triangle)))
            for vertex in triangle:
                output.write(struct.pack("<3f", *vertex))
            output.write(struct.pack("<H", 0))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    triangles = build_fixture()
    write_binary_stl(args.output, triangles)
    print(f"wrote {len(triangles)} triangles to {args.output}")


if __name__ == "__main__":
    main()
