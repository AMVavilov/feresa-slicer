// SPDX-License-Identifier: AGPL-3.0-only

const EPSILON = 1e-7;
const WORD_PATTERN = /([XYZEFIJKR])\s*(-?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?)/gi;

export const LINE_TYPE_COLORS = Object.freeze({
    travel: 0x3c8fd8,
    outerWall: 0xe43d30,
    innerWall: 0xf4a62a,
    overhangWall: 0x9c5ed5,
    sparseInfill: 0x67b547,
    solidInfill: 0xe98b2a,
    topSurface: 0xf2d13d,
    bottomSurface: 0xc78ad6,
    ironing: 0x65c8c0,
    bridge: 0x438bd3,
    internalBridge: 0x5d75c8,
    gapInfill: 0xd875a7,
    skirt: 0x44b7b0,
    brim: 0x25a790,
    support: 0x6b9eb5,
    supportInterface: 0x4b7e9c,
    supportTransition: 0x84adbd,
    primeTower: 0x8f6bb3,
    custom: 0x7e8581,
    multiple: 0x9a744d,
    unknown: 0xf26b38,
});

const LINE_TYPE_ALIASES = new Map([
    ["perimeter", "innerWall"],
    ["inner wall", "innerWall"],
    ["wall inner", "innerWall"],
    ["external perimeter", "outerWall"],
    ["outer wall", "outerWall"],
    ["wall outer", "outerWall"],
    ["overhang perimeter", "overhangWall"],
    ["overhang wall", "overhangWall"],
    ["sparse infill", "sparseInfill"],
    ["internal infill", "sparseInfill"],
    ["infill", "sparseInfill"],
    ["fill", "sparseInfill"],
    ["internal solid infill", "solidInfill"],
    ["solid infill", "solidInfill"],
    ["skin", "solidInfill"],
    ["top solid infill", "topSurface"],
    ["top surface", "topSurface"],
    ["bottom surface", "bottomSurface"],
    ["ironing", "ironing"],
    ["bridge", "bridge"],
    ["bridge infill", "bridge"],
    ["internal bridge", "internalBridge"],
    ["internal bridge infill", "internalBridge"],
    ["gap fill", "gapInfill"],
    ["gap infill", "gapInfill"],
    ["skirt", "skirt"],
    ["raft", "skirt"],
    ["brim", "brim"],
    ["support", "support"],
    ["support material", "support"],
    ["support interface", "supportInterface"],
    ["support material interface", "supportInterface"],
    ["support transition", "supportTransition"],
    ["prime tower", "primeTower"],
    ["wipe tower", "primeTower"],
    ["custom", "custom"],
    ["multiple", "multiple"],
    ["undefined", "unknown"],
    ["none", "unknown"],
]);

function normalizedMetadataLabel(label) {
    return String(label || "")
        .trim()
        .toLowerCase()
        .replace(/[_-]+/g, " ")
        .replace(/\s+/g, " ");
}

export function normalizeLineType(label) {
    return LINE_TYPE_ALIASES.get(normalizedMetadataLabel(label)) || "unknown";
}

export function lineTypeColor(lineType, extrusion = true) {
    if (!extrusion) return LINE_TYPE_COLORS.travel;
    return LINE_TYPE_COLORS[lineType] ?? LINE_TYPE_COLORS.unknown;
}

function positiveMetadataNumber(comment, names) {
    const pattern = new RegExp(`^(?:${names.join("|")})\\s*[:=]\\s*(-?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?)`, "i");
    const match = comment.match(pattern);
    if (!match) return null;
    const value = Number(match[1]);
    return Number.isFinite(value) && value > 0 ? value : null;
}

function readWords(line) {
    const words = new Map();
    for (const match of line.matchAll(WORD_PATTERN)) {
        words.set(match[1].toUpperCase(), Number(match[2]));
    }
    return words;
}

function directedArcSweep(startAngle, endAngle, clockwise) {
    let sweep = endAngle - startAngle;
    if (clockwise) {
        while (sweep >= 0) sweep -= Math.PI * 2;
    }
    else {
        while (sweep <= 0) sweep += Math.PI * 2;
    }
    return sweep;
}

function arcCenterFromRadius(startX, startY, endX, endY, radiusWord, clockwise) {
    const dx = endX - startX;
    const dy = endY - startY;
    const chord = Math.hypot(dx, dy);
    const radius = Math.abs(radiusWord);
    if (chord <= EPSILON || radius <= EPSILON || chord > radius * 2 + EPSILON) return null;
    const middleX = (startX + endX) / 2;
    const middleY = (startY + endY) / 2;
    const height = Math.sqrt(Math.max(0, radius * radius - chord * chord / 4));
    const perpendicularX = -dy / chord;
    const perpendicularY = dx / chord;
    const candidates = [1, -1].map((side) => {
        const centerX = middleX + perpendicularX * height * side;
        const centerY = middleY + perpendicularY * height * side;
        const startAngle = Math.atan2(startY - centerY, startX - centerX);
        const endAngle = Math.atan2(endY - centerY, endX - centerX);
        const sweep = directedArcSweep(startAngle, endAngle, clockwise);
        return { centerX, centerY, sweep };
    });
    const wantsMajorArc = radiusWord < 0;
    return candidates.find((candidate) =>
        wantsMajorArc ? Math.abs(candidate.sweep) > Math.PI : Math.abs(candidate.sweep) <= Math.PI
    ) || candidates[0];
}

function interpolateArc(startX, startY, endX, endY, words, clockwise) {
    let center;
    if (words.has("I") || words.has("J")) {
        center = {
            centerX: startX + (words.get("I") || 0),
            centerY: startY + (words.get("J") || 0),
        };
        const startAngle = Math.atan2(startY - center.centerY, startX - center.centerX);
        const endAngle = Math.atan2(endY - center.centerY, endX - center.centerX);
        center.sweep = directedArcSweep(startAngle, endAngle, clockwise);
    }
    else if (words.has("R")) {
        center = arcCenterFromRadius(startX, startY, endX, endY, words.get("R"), clockwise);
    }
    if (!center) return [];

    const radius = Math.hypot(startX - center.centerX, startY - center.centerY);
    if (radius <= EPSILON || Math.abs(center.sweep) <= EPSILON) return [];
    const startAngle = Math.atan2(startY - center.centerY, startX - center.centerX);
    const count = Math.min(720, Math.max(
        2,
        Math.ceil(Math.abs(center.sweep) / (Math.PI / 18)),
        Math.ceil(Math.abs(center.sweep) * radius / 0.5),
    ));
    return Array.from({ length: count }, (_, index) => {
        const ratio = (index + 1) / count;
        const angle = startAngle + center.sweep * ratio;
        return {
            x: index === count - 1 ? endX : center.centerX + Math.cos(angle) * radius,
            y: index === count - 1 ? endY : center.centerY + Math.sin(angle) * radius,
            ratio,
        };
    });
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
    let changeLayerSeen = false;
    let lineTypeLabel = "Undefined";
    let lineType = "unknown";
    let explicitLineWidth = null;
    let explicitLayerHeight = null;
    let inferredLayerHeight = null;
    let previousExtrusionZ = null;

    for (const rawLine of gcode.split(/\r?\n/)) {
        const commentStart = rawLine.indexOf(";");
        const comment = commentStart >= 0 ? rawLine.slice(commentStart + 1).trim() : "";
        const layerMatch = comment.match(/^LAYER\s*:\s*(\d+)/i);
        if (layerMatch) {
            layer = Number(layerMatch[1]);
            highestLayer = Math.max(highestLayer, layer);
        }
        else if (/^(?:CHANGE_LAYER|LAYER_CHANGE)\b/i.test(comment)) {
            if (changeLayerSeen) layer += 1;
            changeLayerSeen = true;
            highestLayer = Math.max(highestLayer, layer);
        }

        const roleMatch = comment.match(/^(?:TYPE|FEATURE)\s*:\s*(.+?)\s*$/i);
        if (roleMatch) {
            lineTypeLabel = roleMatch[1];
            lineType = normalizeLineType(lineTypeLabel);
        }

        const taggedLineWidth = positiveMetadataNumber(comment, ["LINE_WIDTH", "WIDTH"]);
        if (taggedLineWidth !== null) explicitLineWidth = taggedLineWidth;
        const taggedLayerHeight = positiveMetadataNumber(comment, ["LAYER_HEIGHT", "HEIGHT"]);
        if (taggedLayerHeight !== null) explicitLayerHeight = taggedLayerHeight;

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
        const isArc = command === "G2" || command === "G3";
        if (command !== "G0" && command !== "G1" && !isArc) continue;

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
        const arcPoints = isArc
            ? interpolateArc(x, y, nextX, nextY, words, command === "G2")
            : [];
        const hasPlanarMotion = arcPoints.length > 0 ||
            Math.abs(nextX - x) > EPSILON || Math.abs(nextY - y) > EPSILON;
        const isExtrusion = (command === "G1" || isArc) && extrusionDelta > EPSILON;

        if (isExtrusion) {
            if (previousExtrusionZ === null && nextZ > EPSILON) {
                inferredLayerHeight = nextZ;
            }
            else if (previousExtrusionZ !== null && nextZ - previousExtrusionZ > EPSILON) {
                inferredLayerHeight = nextZ - previousExtrusionZ;
            }
            previousExtrusionZ = nextZ;
        }

        if (hasPlanarMotion) {
            const path = arcPoints.length > 0
                ? arcPoints.map((point) => ({
                    x: point.x,
                    y: point.y,
                    z: z + (nextZ - z) * point.ratio,
                }))
                : [{ x: nextX, y: nextY, z: nextZ }];
            let segmentStartX = x;
            let segmentStartY = y;
            let segmentStartZ = z;
            for (const point of path) {
                segments.push({
                    start: [segmentStartX - bedWidth / 2, segmentStartZ + 0.25, segmentStartY - bedDepth / 2],
                    end: [point.x - bedWidth / 2, point.z + 0.25, point.y - bedDepth / 2],
                    layer,
                    x: point.x,
                    y: point.y,
                    z: point.z,
                    speed: feedRate / 60,
                    extrusion: isExtrusion,
                    lineType: isExtrusion ? lineType : "travel",
                    lineTypeLabel: isExtrusion ? lineTypeLabel : "Travel",
                    lineWidth: isExtrusion ? explicitLineWidth : null,
                    layerHeight: isExtrusion ? (explicitLayerHeight ?? inferredLayerHeight) : null,
                });
                segmentStartX = point.x;
                segmentStartY = point.y;
                segmentStartZ = point.z;
            }
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

export function selectVisibleToolpathSegments(segments, preview = {}) {
    const minimumLayer = Number.isFinite(preview.minimumLayer) ? preview.minimumLayer : 0;
    const maximumLayer = Number.isFinite(preview.maximumLayer)
        ? preview.maximumLayer
        : Number.MAX_SAFE_INTEGER;
    const showExtrusion = preview.showExtrusion !== false;
    const showTravel = preview.showTravel === true;
    const filtered = segments.filter((segment) =>
        segment.layer >= minimumLayer &&
        segment.layer <= maximumLayer &&
        ((segment.extrusion && showExtrusion) || (!segment.extrusion && showTravel))
    );
    if (filtered.length === 0) return [];

    const requestedRatio = Number(preview.maximumSegmentRatio);
    const maximumSegmentRatio = Number.isFinite(requestedRatio)
        ? Math.min(1, Math.max(0, requestedRatio))
        : 1;
    const maximumIndex = Math.round((filtered.length - 1) * maximumSegmentRatio);
    return filtered.slice(0, maximumIndex + 1);
}

export function toolpathSelectionPayload(eligibleSegments, visibleSegments) {
    const selected = visibleSegments.at(-1);
    if (!selected) {
        return {
            selected: false,
            displayedSegmentCount: 0,
            eligibleSegmentCount: eligibleSegments.length,
        };
    }
    return {
        selected: true,
        displayedSegmentCount: visibleSegments.length,
        eligibleSegmentCount: eligibleSegments.length,
        layer: selected.layer,
        x: selected.x,
        y: selected.y,
        z: selected.z,
        speed: selected.speed,
        extrusion: selected.extrusion,
        lineType: selected.lineType,
        lineTypeLabel: selected.lineTypeLabel,
        lineWidth: selected.lineWidth,
        layerHeight: selected.layerHeight,
    };
}

export function parseToolpath(gcode, bedWidth, bedDepth) {
    const { segments } = parseToolpathDetailed(gcode, bedWidth, bedDepth);
    return segments
        .filter((segment) => segment.extrusion)
        .flatMap((segment) => [...segment.start, ...segment.end]);
}

export function maximumExtrusionSpeed(segments) {
    return segments.reduce(
        (maximum, segment) => segment.extrusion ? Math.max(maximum, segment.speed) : maximum,
        1,
    );
}
