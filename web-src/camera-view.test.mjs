// SPDX-License-Identifier: AGPL-3.0-only
import assert from "node:assert/strict";
import test from "node:test";
import {
    CAMERA_VIEW_PRESETS,
    cameraDistanceToFrameSphere,
    cameraViewDefinition,
    createCameraViewPreference,
    normalizeCameraViewPreset,
} from "./camera-view.mjs";

const EXPECTED_CAMERA_VIEWS = Object.freeze({
    isometric: { direction: [1, 0.82, 1.16], up: [0, 1, 0] },
    top: { direction: [0, 1, 0], up: [0, 0, -1] },
    bottom: { direction: [0, -1, 0], up: [0, 0, 1] },
    front: { direction: [0, 0, 1], up: [0, 1, 0] },
    back: { direction: [0, 0, -1], up: [0, 1, 0] },
    left: { direction: [-1, 0, 0], up: [0, 1, 0] },
    right: { direction: [1, 0, 0], up: [0, 1, 0] },
});

function crossProductLengthSquared(left, right) {
    const x = left[1] * right[2] - left[2] * right[1];
    const y = left[2] * right[0] - left[0] * right[2];
    const z = left[0] * right[1] - left[1] * right[0];
    return x * x + y * y + z * z;
}

test("camera view presets expose stable directions and non-collinear up vectors", () => {
    assert.deepEqual(CAMERA_VIEW_PRESETS, Object.keys(EXPECTED_CAMERA_VIEWS));

    for (const [preset, expected] of Object.entries(EXPECTED_CAMERA_VIEWS)) {
        const definition = cameraViewDefinition(preset);
        assert.deepEqual(definition, { preset, ...expected });
        assert.ok(
            crossProductLengthSquared(definition.direction, definition.up) > 1e-12,
            `${preset} direction and up vectors must not be collinear`,
        );
    }
});

test("camera view presets reject unknown values", () => {
    assert.equal(normalizeCameraViewPreset(" Right "), "right");
    assert.throws(() => normalizeCameraViewPreset("diagonal"), /Unsupported camera view preset/);
    assert.throws(() => normalizeCameraViewPreset("toString"), /Unsupported camera view preset/);
});

test("selected camera view survives asynchronous scene reframing", () => {
    const preference = createCameraViewPreference();

    preference.select("top");
    assert.deepEqual(preference.current(), {
        preset: "top",
        direction: [0, 1, 0],
        up: [0, 0, -1],
    });

    // A newer selection made while a model is loading must win when the
    // completed load restores the camera view.
    preference.select("front");
    assert.equal(preference.current().preset, "front");
    assert.throws(() => preference.select("unknown"), /Unsupported camera view preset/);
    assert.equal(preference.current().preset, "front");
});

test("camera framing uses the narrower field of view", () => {
    const portraitDistance = cameraDistanceToFrameSphere({
        radius: 100,
        verticalFovDegrees: 40,
        aspect: 0.5,
        margin: 1,
    });
    const landscapeDistance = cameraDistanceToFrameSphere({
        radius: 100,
        verticalFovDegrees: 40,
        aspect: 2,
        margin: 1,
    });

    assert.ok(portraitDistance > landscapeDistance);
    assert.ok(Math.abs(landscapeDistance - (100 / Math.sin(20 * Math.PI / 180))) < 1e-9);
    assert.equal(cameraDistanceToFrameSphere({ radius: 0, verticalFovDegrees: 40, aspect: 1 }), 0);
});
