// SPDX-License-Identifier: AGPL-3.0-only
import assert from "node:assert/strict";
import test from "node:test";
import * as THREE from "three";
import { modelTransformToViewerComponents } from "./model-scene-contract.mjs";

const EPSILON = 1e-9;

function assertVectorClose(actual, expected) {
    for (let index = 0; index < 3; index += 1) {
        assert.ok(
            Math.abs(actual[index] - expected[index]) <= EPSILON,
            `${actual[index]} differs from ${expected[index]} at axis ${index}`,
        );
    }
}

function applyPrintTransform(point, transform) {
    const value = new THREE.Vector3(
        point[0] * transform.scaleX,
        point[1] * transform.scaleY,
        point[2] * transform.scaleZ,
    );
    value.applyAxisAngle(new THREE.Vector3(1, 0, 0), THREE.MathUtils.degToRad(transform.rotationXDegrees));
    value.applyAxisAngle(new THREE.Vector3(0, 1, 0), THREE.MathUtils.degToRad(transform.rotationYDegrees));
    value.applyAxisAngle(new THREE.Vector3(0, 0, 1), THREE.MathUtils.degToRad(transform.rotationZDegrees));
    value.add(new THREE.Vector3(transform.positionX, transform.positionY, transform.positionZ));
    return value;
}

test("viewer matrix matches print scale -> Rx -> Ry -> Rz -> translation", () => {
    const transform = {
        positionX: 130,
        positionY: 140,
        positionZ: 7,
        rotationXDegrees: 10,
        rotationYDegrees: 20,
        rotationZDegrees: 30,
        scaleX: 2,
        scaleY: 3,
        scaleZ: 4,
    };
    const components = modelTransformToViewerComponents(transform, 220, 240);
    const object = new THREE.Object3D();
    object.position.fromArray(components.position);
    object.rotation.fromArray([...components.rotation, components.rotationOrder]);
    object.scale.fromArray(components.scale);
    object.updateMatrixWorld(true);

    const printPoint = [1, 2, 3];
    const viewerPoint = new THREE.Vector3(printPoint[0], printPoint[2], printPoint[1])
        .applyMatrix4(object.matrixWorld)
        .toArray();
    const expectedPrintPoint = applyPrintTransform(printPoint, transform);
    const expectedViewerPoint = [
        expectedPrintPoint.x - 110,
        expectedPrintPoint.z,
        expectedPrintPoint.y - 120,
    ];

    assertVectorClose(viewerPoint, expectedViewerPoint);
});

test("Three.js bounds include every transformed corner for non-uniform XYZ transforms", () => {
    const transform = {
        positionX: 100,
        positionY: 90,
        positionZ: 3,
        rotationXDegrees: 27,
        rotationYDegrees: 41,
        rotationZDegrees: 13,
        scaleX: 1.5,
        scaleY: 0.75,
        scaleZ: 2.25,
    };
    const printCorners = [];
    for (const x of [-4, 4]) {
        for (const y of [-6, 6]) {
            for (const z of [0, 8]) printCorners.push([x, y, z]);
        }
    }
    const geometry = new THREE.BufferGeometry().setAttribute(
        "position",
        new THREE.Float32BufferAttribute(
            printCorners.flatMap(([x, y, z]) => [x, z, y]),
            3,
        ),
    );
    const points = new THREE.Points(geometry, new THREE.PointsMaterial());
    const components = modelTransformToViewerComponents(transform, 220, 220);
    points.position.fromArray(components.position);
    points.rotation.fromArray([...components.rotation, components.rotationOrder]);
    points.scale.fromArray(components.scale);
    points.updateMatrixWorld(true);

    const actualBounds = new THREE.Box3().setFromObject(points);
    const expectedViewerCorners = printCorners.map((point) => {
        const value = applyPrintTransform(point, transform);
        return new THREE.Vector3(value.x - 110, value.z, value.y - 110);
    });
    const expectedBounds = new THREE.Box3().setFromPoints(expectedViewerCorners);

    assertVectorClose(actualBounds.min.toArray(), expectedBounds.min.toArray());
    assertVectorClose(actualBounds.max.toArray(), expectedBounds.max.toArray());
});
