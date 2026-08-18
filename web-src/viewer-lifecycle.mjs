// SPDX-License-Identifier: AGPL-3.0-only

/**
 * Tracks the latest asynchronous request without relying on caller-supplied
 * cache-busting versions. A request may update viewer state only while its
 * token is current.
 */
export function createLatestRequestGate() {
    let generation = 0;
    return {
        begin() {
            generation += 1;
            return generation;
        },
        invalidate() {
            generation += 1;
        },
        isCurrent(token) {
            return token === generation;
        },
    };
}

/**
 * Releases GPU resources owned by an Object3D subtree. Geometry or material
 * instances shared by more than one child are disposed exactly once.
 */
export function disposeObject3DResources(root) {
    if (!root?.traverse) return;
    const geometries = new Set();
    const materials = new Set();
    const textures = new Set();

    root.traverse((object) => {
        const geometry = object?.geometry;
        if (geometry?.dispose && !geometries.has(geometry)) {
            geometries.add(geometry);
            geometry.dispose();
        }

        const objectMaterials = Array.isArray(object?.material)
            ? object.material
            : object?.material
                ? [object.material]
                : [];
        for (const material of objectMaterials) {
            if (!material || materials.has(material)) continue;
            materials.add(material);
            for (const value of Object.values(material)) {
                if (value?.isTexture && value.dispose && !textures.has(value)) {
                    textures.add(value);
                    value.dispose();
                }
            }
            material.dispose?.();
        }
    });
}
