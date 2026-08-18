// SPDX-License-Identifier: AGPL-3.0-only
import assert from "node:assert/strict";
import test from "node:test";
import {
    createLatestRequestGate,
    disposeObject3DResources,
} from "./viewer-lifecycle.mjs";

test("invalidating a request prevents a stale model load from committing", () => {
    const gate = createLatestRequestGate();
    const loadingModel = gate.begin();

    gate.invalidate();

    assert.equal(gate.isCurrent(loadingModel), false);
    const replacementModel = gate.begin();
    assert.equal(gate.isCurrent(replacementModel), true);
});

test("a failed old request cannot clear a newer toolpath", () => {
    const gate = createLatestRequestGate();
    const oldRequest = gate.begin();
    const currentRequest = gate.begin();

    assert.equal(gate.isCurrent(oldRequest), false);
    assert.equal(gate.isCurrent(currentRequest), true);
});

test("disposing an Object3D subtree releases shared GPU resources once", () => {
    const calls = { geometry: 0, material: 0, texture: 0 };
    const geometry = { dispose: () => { calls.geometry += 1; } };
    const texture = { isTexture: true, dispose: () => { calls.texture += 1; } };
    const material = {
        map: texture,
        dispose: () => { calls.material += 1; },
    };
    const nodes = [
        { geometry, material },
        { geometry, material: [material] },
    ];
    const root = { traverse: (visit) => nodes.forEach(visit) };

    disposeObject3DResources(root);

    assert.deepEqual(calls, { geometry: 1, material: 1, texture: 1 });
});

test("disposing an empty resource tree is a no-op", () => {
    assert.doesNotThrow(() => disposeObject3DResources(null));
    assert.doesNotThrow(() => disposeObject3DResources({}));
});
