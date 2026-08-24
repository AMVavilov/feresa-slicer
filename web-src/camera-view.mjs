// SPDX-License-Identifier: AGPL-3.0-only

const CAMERA_VIEW_DEFINITIONS = Object.freeze({
    isometric: Object.freeze({
        direction: Object.freeze([1, 0.82, 1.16]),
        up: Object.freeze([0, 1, 0]),
    }),
    top: Object.freeze({
        direction: Object.freeze([0, 1, 0]),
        // Keep +X pointing right while looking down at the XZ print bed.
        up: Object.freeze([0, 0, -1]),
    }),
    bottom: Object.freeze({
        direction: Object.freeze([0, -1, 0]),
        // The opposite up vector preserves +X pointing right from below.
        up: Object.freeze([0, 0, 1]),
    }),
    front: Object.freeze({
        direction: Object.freeze([0, 0, 1]),
        up: Object.freeze([0, 1, 0]),
    }),
    back: Object.freeze({
        direction: Object.freeze([0, 0, -1]),
        up: Object.freeze([0, 1, 0]),
    }),
    left: Object.freeze({
        direction: Object.freeze([-1, 0, 0]),
        up: Object.freeze([0, 1, 0]),
    }),
    right: Object.freeze({
        direction: Object.freeze([1, 0, 0]),
        up: Object.freeze([0, 1, 0]),
    }),
});

export const CAMERA_VIEW_PRESETS = Object.freeze(Object.keys(CAMERA_VIEW_DEFINITIONS));

function vectorLengthSquared(vector) {
    return vector.reduce((sum, component) => sum + component * component, 0);
}

function crossProductLengthSquared(left, right) {
    const x = left[1] * right[2] - left[2] * right[1];
    const y = left[2] * right[0] - left[0] * right[2];
    const z = left[0] * right[1] - left[1] * right[0];
    return x * x + y * y + z * z;
}

function validateCameraViewDefinition(preset, definition) {
    for (const [name, vector] of Object.entries(definition)) {
        if (!Array.isArray(vector) || vector.length !== 3 || !vector.every(Number.isFinite)) {
            throw new TypeError(`Camera view '${preset}' has an invalid ${name} vector`);
        }
        if (vectorLengthSquared(vector) <= Number.EPSILON) {
            throw new RangeError(`Camera view '${preset}' has a zero-length ${name} vector`);
        }
    }

    const directionLengthSquared = vectorLengthSquared(definition.direction);
    const upLengthSquared = vectorLengthSquared(definition.up);
    const relativeCrossLengthSquared = crossProductLengthSquared(
        definition.direction,
        definition.up,
    ) / (directionLengthSquared * upLengthSquared);
    if (relativeCrossLengthSquared <= 1e-12) {
        throw new RangeError(`Camera view '${preset}' has collinear direction and up vectors`);
    }
}

for (const [preset, definition] of Object.entries(CAMERA_VIEW_DEFINITIONS)) {
    validateCameraViewDefinition(preset, definition);
}

export function normalizeCameraViewPreset(value) {
    const preset = String(value ?? "").trim().toLowerCase();
    if (!Object.hasOwn(CAMERA_VIEW_DEFINITIONS, preset)) {
        throw new TypeError(`Unsupported camera view preset: ${value}`);
    }
    return preset;
}

export function cameraViewDefinition(value) {
    const preset = normalizeCameraViewPreset(value);
    const definition = CAMERA_VIEW_DEFINITIONS[preset];
    return {
        preset,
        direction: [...definition.direction],
        up: [...definition.up],
    };
}

/**
 * Keeps the requested camera view independent from the current scene contents.
 * Model loading temporarily reframes the scene, so the viewer can restore this
 * preference after an asynchronous load finishes without replaying a stale
 * Android request.
 */
export function createCameraViewPreference(initialPreset = "isometric") {
    let selectedPreset = normalizeCameraViewPreset(initialPreset);
    return Object.freeze({
        select(value) {
            selectedPreset = normalizeCameraViewPreset(value);
            return cameraViewDefinition(selectedPreset);
        },
        current() {
            return cameraViewDefinition(selectedPreset);
        },
    });
}

/**
 * Returns the camera-to-target distance needed to contain a bounding sphere.
 * The narrower of the vertical and horizontal fields of view controls fitting,
 * so the result remains framed in both portrait and landscape viewports.
 */
export function cameraDistanceToFrameSphere({
    radius,
    verticalFovDegrees,
    aspect,
    margin = 1.12,
}) {
    const safeRadius = Math.max(0, Number(radius) || 0);
    const safeVerticalFov = Math.min(179, Math.max(1, Number(verticalFovDegrees) || 38));
    const safeAspect = Math.max(0.01, Number(aspect) || 1);
    const safeMargin = Math.max(1, Number(margin) || 1);
    const verticalHalfFov = safeVerticalFov * Math.PI / 360;
    const horizontalHalfFov = Math.atan(Math.tan(verticalHalfFov) * safeAspect);
    const limitingHalfFov = Math.min(verticalHalfFov, horizontalHalfFov);
    return safeRadius === 0 ? 0 : safeRadius * safeMargin / Math.sin(limitingHalfFov);
}
