// SPDX-License-Identifier: AGPL-3.0-only
import assert from "node:assert/strict";
import test from "node:test";
import {
    lineTypeColor,
    maximumExtrusionSpeed,
    normalizeLineType,
    parseToolpath,
    parseToolpathDetailed,
    selectVisibleToolpathSegments,
    toolpathSelectionPayload,
} from "./toolpath-parser.mjs";

function segmentLengths(positions) {
    const lengths = [];
    for (let index = 0; index < positions.length; index += 6) {
        const dx = positions[index + 3] - positions[index];
        const dy = positions[index + 4] - positions[index + 1];
        const dz = positions[index + 5] - positions[index + 2];
        lengths.push(Math.hypot(dx, dy, dz));
    }
    return lengths;
}

test("G0 travel does not create an extrusion connector", () => {
    const positions = parseToolpath(`
        G90
        M82
        G92 E0
        G0 X10 Y10
        G1 X11 Y10 E1
        G0 X100 Y100
        G1 X101 Y100 E2
    `, 220, 220);

    assert.equal(positions.length / 6, 2);
    assert.deepEqual(segmentLengths(positions), [1, 1]);
});

test("relative coordinates and extrusion are respected", () => {
    const positions = parseToolpath(`
        G91
        M83
        G0 X10 Y20 Z0.2
        G1 X2 Y0 E0.1
        G0 X20 Y20
        G1 X0 Y3 E0.1
    `, 220, 220);

    assert.equal(positions.length / 6, 2);
    assert.deepEqual(segmentLengths(positions), [2, 3]);
});

test("G2 and G3 extrusion arcs are rendered as curved toolpath segments", () => {
    const result = parseToolpathDetailed(`
        G90
        M83
        G0 X10 Y0 Z0.2
        G3 X0 Y10 I-10 J0 E1 F1200
        G2 X0 Y10 I0 J-10 E1 F1200
    `, 220, 220);
    const extrusion = result.segments.filter((segment) => segment.extrusion);
    const totalLength = extrusion.reduce((sum, segment) => {
        const dx = segment.end[0] - segment.start[0];
        const dy = segment.end[1] - segment.start[1];
        const dz = segment.end[2] - segment.start[2];
        return sum + Math.hypot(dx, dy, dz);
    }, 0);

    assert.ok(extrusion.length > 30);
    assert.ok(Math.abs(totalLength - (Math.PI * 5 + Math.PI * 20)) < 0.2);
    assert.ok(Math.abs(extrusion.at(-1).x) < 1e-9);
    assert.ok(Math.abs(extrusion.at(-1).y - 10) < 1e-9);
});

test("G92 resets position and extrusion without drawing", () => {
    const positions = parseToolpath(`
        G90
        M82
        G92 X50 Y60 E0
        G1 X54 Y60 E0.5
        G92 E0
        G0 X70 Y80
        G1 X70 Y85 E0.5
    `, 220, 220);

    assert.equal(positions.length / 6, 2);
    assert.deepEqual(segmentLengths(positions), [4, 5]);
});

test("detailed preview keeps layer, speed and travel metadata", () => {
    const result = parseToolpathDetailed(`
        G90
        M82
        ;LAYER:0
        G0 X10 Y10 Z0.2 F6000
        G1 X20 Y10 E1 F2400
        ;LAYER:1
        G0 X30 Y30 Z0.4
        G1 X35 Y30 E2 F3000
    `, 220, 220);

    assert.equal(result.layerCount, 2);
    assert.equal(result.segments.length, 4);
    assert.equal(result.segments[0].extrusion, false);
    assert.equal(result.segments[1].extrusion, true);
    assert.equal(result.segments[1].speed, 40);
    assert.equal(result.segments[3].layer, 1);
    assert.equal(result.segments[3].z, 0.4);
});

test("maximum speed handles toolpaths larger than the JavaScript argument limit", () => {
    const segments = Array.from({ length: 200_000 }, (_, index) => ({
        extrusion: index % 2 === 0,
        speed: index % 2 === 0 ? index : 1_000_000,
    }));

    assert.equal(maximumExtrusionSpeed(segments), 199_998);
});

test("TYPE comments preserve Orca line roles and path dimensions", () => {
    const result = parseToolpathDetailed(`
        G90
        M83
        ;LAYER:0
        ;TYPE:Perimeter
        ;WIDTH:0.42
        ;HEIGHT:0.20
        G0 X10 Y10 Z0.2
        G1 X20 Y10 E0.5
        ;TYPE:External perimeter
        ;WIDTH:0.46
        G1 X20 Y20 E0.5
        ;TYPE:Sparse infill
        ;WIDTH:0.50
        G1 X10 Y20 E0.5
    `, 220, 220);

    const extrusion = result.segments.filter((segment) => segment.extrusion);
    assert.deepEqual(extrusion.map((segment) => segment.lineType), [
        "innerWall",
        "outerWall",
        "sparseInfill",
    ]);
    assert.deepEqual(extrusion.map((segment) => segment.lineTypeLabel), [
        "Perimeter",
        "External perimeter",
        "Sparse infill",
    ]);
    assert.deepEqual(extrusion.map((segment) => segment.lineWidth), [0.42, 0.46, 0.5]);
    assert.deepEqual(extrusion.map((segment) => segment.layerHeight), [0.2, 0.2, 0.2]);
});

test("Orca FEATURE, LINE_WIDTH, LAYER_HEIGHT and CHANGE_LAYER tags are parsed", () => {
    const result = parseToolpathDetailed(`
        G90
        M83
        ; CHANGE_LAYER
        ; LAYER_HEIGHT: 0.24
        ; LINE_WIDTH: 0.48
        ; FEATURE: Outer wall
        G0 X0 Y0 Z0.24
        G1 X10 Y0 E0.5
        ; CHANGE_LAYER
        ; LAYER_HEIGHT: 0.16
        ; FEATURE: Top surface
        G0 X0 Y0 Z0.40
        G1 X10 Y0 E0.5
    `, 220, 220);

    const extrusion = result.segments.filter((segment) => segment.extrusion);
    assert.equal(result.layerCount, 2);
    assert.deepEqual(extrusion.map((segment) => segment.layer), [0, 1]);
    assert.deepEqual(extrusion.map((segment) => segment.lineType), ["outerWall", "topSurface"]);
    assert.deepEqual(extrusion.map((segment) => segment.lineWidth), [0.48, 0.48]);
    assert.deepEqual(extrusion.map((segment) => segment.layerHeight), [0.24, 0.16]);
});

test("real Orca LAYER_CHANGE tags increment preview layers", () => {
    const result = parseToolpathDetailed(`
        G90
        M83
        ;LAYER_CHANGE
        G0 X0 Y0 Z0.2
        G1 X10 Y0 E0.5
        ;LAYER_CHANGE
        G0 X0 Y0 Z0.4
        G1 X10 Y0 E0.5
    `, 220, 220);

    const extrusion = result.segments.filter((segment) => segment.extrusion);
    assert.equal(result.layerCount, 2);
    assert.deepEqual(extrusion.map((segment) => segment.layer), [0, 1]);
});

test("layer height is inferred from extrusion Z changes when no tag is present", () => {
    const result = parseToolpathDetailed(`
        G90
        M83
        ;LAYER:0
        G0 X0 Y0 Z0.28
        G1 X10 Y0 E0.5
        ;LAYER:1
        G0 X0 Y0 Z0.48
        G1 X10 Y0 E0.5
    `, 220, 220);

    const extrusion = result.segments.filter((segment) => segment.extrusion);
    assert.ok(Math.abs(extrusion[0].layerHeight - 0.28) < 1e-9);
    assert.ok(Math.abs(extrusion[1].layerHeight - 0.2) < 1e-9);
});

test("preview filters layers and toggles before applying maximum segment ratio", () => {
    const segments = Array.from({ length: 10 }, (_, index) => ({
        layer: index < 2 ? 0 : 1,
        extrusion: index % 2 === 0,
        id: index,
    }));

    const visible = selectVisibleToolpathSegments(segments, {
        minimumLayer: 1,
        maximumLayer: 1,
        showExtrusion: true,
        showTravel: false,
        maximumSegmentRatio: 0.5,
    });
    assert.deepEqual(visible.map((segment) => segment.id), [2, 4, 6]);

    const first = selectVisibleToolpathSegments(segments, {
        showExtrusion: true,
        showTravel: true,
        maximumSegmentRatio: 0,
    });
    assert.deepEqual(first.map((segment) => segment.id), [0]);

    const all = selectVisibleToolpathSegments(segments, {
        showExtrusion: true,
        showTravel: true,
        maximumSegmentRatio: 1,
    });
    assert.equal(all.length, segments.length);
});

test("line type aliases receive distinct stable colors", () => {
    assert.equal(normalizeLineType("WALL-OUTER"), "outerWall");
    assert.equal(normalizeLineType("Support interface"), "supportInterface");
    assert.equal(normalizeLineType("not an Orca role"), "unknown");
    assert.notEqual(lineTypeColor("outerWall"), lineTypeColor("innerWall"));
    assert.notEqual(lineTypeColor("sparseInfill"), lineTypeColor("support"));
    assert.notEqual(lineTypeColor("travel", false), lineTypeColor("unknown", true));
});

test("selection payload reports metadata for the last actually displayed segment", () => {
    const result = parseToolpathDetailed(`
        G90
        M83
        ;LAYER:0
        ;TYPE:External perimeter
        ;WIDTH:0.46
        ;HEIGHT:0.24
        G0 X10 Y20 Z0.24 F6000
        G1 X30 Y40 E0.5 F2700
        ;TYPE:Sparse infill
        ;WIDTH:0.50
        G1 X50 Y60 E0.5 F4800
    `, 220, 220);
    const eligible = selectVisibleToolpathSegments(result.segments, {
        showExtrusion: true,
        showTravel: false,
        maximumSegmentRatio: 1,
    });
    const visible = selectVisibleToolpathSegments(result.segments, {
        showExtrusion: true,
        showTravel: false,
        maximumSegmentRatio: 0,
    });

    assert.deepEqual(toolpathSelectionPayload(eligible, visible), {
        selected: true,
        displayedSegmentCount: 1,
        eligibleSegmentCount: 2,
        layer: 0,
        x: 30,
        y: 40,
        z: 0.24,
        speed: 45,
        extrusion: true,
        lineType: "outerWall",
        lineTypeLabel: "External perimeter",
        lineWidth: 0.46,
        layerHeight: 0.24,
    });
    assert.deepEqual(toolpathSelectionPayload(eligible, []), {
        selected: false,
        displayedSegmentCount: 0,
        eligibleSegmentCount: 2,
    });
});
