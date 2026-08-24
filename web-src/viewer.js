// SPDX-License-Identifier: AGPL-3.0-only
import * as THREE from "three";
import { OrbitControls } from "three/addons/controls/OrbitControls.js";
import { STLLoader } from "three/addons/loaders/STLLoader.js";
import {
    modelTransformToViewerComponents,
    normalizeModelObjectsPayload,
    normalizeModelTransform,
    objectSelectionPayload,
} from "./model-scene-contract.mjs";
import {
    lineTypeColor,
    maximumExtrusionSpeed,
    parseToolpathDetailed,
    selectVisibleToolpathSegments,
    toolpathSelectionPayload,
} from "./toolpath-parser.mjs";
import {
    createLatestRequestGate,
    disposeObject3DResources,
} from "./viewer-lifecycle.mjs";
import {
    cameraDistanceToFrameSphere,
    createCameraViewPreference,
} from "./camera-view.mjs";

const canvas = document.getElementById("scene");
const statusElement = document.getElementById("status");
const fallbackElement = document.getElementById("fallback");
const resetButton = document.getElementById("reset");
const queryParams = new URLSearchParams(window.location.search);
const cameraViewPreference = createCameraViewPreference();
const viewerLanguage = queryParams.get("lang") === "ru" ? "ru" : "en";
const viewerText = {
    ru: {
        waitingModel: "Загрузите STL-модель",
        loadingModel: "Загрузка STL…",
        loadingModels: "Загрузка STL-моделей…",
        gestureHint: "Проведите для вращения · сведите пальцы для масштаба",
        resetCamera: "Сбросить вид",
        previewUnavailable: "3D-просмотр недоступен. Числовые настройки положения остаются доступны.",
        noVisibleToolpaths: "Нет видимых траекторий",
        loadingGcode: "Загрузка просмотра G-code…",
        selected: "Выбрано",
        models: "моделей · коснитесь модели для выбора",
        segments: "сегментов",
        toolpathSegments: "сегментов траектории",
        layers: "слои",
    },
    en: {
        waitingModel: "Load an STL model",
        loadingModel: "Loading STL…",
        loadingModels: "Loading STL models…",
        gestureHint: "Drag to rotate · pinch to zoom",
        resetCamera: "Reset view",
        previewUnavailable: "3D preview is unavailable. Numeric placement controls remain available.",
        noVisibleToolpaths: "No visible toolpaths",
        loadingGcode: "Loading G-code preview…",
        selected: "Selected",
        models: "models · tap a model to select",
        segments: "segments",
        toolpathSegments: "toolpath segments",
        layers: "layers",
    },
}[viewerLanguage];

statusElement.textContent = viewerText.waitingModel;
resetButton.textContent = viewerText.resetCamera;
fallbackElement.textContent = viewerText.previewUnavailable;

let renderer;
let camera;
let controls;
const modelObjects = new Map();
let selectedObjectId = null;
let toolpathLines = null;
let toolpathData = null;
let toolpathSegmentCount = 0;
let toolpathEligibleSegmentCount = 0;
const modelRequestGate = createLatestRequestGate();
const gcodeRequestGate = createLatestRequestGate();
let viewMode = "model";
let bedWidth = 220;
let bedDepth = 220;
let bedGroup = null;
let renderQueued = false;
let cameraTransitionFrame = null;
let cameraTransitionGeneration = 0;
let darkTheme = queryParams.get("theme") === "dark";
const toolpathPreview = {
    minimumLayer: 0,
    maximumLayer: Number.MAX_SAFE_INTEGER,
    colorMode: "lineType",
    maximumSegmentRatio: 1,
    showExtrusion: true,
    showTravel: false,
};

const transformState = {
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
};

const LEGACY_OBJECT_ID = "current";
const raycaster = new THREE.Raycaster();
const pointerPosition = new THREE.Vector2();
const activePointers = new Set();
let pointerDown = null;
let gestureHadMultiplePointers = false;

const scene = new THREE.Scene();
scene.background = new THREE.Color(darkTheme ? 0x202421 : 0xeef1ec);

function render() {
    if (renderer && camera) renderer.render(scene, camera);
}

function requestRender() {
    if (renderQueued) return;
    renderQueued = true;
    requestAnimationFrame(() => {
        renderQueued = false;
        render();
    });
}

function reportError(message) {
    statusElement.textContent = message;
    statusElement.style.color = "#8b1f17";
    if (window.AndroidBridge?.onError) window.AndroidBridge.onError(String(message));
}

function initRenderer() {
    try {
        renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: false });
        renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
        renderer.outputColorSpace = THREE.SRGBColorSpace;
        renderer.shadowMap.enabled = false;
    } catch (error) {
        fallbackElement.style.display = "flex";
        reportError(`WebGL unavailable: ${error.message}`);
        return false;
    }

    camera = new THREE.PerspectiveCamera(38, 1, 0.1, 3000);
    controls = new OrbitControls(camera, canvas);
    controls.enableDamping = false;
    controls.screenSpacePanning = false;
    controls.minDistance = 25;
    controls.maxDistance = 1200;
    controls.minPolarAngle = 0;
    controls.maxPolarAngle = Math.PI;
    controls.addEventListener("change", requestRender);

    scene.add(new THREE.HemisphereLight(0xffffff, 0x718078, 2.3));
    const keyLight = new THREE.DirectionalLight(0xffffff, 2.2);
    keyLight.position.set(180, 260, 120);
    scene.add(keyLight);

    rebuildBed();
    resize();
    resetCamera();
    return true;
}

function rebuildBed() {
    if (bedGroup) {
        scene.remove(bedGroup);
        disposeObject3DResources(bedGroup);
    }
    bedGroup = new THREE.Group();

    const plate = new THREE.Mesh(
        new THREE.PlaneGeometry(bedWidth, bedDepth),
        new THREE.MeshStandardMaterial({ color: darkTheme ? 0x2c322e : 0xfafbf8, roughness: 0.9, metalness: 0.0 })
    );
    plate.rotation.x = -Math.PI / 2;
    plate.position.y = -0.15;
    bedGroup.add(plate);

    const divisions = Math.max(2, Math.round(Math.max(bedWidth, bedDepth) / 10));
    const grid = new THREE.GridHelper(
        Math.max(bedWidth, bedDepth),
        divisions,
        darkTheme ? 0x78aa9d : 0x81948b,
        darkTheme ? 0x43514a : 0xcbd4ce,
    );
    grid.position.y = 0;
    bedGroup.add(grid);

    const outlinePoints = [
        new THREE.Vector3(-bedWidth / 2, 0.1, -bedDepth / 2),
        new THREE.Vector3(bedWidth / 2, 0.1, -bedDepth / 2),
        new THREE.Vector3(bedWidth / 2, 0.1, bedDepth / 2),
        new THREE.Vector3(-bedWidth / 2, 0.1, bedDepth / 2),
        new THREE.Vector3(-bedWidth / 2, 0.1, -bedDepth / 2),
    ];
    const outline = new THREE.Line(
        new THREE.BufferGeometry().setFromPoints(outlinePoints),
        new THREE.LineBasicMaterial({ color: 0x006b57 })
    );
    bedGroup.add(outline);
    scene.add(bedGroup);
    requestRender();
}

function resetCamera() {
    if (!camera || !controls) return;
    cancelCameraTransition();
    const span = Math.max(bedWidth, bedDepth);
    camera.position.set(span * 0.72, span * 0.62, span * 0.82);
    camera.up.set(0, 1, 0);
    controls.target.set(0, 12, 0);
    controls.update();
    requestRender();
}

function visibleModelRecords() {
    return [...modelObjects.values()].filter((record) => record.visible);
}

function modelBounds() {
    const bounds = new THREE.Box3();
    let hasBounds = false;
    for (const record of visibleModelRecords()) {
        record.group.updateMatrixWorld(true);
        bounds.expandByObject(record.group);
        hasBounds = true;
    }
    return hasBounds && !bounds.isEmpty() ? bounds : null;
}

function frameAll() {
    if (!camera || !controls) return;
    cancelCameraTransition();
    const bounds = modelBounds();
    if (!bounds) {
        resetCamera();
        return;
    }
    const size = bounds.getSize(new THREE.Vector3());
    const center = bounds.getCenter(new THREE.Vector3());
    const maximumDimension = Math.max(size.x, size.y, size.z, 10);
    const verticalDistance = maximumDimension /
        (2 * Math.tan(THREE.MathUtils.degToRad(camera.fov * 0.5)));
    const horizontalDistance = verticalDistance / Math.max(camera.aspect, 0.25);
    const distance = Math.max(verticalDistance, horizontalDistance) * 1.45;
    const direction = new THREE.Vector3(1, 0.82, 1.16).normalize();
    camera.position.copy(center).addScaledVector(direction, distance);
    camera.near = Math.max(0.1, distance / 1000);
    camera.far = Math.max(3000, distance * 20);
    camera.updateProjectionMatrix();
    controls.target.copy(center);
    controls.update();
    requestRender();
}

function activeViewBounds() {
    if (viewMode === "toolpath" && toolpathLines) {
        const toolpathBounds = new THREE.Box3().setFromObject(toolpathLines);
        if (!toolpathBounds.isEmpty()) return toolpathBounds;
    }
    const bounds = modelBounds();
    if (bounds) return bounds;
    return new THREE.Box3(
        new THREE.Vector3(-bedWidth / 2, 0, -bedDepth / 2),
        new THREE.Vector3(bedWidth / 2, 0, bedDepth / 2),
    );
}

function cameraFramingRadius(bounds, target) {
    const sphere = bounds.getBoundingSphere(new THREE.Sphere());
    return sphere.radius + sphere.center.distanceTo(target);
}

function cancelCameraTransition() {
    cameraTransitionGeneration += 1;
    if (cameraTransitionFrame != null) cancelAnimationFrame(cameraTransitionFrame);
    cameraTransitionFrame = null;
    if (controls) controls.enabled = true;
}

function finishCameraView(position, target, up) {
    camera.position.copy(position);
    camera.up.copy(up);
    controls.target.copy(target);
    camera.lookAt(target);
    controls.update();
    controls.enabled = true;
    cameraTransitionFrame = null;
    requestRender();
}

/**
 * Rotates the camera to a named view while retaining the current orbit target
 * and zoom. The distance only grows when required to keep the active models,
 * toolpath, or (for an empty scene) print bed inside the viewport.
 */
function applyCameraViewDefinition(definition, options = {}) {
    if (!camera || !controls) return false;
    const direction = new THREE.Vector3(...definition.direction).normalize();
    const up = new THREE.Vector3(...definition.up).normalize();
    const target = controls.target.clone();
    const currentDistance = Math.max(camera.position.distanceTo(target), controls.minDistance);
    const radius = cameraFramingRadius(activeViewBounds(), target);
    const framingDistance = cameraDistanceToFrameSphere({
        radius,
        verticalFovDegrees: camera.fov,
        aspect: camera.aspect,
        margin: options.margin,
    });
    const distance = Math.max(currentDistance, framingDistance, controls.minDistance);
    const destination = target.clone().addScaledVector(direction, distance);
    camera.near = Math.max(0.1, distance / 1000);
    camera.far = Math.max(3000, distance * 20);
    camera.updateProjectionMatrix();
    controls.maxDistance = Math.max(1200, distance * 1.25);

    const durationMs = Math.max(0, Number(options.durationMs ?? 320) || 0);
    cancelCameraTransition();
    if (durationMs === 0) {
        finishCameraView(destination, target, up);
        return true;
    }

    const transitionGeneration = cameraTransitionGeneration;
    const startTime = performance.now();
    const startPosition = camera.position.clone();
    const startQuaternion = camera.quaternion.clone();
    const destinationCamera = camera.clone();
    destinationCamera.position.copy(destination);
    destinationCamera.up.copy(up);
    destinationCamera.lookAt(target);
    const destinationQuaternion = destinationCamera.quaternion.clone();
    controls.enabled = false;

    const animateCamera = (time) => {
        if (transitionGeneration !== cameraTransitionGeneration) return;
        const progress = Math.min(1, Math.max(0, (time - startTime) / durationMs));
        const eased = 1 - Math.pow(1 - progress, 3);
        camera.position.lerpVectors(startPosition, destination, eased);
        camera.quaternion.slerpQuaternions(startQuaternion, destinationQuaternion, eased);
        requestRender();
        if (progress < 1) {
            cameraTransitionFrame = requestAnimationFrame(animateCamera);
        } else {
            finishCameraView(destination, target, up);
        }
    };
    cameraTransitionFrame = requestAnimationFrame(animateCamera);
    return true;
}

function setCameraView(preset, options = {}) {
    return applyCameraViewDefinition(cameraViewPreference.select(preset), options);
}

function restoreCameraViewAfterFraming() {
    const definition = cameraViewPreference.current();
    if (definition.preset !== "isometric") {
        applyCameraViewDefinition(definition, { durationMs: 0 });
    }
}

function transformGeometryFromStl(geometry) {
    geometry.computeBoundingBox();
    const bounds = geometry.boundingBox;
    if (!bounds || bounds.isEmpty()) throw new Error("STL geometry is empty");
    const centerX = (bounds.min.x + bounds.max.x) / 2;
    const centerY = (bounds.min.y + bounds.max.y) / 2;
    const minimumZ = bounds.min.z;
    const positions = geometry.getAttribute("position");
    for (let index = 0; index < positions.count; index += 1) {
        const stlX = positions.getX(index);
        const stlY = positions.getY(index);
        const stlZ = positions.getZ(index);
        positions.setXYZ(index, stlX - centerX, stlZ - minimumZ, stlY - centerY);
    }
    positions.needsUpdate = true;
    geometry.computeVertexNormals();
    geometry.computeBoundingBox();
    geometry.computeBoundingSphere();
}

function createModelRecord(descriptor, geometry) {
    const mesh = new THREE.Mesh(
        geometry,
        new THREE.MeshStandardMaterial({
            color: 0x2a9d83,
            emissive: 0x000000,
            roughness: 0.52,
            metalness: 0.03,
            side: THREE.DoubleSide,
        }),
    );
    const edges = new THREE.LineSegments(
        new THREE.EdgesGeometry(geometry, 32),
        new THREE.LineBasicMaterial({ color: 0x134d42, transparent: true, opacity: 0.5 }),
    );
    const group = new THREE.Group();
    group.add(mesh, edges);
    group.userData.objectId = descriptor.objectId;
    mesh.userData.objectId = descriptor.objectId;
    return {
        ...descriptor,
        group,
        mesh,
        edges,
        insideBed: true,
    };
}

function disposeModelRecord(record) {
    scene.remove(record.group);
    disposeObject3DResources(record.group);
}

function disposeLoadedModels() {
    for (const record of modelObjects.values()) disposeModelRecord(record);
    modelObjects.clear();
    selectedObjectId = null;
}

function clearModels() {
    modelRequestGate.invalidate();
    disposeLoadedModels();
    clearToolpath();
    setViewMode("model");
    reportObjectSelection(null, "api");
    statusElement.style.color = "#4f5852";
    statusElement.textContent = viewerText.waitingModel;
    frameAll();
    restoreCameraViewAfterFraming();
}

function modelRequestUrl(url, version) {
    const resolved = new URL(url, window.location.href);
    if (resolved.origin !== window.location.origin) {
        throw new Error("STL URL must use the viewer origin");
    }
    resolved.searchParams.set("v", version);
    return resolved.href;
}

async function loadModels(input) {
    const payload = normalizeModelObjectsPayload(input, transformState);
    const requestToken = modelRequestGate.begin();
    clearToolpath();
    setViewMode("model");
    statusElement.style.color = "#4f5852";
    statusElement.textContent = payload.objects.length > 1
        ? `${viewerText.loadingModels} (${payload.objects.length})`
        : payload.objects.length === 1
            ? viewerText.loadingModel
            : viewerText.waitingModel;

    if (payload.objects.length === 0) {
        disposeLoadedModels();
        reportObjectSelection(null, "api");
        frameAll();
        restoreCameraViewAfterFraming();
        return;
    }

    try {
        const buffers = await Promise.all(payload.objects.map(async (descriptor) => {
            const response = await fetch(modelRequestUrl(descriptor.url, payload.version), { cache: "no-store" });
            if (!response.ok) {
                throw new Error(`STL request failed for ${descriptor.objectId} (${response.status})`);
            }
            return response.arrayBuffer();
        }));
        if (!modelRequestGate.isCurrent(requestToken)) return;

        const loadedRecords = payload.objects.map((descriptor, index) => {
            const geometry = new STLLoader().parse(buffers[index]);
            transformGeometryFromStl(geometry);
            return createModelRecord(descriptor, geometry);
        });

        disposeLoadedModels();
        for (const record of loadedRecords) {
            modelObjects.set(record.objectId, record);
            scene.add(record.group);
            applyObjectTransform(record, record.transform, false);
        }
        setViewMode(viewMode);
        selectObject(payload.selectedObjectId, true, "api");
        reportSceneState(true);
        if (payload.frameAll) {
            frameAll();
            restoreCameraViewAfterFraming();
        }
        statusElement.textContent = payload.objects.length > 1
            ? `${payload.objects.length} ${viewerText.models}`
            : viewerText.gestureHint;
        requestRender();
    } catch (error) {
        if (!modelRequestGate.isCurrent(requestToken)) return;
        disposeLoadedModels();
        reportObjectSelection(null, "api");
        reportError(error.message || "Cannot display STL model");
    }
}

async function loadModel(version = Date.now()) {
    return loadModels({
        version,
        objects: [{
            objectId: LEGACY_OBJECT_ID,
            url: "../../model/current.stl",
            transform: transformState,
        }],
        selectedObjectId: LEGACY_OBJECT_ID,
        frameAll: true,
    });
}

function applyObjectTransform(record, transform, notify = true) {
    record.transform = normalizeModelTransform(transform, record.transform);
    const viewerTransform = modelTransformToViewerComponents(
        record.transform,
        bedWidth,
        bedDepth,
    );
    record.group.position.fromArray(viewerTransform.position);
    record.group.rotation.order = viewerTransform.rotationOrder;
    record.group.rotation.fromArray([
        ...viewerTransform.rotation,
        viewerTransform.rotationOrder,
    ]);
    record.group.scale.fromArray(viewerTransform.scale);
    record.group.updateMatrixWorld(true);
    if (notify) reportSceneState(true);
    requestRender();
}

function applyTransform(notify = true) {
    const record = modelObjects.get(LEGACY_OBJECT_ID) ?? modelObjects.get(selectedObjectId);
    if (!record) return;
    applyObjectTransform(record, transformState, notify);
}

function boundsPayload(bounds) {
    const insideBed = bounds.min.x >= -bedWidth / 2 - 0.001 &&
        bounds.max.x <= bedWidth / 2 + 0.001 &&
        bounds.min.z >= -bedDepth / 2 - 0.001 &&
        bounds.max.z <= bedDepth / 2 + 0.001 &&
        bounds.min.y >= -0.001;
    return {
        minimumX: bounds.min.x + bedWidth / 2,
        maximumX: bounds.max.x + bedWidth / 2,
        minimumY: bounds.min.z + bedDepth / 2,
        maximumY: bounds.max.z + bedDepth / 2,
        minimumZ: bounds.min.y,
        maximumZ: bounds.max.y,
        height: bounds.max.y,
        insideBed,
    };
}

function updateModelAppearance(record) {
    const selected = record.objectId === selectedObjectId;
    record.mesh.material.color.setHex(record.insideBed
        ? (selected ? 0x35b99c : 0x2a9d83)
        : (selected ? 0xef6a59 : 0xd45445));
    record.mesh.material.emissive.setHex(selected ? 0x164f43 : 0x000000);
    record.mesh.material.emissiveIntensity = selected ? 0.42 : 0;
    record.edges.material.color.setHex(selected ? 0xffc857 : 0x134d42);
    record.edges.material.opacity = selected ? 1 : 0.5;
}

function reportSceneState(notify = true) {
    const aggregateBounds = modelBounds();
    if (!aggregateBounds) return;
    const objects = [];
    for (const record of visibleModelRecords()) {
        const bounds = new THREE.Box3().setFromObject(record.group);
        const objectPayload = boundsPayload(bounds);
        record.insideBed = objectPayload.insideBed;
        updateModelAppearance(record);
        objects.push({ objectId: record.objectId, ...objectPayload });
    }
    const payload = {
        ...boundsPayload(aggregateBounds),
        objectId: selectedObjectId,
        objectCount: objects.length,
        objects,
    };
    if (notify && window.AndroidBridge?.onSceneState) {
        window.AndroidBridge.onSceneState(JSON.stringify(payload));
    }
}

function reportObjectSelection(objectId, source) {
    if (window.AndroidBridge?.onObjectSelected) {
        window.AndroidBridge.onObjectSelected(JSON.stringify(objectSelectionPayload(objectId, source)));
    }
}

function selectObject(objectId, notify = true, source = "api") {
    const normalizedObjectId = objectId != null && modelObjects.has(String(objectId))
        ? String(objectId)
        : null;
    const changed = selectedObjectId !== normalizedObjectId;
    selectedObjectId = normalizedObjectId;
    for (const record of modelObjects.values()) updateModelAppearance(record);
    if (notify && changed) reportObjectSelection(selectedObjectId, source);
    requestRender();
    return selectedObjectId;
}

function updateObjectTransform(objectId, payload) {
    const record = modelObjects.get(String(objectId));
    if (!record) throw new Error(`Unknown model objectId: ${objectId}`);
    applyObjectTransform(record, payload, true);
}

function updateTransform(payload) {
    Object.assign(transformState, normalizeModelTransform(payload, transformState));
    applyTransform(true);
}

function setBed(width, depth) {
    const nextWidth = Math.max(10, Number(width) || 220);
    const nextDepth = Math.max(10, Number(depth) || 220);
    const dimensionsChanged = nextWidth !== bedWidth || nextDepth !== bedDepth;
    bedWidth = nextWidth;
    bedDepth = nextDepth;
    if (dimensionsChanged) rebuildBed();
    for (const record of modelObjects.values()) applyObjectTransform(record, record.transform, false);
    reportSceneState(true);
}

function setTheme(enabled) {
    const nextTheme = Boolean(enabled);
    const themeChanged = nextTheme !== darkTheme;
    darkTheme = nextTheme;
    document.documentElement.dataset.theme = darkTheme ? "dark" : "light";
    scene.background.setHex(darkTheme ? 0x202421 : 0xeef1ec);
    if (themeChanged) rebuildBed();
    requestRender();
}

function disposeToolpathLines() {
    if (!toolpathLines) return;
    scene.remove(toolpathLines);
    toolpathLines.geometry.dispose();
    toolpathLines.material.dispose();
    toolpathLines = null;
    toolpathSegmentCount = 0;
    toolpathEligibleSegmentCount = 0;
}

function clearToolpath({ invalidateRequest = true } = {}) {
    if (invalidateRequest) gcodeRequestGate.invalidate();
    toolpathData = null;
    disposeToolpathLines();
    reportToolpathSelection([], []);
    requestRender();
}

function positiveValueRange(segments, property) {
    let minimum = Number.POSITIVE_INFINITY;
    let maximum = Number.NEGATIVE_INFINITY;
    for (const segment of segments) {
        const value = Number(segment[property]);
        if (!segment.extrusion || !Number.isFinite(value) || value <= 0) continue;
        minimum = Math.min(minimum, value);
        maximum = Math.max(maximum, value);
    }
    return Number.isFinite(minimum) ? { minimum, maximum } : null;
}

function valueColor(value, range, startHue, endHue, fallback) {
    if (!range || !Number.isFinite(value) || value <= 0) return new THREE.Color(fallback);
    const span = range.maximum - range.minimum;
    const ratio = span > 1e-7 ? (value - range.minimum) / span : 0.5;
    return new THREE.Color().setHSL(startHue + (endHue - startHue) * ratio, 0.82, 0.52);
}

function segmentColor(segment, maximumSpeed, lineWidthRange, layerHeightRange) {
    if (!segment.extrusion) return new THREE.Color(lineTypeColor("travel", false));
    if (toolpathPreview.colorMode === "speed") {
        const ratio = Math.min(1, Math.max(0, segment.speed / Math.max(maximumSpeed, 1)));
        return new THREE.Color().setHSL(0.62 - ratio * 0.62, 0.88, 0.53);
    }
    if (toolpathPreview.colorMode === "lineWidth") {
        return valueColor(segment.lineWidth, lineWidthRange, 0.62, 0.0, 0x7e8581);
    }
    if (toolpathPreview.colorMode === "layerHeight") {
        return valueColor(segment.layerHeight, layerHeightRange, 0.76, 0.08, 0x7e8581);
    }
    return new THREE.Color(lineTypeColor(segment.lineType, true));
}

function reportToolpathSelection(eligible, visible) {
    if (window.AndroidBridge?.onToolpathSelection) {
        window.AndroidBridge.onToolpathSelection(
            JSON.stringify(toolpathSelectionPayload(eligible, visible)),
        );
    }
}

function rebuildToolpath() {
    disposeToolpathLines();
    if (!toolpathData) {
        reportToolpathSelection([], []);
        return;
    }
    const eligible = selectVisibleToolpathSegments(toolpathData.segments, {
        ...toolpathPreview,
        maximumSegmentRatio: 1,
    });
    const visible = selectVisibleToolpathSegments(toolpathData.segments, toolpathPreview);
    reportToolpathSelection(eligible, visible);
    const maximumSpeed = maximumExtrusionSpeed(eligible);
    const lineWidthRange = positiveValueRange(eligible, "lineWidth");
    const layerHeightRange = positiveValueRange(eligible, "layerHeight");
    const positions = [];
    const colors = [];
    for (const segment of visible) {
        positions.push(...segment.start, ...segment.end);
        const color = segmentColor(segment, maximumSpeed, lineWidthRange, layerHeightRange);
        colors.push(color.r, color.g, color.b, color.r, color.g, color.b);
    }
    if (positions.length === 0) {
        statusElement.textContent = viewerText.noVisibleToolpaths;
        requestRender();
        return;
    }
    const geometry = new THREE.BufferGeometry();
    geometry.setAttribute("position", new THREE.Float32BufferAttribute(positions, 3));
    geometry.setAttribute("color", new THREE.Float32BufferAttribute(colors, 3));
    toolpathLines = new THREE.LineSegments(
        geometry,
        new THREE.LineBasicMaterial({ vertexColors: true, transparent: true, opacity: 0.94 })
    );
    toolpathSegmentCount = positions.length / 6;
    toolpathEligibleSegmentCount = eligible.length;
    scene.add(toolpathLines);
    toolpathLines.visible = viewMode === "toolpath";
    statusElement.textContent = `${toolpathSegmentCount} / ${toolpathEligibleSegmentCount} ${viewerText.segments} · ${viewerText.layers} ${toolpathPreview.minimumLayer + 1}–${Math.min(toolpathPreview.maximumLayer + 1, toolpathData.layerCount)}`;
    requestRender();
}

function setToolpathPreview(payload) {
    Object.assign(toolpathPreview, payload || {});
    rebuildToolpath();
}

async function loadToolpath(version = Date.now()) {
    const requestToken = gcodeRequestGate.begin();
    statusElement.textContent = viewerText.loadingGcode;
    try {
        const response = await fetch(`../../model/current.gcode?v=${encodeURIComponent(version)}`, { cache: "no-store" });
        if (!response.ok) throw new Error(`G-code request failed (${response.status})`);
        const source = await response.text();
        if (!gcodeRequestGate.isCurrent(requestToken)) return;
        const parsed = parseToolpathDetailed(source, bedWidth, bedDepth);
        if (!parsed.segments.some((segment) => segment.extrusion)) throw new Error("G-code has no extrusion paths");
        toolpathData = parsed;
        toolpathPreview.minimumLayer = 0;
        toolpathPreview.maximumLayer = Math.max(0, parsed.layerCount - 1);
        rebuildToolpath();
        setViewMode("toolpath");
        requestRender();
    } catch (error) {
        if (!gcodeRequestGate.isCurrent(requestToken)) return;
        clearToolpath({ invalidateRequest: false });
        reportError(error.message || "Cannot display G-code preview");
    }
}

function setViewMode(mode) {
    viewMode = mode === "toolpath" ? "toolpath" : "model";
    for (const record of modelObjects.values()) {
        record.group.visible = viewMode === "model" && record.visible;
    }
    if (toolpathLines) toolpathLines.visible = viewMode === "toolpath";
    statusElement.textContent = viewMode === "toolpath" && toolpathSegmentCount > 0
        ? `${toolpathSegmentCount} / ${toolpathEligibleSegmentCount} ${viewerText.toolpathSegments}`
        : modelObjects.size > 1
            ? `${modelObjects.size} ${viewerText.models}`
            : viewerText.gestureHint;
    requestRender();
}

function selectObjectAtPointer(event) {
    if (viewMode !== "model" || !camera || !renderer) return;
    const rect = canvas.getBoundingClientRect();
    if (rect.width <= 0 || rect.height <= 0) return;
    pointerPosition.set(
        ((event.clientX - rect.left) / rect.width) * 2 - 1,
        -((event.clientY - rect.top) / rect.height) * 2 + 1,
    );
    raycaster.setFromCamera(pointerPosition, camera);
    const meshes = visibleModelRecords().map((record) => record.mesh);
    const intersection = raycaster.intersectObjects(meshes, false)[0];
    const objectId = intersection?.object?.userData?.objectId ?? null;
    selectObject(objectId, true, "pointer");
    if (objectId != null) statusElement.textContent = `${viewerText.selected}: ${objectId}`;
}

function onPointerDown(event) {
    activePointers.add(event.pointerId);
    if (activePointers.size > 1) gestureHadMultiplePointers = true;
    if (event.isPrimary) {
        pointerDown = { pointerId: event.pointerId, x: event.clientX, y: event.clientY };
    }
}

function onPointerUp(event) {
    const distance = pointerDown?.pointerId === event.pointerId
        ? Math.hypot(event.clientX - pointerDown.x, event.clientY - pointerDown.y)
        : Number.POSITIVE_INFINITY;
    const isTap = event.isPrimary && distance <= 8 && !gestureHadMultiplePointers;
    activePointers.delete(event.pointerId);
    if (isTap) selectObjectAtPointer(event);
    if (event.isPrimary) pointerDown = null;
    if (activePointers.size === 0) gestureHadMultiplePointers = false;
}

function onPointerCancel(event) {
    activePointers.delete(event.pointerId);
    if (event.isPrimary) pointerDown = null;
    if (activePointers.size === 0) gestureHadMultiplePointers = false;
}

function resize() {
    if (!renderer || !camera) return;
    // Android WebView can report a zero CSS percentage/vh height for an
    // embedded view even though its visual viewport has a real size.
    const width = Math.max(1, Math.round(window.visualViewport?.width || window.innerWidth));
    const height = Math.max(1, Math.round(window.visualViewport?.height || window.innerHeight));
    canvas.style.width = `${width}px`;
    canvas.style.height = `${height}px`;
    renderer.setSize(width, height, false);
    camera.aspect = width / height;
    camera.updateProjectionMatrix();
    requestRender();
}

window.FeresaSlicerViewer = {
    loadModel,
    loadModels,
    clearModels,
    loadToolpath,
    clearToolpath,
    updateTransform,
    updateObjectTransform,
    selectObject,
    setBed,
    setTheme,
    setViewMode,
    setToolpathPreview,
    setCameraView,
    frameAll,
    resetCamera,
};

resetButton.addEventListener("click", frameAll);
canvas.addEventListener("pointerdown", onPointerDown);
canvas.addEventListener("pointerup", onPointerUp);
canvas.addEventListener("pointercancel", onPointerCancel);
window.addEventListener("resize", resize);
new ResizeObserver(resize).observe(document.body);

if (initRenderer()) {
    setTheme(darkTheme);
    if (window.AndroidBridge?.onReady) window.AndroidBridge.onReady();
    requestRender();
}
