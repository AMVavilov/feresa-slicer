// SPDX-License-Identifier: AGPL-3.0-only
import assert from "node:assert/strict";
import test from "node:test";
import { parseToolpath, parseToolpathDetailed } from "./toolpath-parser.mjs";

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
