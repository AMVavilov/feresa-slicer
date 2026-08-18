// SPDX-License-Identifier: AGPL-3.0-only

export const DEFAULT_MODEL_TRANSFORM = Object.freeze({
    positionX: 110,
    positionY: 110,
    positionZ: 0,
    rotationXDegrees: 0,
    rotationYDegrees: 0,
    rotationZDegrees: 0,
    scaleX: 1,
    scaleY: 1,
    scaleZ: 1,
    // Backward-compatible aliases. `rotationDegrees` is print-space Z and
    // `scale` applies uniformly when no per-axis scale is supplied.
    rotationDegrees: 0,
    scale: 1,
});

function finiteNumber(value, fallback) {
    const number = Number(value);
    return Number.isFinite(number) ? number : fallback;
}

export function normalizeModelTransform(transform, fallback = DEFAULT_MODEL_TRANSFORM) {
    const source = transform && typeof transform === "object" ? transform : {};
    const base = fallback && typeof fallback === "object" ? fallback : DEFAULT_MODEL_TRANSFORM;
    const hasFinite = (name) => Object.prototype.hasOwnProperty.call(source, name) &&
        Number.isFinite(Number(source[name]));

    const legacyRotationProvided = hasFinite("rotationDegrees");
    const rotationZDegrees = hasFinite("rotationZDegrees")
        ? Number(source.rotationZDegrees)
        : legacyRotationProvided
            ? Number(source.rotationDegrees)
            : finiteNumber(base.rotationZDegrees, finiteNumber(base.rotationDegrees, 0));

    const legacyScaleProvided = hasFinite("scale");
    const legacyScale = legacyScaleProvided
        ? Number(source.scale)
        : finiteNumber(base.scale, 1);
    const axisScale = (name) => hasFinite(name)
        ? Number(source[name])
        : legacyScaleProvided
            ? legacyScale
            : finiteNumber(base[name], legacyScale);

    const normalized = {
        positionX: finiteNumber(source.positionX, base.positionX),
        positionY: finiteNumber(source.positionY, base.positionY),
        positionZ: finiteNumber(source.positionZ, finiteNumber(base.positionZ, 0)),
        rotationXDegrees: finiteNumber(
            source.rotationXDegrees,
            finiteNumber(base.rotationXDegrees, 0),
        ),
        rotationYDegrees: finiteNumber(
            source.rotationYDegrees,
            finiteNumber(base.rotationYDegrees, 0),
        ),
        rotationZDegrees,
        scaleX: axisScale("scaleX"),
        scaleY: axisScale("scaleY"),
        scaleZ: axisScale("scaleZ"),
        rotationDegrees: rotationZDegrees,
        scale: legacyScale,
    };
    if (normalized.scaleX <= 0 || normalized.scaleY <= 0 || normalized.scaleZ <= 0) {
        throw new Error("Model scale must be greater than zero on every axis");
    }
    return normalized;
}

/**
 * Maps the print-space transform into the viewer's axes. STL geometry is
 * normalized as (print X, print Z, print Y), which is a handedness-changing
 * mapping. The YZX Euler order below is therefore equivalent to applying
 * scale -> Rx -> Ry -> Rz -> translation in print space.
 */
export function modelTransformToViewerComponents(transform, bedWidth, bedDepth) {
    const value = normalizeModelTransform(transform);
    const degreesToRadians = Math.PI / 180;
    return {
        position: [
            value.positionX - bedWidth / 2,
            value.positionZ,
            value.positionY - bedDepth / 2,
        ],
        rotation: [
            -value.rotationXDegrees * degreesToRadians,
            -value.rotationZDegrees * degreesToRadians,
            -value.rotationYDegrees * degreesToRadians,
        ],
        rotationOrder: "YZX",
        scale: [value.scaleX, value.scaleZ, value.scaleY],
    };
}

export function normalizeModelObjectsPayload(input, fallbackTransform = DEFAULT_MODEL_TRANSFORM) {
    const payload = typeof input === "string" ? JSON.parse(input) : input;
    const source = Array.isArray(payload) ? { objects: payload } : payload;
    if (!source || !Array.isArray(source.objects)) {
        throw new Error("loadModels expects an objects array");
    }

    const objectIds = new Set();
    const objects = source.objects.map((value, index) => {
        if (!value || typeof value !== "object") {
            throw new Error(`Model descriptor at index ${index} is invalid`);
        }
        const objectId = String(value.objectId ?? "").trim();
        if (!objectId) throw new Error(`Model descriptor at index ${index} has no objectId`);
        if (objectIds.has(objectId)) throw new Error(`Duplicate model objectId: ${objectId}`);
        objectIds.add(objectId);

        const url = String(value.url ?? "").trim();
        if (!url) throw new Error(`Model ${objectId} has no URL`);
        return {
            objectId,
            url,
            transform: normalizeModelTransform(value.transform, fallbackTransform),
            visible: value.visible !== false,
        };
    });

    const requestedSelection = source.selectedObjectId == null
        ? null
        : String(source.selectedObjectId);
    const selectedObjectId = objectIds.has(requestedSelection)
        ? requestedSelection
        : objects[0]?.objectId ?? null;

    return {
        version: String(source.version ?? Date.now()),
        objects,
        selectedObjectId,
        frameAll: source.frameAll !== false,
    };
}

export function objectSelectionPayload(objectId, source = "api") {
    return {
        objectId: objectId == null ? null : String(objectId),
        source: source === "pointer" ? "pointer" : "api",
    };
}
