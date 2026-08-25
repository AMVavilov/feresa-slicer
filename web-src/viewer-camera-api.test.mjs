// SPDX-License-Identifier: AGPL-3.0-only
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const viewerSource = readFileSync(new URL("./viewer.js", import.meta.url), "utf8");
const viewerHtml = readFileSync(
    new URL("../app/src/main/assets/viewer/index.html", import.meta.url),
    "utf8",
);

test("viewer publishes the camera framing and persistence API", () => {
    for (const method of [
        "getCameraState",
        "restoreCameraState",
        "fitModels",
        "fitSelectedModel",
        "showWholeBed",
    ]) {
        assert.match(
            viewerSource,
            new RegExp(`\\n\\s*${method}(?::|,)`),
            `${method} must remain exposed through window.FeresaSlicerViewer`,
        );
    }
});

test("manual OrbitControls interaction reports a free camera state", () => {
    assert.match(viewerSource, /controls\.addEventListener\("start", onCameraControlsStart\)/);
    assert.match(viewerSource, /controls\.addEventListener\("end", onCameraControlsEnd\)/);
    assert.match(viewerSource, /activeCameraPreset = null;\s*reportCameraState\("manual", true\)/);
    assert.match(viewerSource, /window\.AndroidBridge\.onCameraState\(JSON\.stringify\(payload\)\)/);
    assert.match(viewerSource, /mode: activeCameraPreset == null \? "free" : "preset"/);
});

test("embedded fit control declares its scene target", () => {
    assert.match(viewerHtml, /aria-controls="scene"/);
    assert.match(viewerHtml, /data-camera-action="fit-models"/);
});

test("hidden viewer chrome removes both status and legacy fit controls", () => {
    assert.match(
        viewerHtml,
        /:root\[data-viewer-status="hidden"\] #status,\s*:root\[data-viewer-status="hidden"\] #reset \{ display: none; \}/,
    );
});

test("viewer ready callback cannot overwrite a saved camera before scene restoration", () => {
    const bootstrapBlock = viewerSource.match(
        /if \(initRenderer\(\)\) \{([\s\S]*?)\n\}/,
    )?.[1];
    assert.ok(bootstrapBlock, "viewer bootstrap block must remain discoverable");
    assert.doesNotMatch(bootstrapBlock, /reportCameraState\(/);
    assert.match(bootstrapBlock, /window\.AndroidBridge\?\.onReady/);
});

test("scene settling restores a saved camera or auto-fits before reporting state", () => {
    const settleBlock = viewerSource.match(
        /function settleCameraAfterSceneChange\([\s\S]*?\n\}/,
    )?.[0];
    assert.ok(settleBlock, "camera settle function must remain discoverable");
    assert.match(settleBlock, /if \(shouldFrame\) \{[\s\S]*?frameAll\(/);
    assert.match(settleBlock, /if \(restoredState != null\) \{[\s\S]*?restoreCameraState\(/);
    assert.ok(
        settleBlock.indexOf("frameAll(") < settleBlock.indexOf("restoreCameraState("),
        "saved camera must win over the provisional auto-fit",
    );
    assert.ok(
        settleBlock.indexOf("restoreCameraState(") < settleBlock.lastIndexOf("reportCameraState("),
        "camera state must be reported only after restoration",
    );
});

test("toolpath preview allocates compact GPU buffers and reports a completed render", () => {
    assert.match(viewerSource, /const positions = new Float32Array\(visible\.length \* 6\)/);
    assert.match(viewerSource, /const colors = new Float32Array\(visible\.length \* 6\)/);
    assert.match(viewerSource, /new THREE\.BufferAttribute\(positions, 3\)/);
    assert.match(viewerSource, /new THREE\.BufferAttribute\(colors, 3\)/);
    assert.match(viewerSource, /requestAnimationFrame\(\(\) => \{/);
    assert.match(viewerSource, /window\.AndroidBridge\.onToolpathRendered\(toolpathSegmentCount\)/);
});
