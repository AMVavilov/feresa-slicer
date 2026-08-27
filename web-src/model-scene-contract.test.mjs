// SPDX-License-Identifier: AGPL-3.0-only
import assert from "node:assert/strict";
import test from "node:test";
import {
    modelTransformToViewerComponents,
    normalizeModelObjectsPayload,
    normalizeModelTransform,
    objectSelectionPayload,
    pointerGestureIsTap,
    shouldReportObjectSelection,
} from "./model-scene-contract.mjs";

test("normalizes stable object ids and independent transforms", () => {
    const payload = normalizeModelObjectsPayload({
        version: 42,
        selectedObjectId: "right",
        objects: [
            {
                objectId: "left",
                url: "/models/left.stl",
                transform: { positionX: 45, positionY: 80, rotationDegrees: 15, scale: 0.5 },
            },
            {
                objectId: "right",
                url: "/models/right.stl",
                transform: { positionX: 170, positionY: 125, rotationDegrees: 90, scale: 1.25 },
            },
        ],
    });

    assert.equal(payload.version, "42");
    assert.equal(payload.selectedObjectId, "right");
    assert.deepEqual(payload.objects.map((object) => object.objectId), ["left", "right"]);
    assert.deepEqual(payload.objects[0].transform, {
        positionX: 45,
        positionY: 80,
        positionZ: 0,
        rotationXDegrees: 0,
        rotationYDegrees: 0,
        rotationZDegrees: 15,
        scaleX: 0.5,
        scaleY: 0.5,
        scaleZ: 0.5,
        rotationDegrees: 15,
        scale: 0.5,
    });
    assert.deepEqual(payload.objects[1].transform, {
        positionX: 170,
        positionY: 125,
        positionZ: 0,
        rotationXDegrees: 0,
        rotationYDegrees: 0,
        rotationZDegrees: 90,
        scaleX: 1.25,
        scaleY: 1.25,
        scaleZ: 1.25,
        rotationDegrees: 90,
        scale: 1.25,
    });
});

test("uses the first object when requested selection is absent", () => {
    const payload = normalizeModelObjectsPayload({
        selectedObjectId: "missing",
        frameAll: false,
        objects: [{ objectId: "first", url: "first.stl" }],
    });

    assert.equal(payload.selectedObjectId, "first");
    assert.equal(payload.frameAll, false);
    assert.deepEqual(payload.objects[0].transform, {
        positionX: 110,
        positionY: 110,
        positionZ: 0,
        rotationXDegrees: 0,
        rotationYDegrees: 0,
        rotationZDegrees: 0,
        scaleX: 1,
        scaleY: 1,
        scaleZ: 1,
        rotationDegrees: 0,
        scale: 1,
    });
});

test("an empty model payload remains an explicit empty scene", () => {
    const payload = normalizeModelObjectsPayload({
        version: "empty-plate",
        selectedObjectId: null,
        objects: [],
    });

    assert.equal(payload.version, "empty-plate");
    assert.deepEqual(payload.objects, []);
    assert.equal(payload.selectedObjectId, null);
});

test("rejects duplicate or empty object ids", () => {
    assert.throws(
        () => normalizeModelObjectsPayload([
            { objectId: "same", url: "one.stl" },
            { objectId: "same", url: "two.stl" },
        ]),
        /Duplicate model objectId/,
    );
    assert.throws(
        () => normalizeModelObjectsPayload([{ objectId: "", url: "one.stl" }]),
        /has no objectId/,
    );
});

test("rejects non-positive scales and preserves finite transform fallback", () => {
    assert.throws(() => normalizeModelTransform({ scale: 0 }), /greater than zero/);
    assert.deepEqual(
        normalizeModelTransform({ positionX: "not-a-number", rotationDegrees: "30", scale: "2" }),
        {
            positionX: 110,
            positionY: 110,
            positionZ: 0,
            rotationXDegrees: 0,
            rotationYDegrees: 0,
            rotationZDegrees: 30,
            scaleX: 2,
            scaleY: 2,
            scaleZ: 2,
            rotationDegrees: 30,
            scale: 2,
        },
    );
});

test("normalizes XYZ translation, rotation and non-uniform scale", () => {
    const transform = normalizeModelTransform({
        positionX: 12,
        positionY: 34,
        positionZ: 5,
        rotationXDegrees: 10,
        rotationYDegrees: 20,
        rotationZDegrees: 30,
        scaleX: 0.5,
        scaleY: 1.5,
        scaleZ: 2,
    });

    assert.deepEqual(transform, {
        positionX: 12,
        positionY: 34,
        positionZ: 5,
        rotationXDegrees: 10,
        rotationYDegrees: 20,
        rotationZDegrees: 30,
        scaleX: 0.5,
        scaleY: 1.5,
        scaleZ: 2,
        rotationDegrees: 30,
        scale: 1,
    });
});

test("legacy partial updates still replace Z rotation and all scale axes", () => {
    const nonUniform = normalizeModelTransform({
        rotationZDegrees: 25,
        scaleX: 1,
        scaleY: 2,
        scaleZ: 3,
    });

    assert.deepEqual(normalizeModelTransform({ rotationDegrees: 75 }, nonUniform), {
        ...nonUniform,
        rotationDegrees: 75,
        rotationZDegrees: 75,
    });
    assert.deepEqual(normalizeModelTransform({ scale: 4 }, nonUniform), {
        ...nonUniform,
        scale: 4,
        scaleX: 4,
        scaleY: 4,
        scaleZ: 4,
    });
});

test("maps print transform into viewer axes with print XYZ operation order", () => {
    const result = modelTransformToViewerComponents({
        positionX: 130,
        positionY: 140,
        positionZ: 7,
        rotationXDegrees: 10,
        rotationYDegrees: 20,
        rotationZDegrees: 30,
        scaleX: 2,
        scaleY: 3,
        scaleZ: 4,
    }, 220, 240);
    const radians = Math.PI / 180;

    assert.deepEqual(result.position, [20, 7, 20]);
    assert.deepEqual(result.rotation, [-10 * radians, -30 * radians, -20 * radians]);
    assert.equal(result.rotationOrder, "YZX");
    assert.deepEqual(result.scale, [2, 4, 3]);
});

test("selection payload is deterministic and nullable", () => {
    assert.deepEqual(objectSelectionPayload("part-a", "pointer"), {
        objectId: "part-a",
        source: "pointer",
    });
    assert.deepEqual(objectSelectionPayload(null, "unknown"), {
        objectId: null,
        source: "api",
    });
});

test("pointer selection is reported even when the selected object did not change", () => {
    assert.equal(shouldReportObjectSelection(false, "pointer"), true);
    assert.equal(shouldReportObjectSelection(false, "api"), false);
    assert.equal(shouldReportObjectSelection(true, "api"), true);
});

test("only a short stationary primary-pointer gesture is treated as a tap", () => {
    const tap = {
        isPrimary: true,
        button: 0,
        maximumDistance: 8,
        durationMs: 500,
        hadMultiplePointers: false,
        cameraChanged: false,
    };
    assert.equal(pointerGestureIsTap(tap), true);
    assert.equal(pointerGestureIsTap({ ...tap, maximumDistance: 8.1 }), false);
    assert.equal(pointerGestureIsTap({ ...tap, durationMs: 501 }), false);
    assert.equal(pointerGestureIsTap({ ...tap, hadMultiplePointers: true }), false);
    assert.equal(pointerGestureIsTap({ ...tap, cameraChanged: true }), false);
    assert.equal(pointerGestureIsTap({ ...tap, isPrimary: false }), false);
});
