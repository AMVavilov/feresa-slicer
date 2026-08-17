// SPDX-License-Identifier: AGPL-3.0-only

const EPSILON = 1e-7;
const WORD_PATTERN = /([XYZEF])\s*(-?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?)/gi;

function readWords(line) {
    const words = new Map();
    for (const match of line.matchAll(WORD_PATTERN)) {
        words.set(match[1].toUpperCase(), Number(match[2]));
    }
    return words;
}

export function parseToolpathDetailed(gcode, bedWidth, bedDepth) {
    const segments = [];
    let x = 0;
    let y = 0;
    let z = 0;
    let extrusion = 0;
    let absoluteCoordinates = true;
    let absoluteExtrusion = true;
    let feedRate = 0;
    let layer = 0;
    let highestLayer = 0;

    for (const rawLine of gcode.split(/\r?\n/)) {
        const layerMatch = rawLine.match(/;\s*LAYER\s*:\s*(\d+)/i);
        if (layerMatch) {
            layer = Number(layerMatch[1]);
            highestLayer = Math.max(highestLayer, layer);
        }
        const line = rawLine.split(";", 1)[0].trim().toUpperCase();
        const commandMatch = line.match(/^([GM])\s*0*(\d+(?:\.\d+)?)(?:\s|$)/);
        if (!commandMatch) continue;
        const command = `${commandMatch[1]}${Number(commandMatch[2])}`;

        if (command === "G90") {
            absoluteCoordinates = true;
            continue;
        }
        if (command === "G91") {
            absoluteCoordinates = false;
            continue;
        }
        if (command === "M82") {
            absoluteExtrusion = true;
            continue;
        }
        if (command === "M83") {
            absoluteExtrusion = false;
            continue;
        }

        const words = readWords(line);
        if (command === "G92") {
            if (words.has("X")) x = words.get("X");
            if (words.has("Y")) y = words.get("Y");
            if (words.has("Z")) z = words.get("Z");
            if (words.has("E")) extrusion = words.get("E");
            continue;
        }
        if (command !== "G0" && command !== "G1") continue;

        const nextX = words.has("X")
            ? (absoluteCoordinates ? words.get("X") : x + words.get("X"))
            : x;
        const nextY = words.has("Y")
            ? (absoluteCoordinates ? words.get("Y") : y + words.get("Y"))
            : y;
        const nextZ = words.has("Z")
            ? (absoluteCoordinates ? words.get("Z") : z + words.get("Z"))
            : z;
        const nextExtrusion = words.has("E")
            ? (absoluteExtrusion ? words.get("E") : extrusion + words.get("E"))
            : extrusion;
        if (words.has("F")) feedRate = words.get("F");
        const extrusionDelta = nextExtrusion - extrusion;
        const hasPlanarMotion = Math.abs(nextX - x) > EPSILON || Math.abs(nextY - y) > EPSILON;

        if (hasPlanarMotion) {
            segments.push({
                start: [x - bedWidth / 2, z + 0.25, y - bedDepth / 2],
                end: [nextX - bedWidth / 2, nextZ + 0.25, nextY - bedDepth / 2],
                layer,
                z: nextZ,
                speed: feedRate / 60,
                extrusion: command === "G1" && extrusionDelta > EPSILON,
            });
        }

        // Travel moves must update the current position even though they are
        // not rendered. Otherwise the next extrusion is falsely connected to
        // the previous contour.
        x = nextX;
        y = nextY;
        z = nextZ;
        extrusion = nextExtrusion;
    }

    return { segments, layerCount: segments.length === 0 ? 0 : highestLayer + 1 };
}

export function parseToolpath(gcode, bedWidth, bedDepth) {
    const { segments } = parseToolpathDetailed(gcode, bedWidth, bedDepth);
    return segments
        .filter((segment) => segment.extrusion)
        .flatMap((segment) => [...segment.start, ...segment.end]);
}
