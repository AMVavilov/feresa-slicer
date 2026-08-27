// SPDX-License-Identifier: AGPL-3.0-only
import * as THREE from "three";
import { OrbitControls } from "three/addons/controls/OrbitControls.js";
import { STLLoader } from "three/addons/loaders/STLLoader.js";
import {
    modelTransformToViewerComponents,
    normalizeModelObjectsPayload,
    normalizeModelTransform,
    objectSelectionPayload,
    pointerGestureIsTap,
    shouldReportObjectSelection,
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
    normalizeCameraViewPreset,
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
        fitScene: "Вписать сцену",
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
        fitScene: "Fit scene",
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
resetButton.textContent = viewerText.fitScene;
fallbackElement.textContent = viewerText.previewUnavailable;

let renderer;
let camera;
let controls;
const modelObjects = new Map();
let selectedObjectId = null;
let toolpathLines = null;
let toolpathData = null;
let toolpathSource = null;
let toolpathLineStarts = null;
let toolpathVersion = null;
let toolpathCommandSourceGeneration = 0;
let toolpathSegmentCount = 0;
let toolpathEligibleSegmentCount = 0;
const modelRequestGate = createLatestRequestGate();
const gcodeRequestGate = createLatestRequestGate();
let viewMode = "model";
let bedWidth = 220;
let bedDepth = 220;
let bedGroup = null;
let renderQueued = false;
let renderFrame = null;
let toolpathRenderedFrame = null;
let cameraTransitionFrame = null;
let cameraTransitionGeneration = 0;
let activeCameraPreset = null;
let cameraGestureActive = false;
let cameraGestureMoved = false;
let cameraMutationRevision = 0;
let modelLoadInProgress = false;
let pendingObjectSelection;
const pendingObjectTransforms = new Map();
let darkTheme = queryParams.get("theme") === "dark";
const toolpathPreview = {
    minimumLayer: 0,
    maximumLayer: Number.MAX_SAFE_INTEGER,
    colorMode: "lineType",
    maximumSegmentRatio: 1,
    showExtrusion: true,
    showTravel: false,
    includeCommands: false,
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
let resizeObserver = null;
let disposed = false;

const scene = new THREE.Scene();
scene.background = new THREE.Color(darkTheme ? 0x202421 : 0xeef1ec);

function render() {
    if (!disposed && renderer && camera) renderer.render(scene, camera);
}

function requestRender() {
    if (disposed || renderQueued) return;
    renderQueued = true;
    renderFrame = requestAnimationFrame(() => {
        renderFrame = null;
        renderQueued = false;
        render();
    });
}

function reportError(message) {
    if (disposed) return;
    statusElement.textContent = message;
    statusElement.style.color = "#8b1f17";
    if (window.AndroidBridge?.onError) window.AndroidBridge.onError(String(message));
}

function initRenderer() {
    try {
        renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: false });
        renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.5));
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
    controls.addEventListener("start", onCameraControlsStart);
    controls.addEventListener("change", onCameraControlsChange);
    controls.addEventListener("end", onCameraControlsEnd);

    scene.add(new THREE.HemisphereLight(0xffffff, 0x718078, 2.3));
    const keyLight = new THREE.DirectionalLight(0xffffff, 2.2);
    keyLight.position.set(180, 260, 120);
    scene.add(keyLight);

    rebuildBed();
    resize();
    resetCamera({ notify: false, trackChange: false });
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

function resetCamera(options = {}) {
    if (!camera || !controls) return;
    cancelCameraTransition();
    if (options.trackChange !== false) cameraMutationRevision += 1;
    if (options.preserveCameraMode !== true) activeCameraPreset = null;
    const span = Math.max(bedWidth, bedDepth);
    camera.position.set(span * 0.72, span * 0.62, span * 0.82);
    camera.up.set(0, 1, 0);
    controls.target.set(0, 12, 0);
    controls.update();
    requestRender();
    if (options.notify !== false) reportCameraState(options.source ?? "reset", false);
    return true;
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

function selectedModelBounds() {
    const record = selectedObjectId == null ? null : modelObjects.get(selectedObjectId);
    if (!record?.visible) return null;
    record.group.updateMatrixWorld(true);
    const bounds = new THREE.Box3().setFromObject(record.group);
    return bounds.isEmpty() ? null : bounds;
}

function printBedBounds() {
    return new THREE.Box3(
        new THREE.Vector3(-bedWidth / 2, -0.2, -bedDepth / 2),
        new THREE.Vector3(bedWidth / 2, 0.2, bedDepth / 2),
    );
}

function currentCameraDirection() {
    const direction = camera.position.clone().sub(controls.target);
    if (direction.lengthSq() <= 1e-12) direction.set(1, 0.82, 1.16);
    return direction.normalize();
}

function frameBounds(bounds, options = {}) {
    if (!camera || !controls || !bounds || bounds.isEmpty()) return false;
    cancelCameraTransition();
    if (options.trackChange !== false) cameraMutationRevision += 1;
    const center = bounds.getCenter(new THREE.Vector3());
    const radius = Math.max(
        bounds.getBoundingSphere(new THREE.Sphere()).radius,
        controls.minDistance * 0.05,
    );
    const distance = Math.max(
        controls.minDistance,
        cameraDistanceToFrameSphere({
            radius,
            verticalFovDegrees: camera.fov,
            aspect: camera.aspect,
            margin: options.margin ?? 1.18,
        }),
    );
    const direction = options.direction
        ? new THREE.Vector3(...options.direction).normalize()
        : currentCameraDirection();
    const position = center.clone().addScaledVector(direction, distance);
    const up = camera.up.clone().normalize();
    camera.near = Math.max(0.1, distance / 1000);
    camera.far = Math.max(3000, distance * 20);
    camera.updateProjectionMatrix();
    controls.maxDistance = Math.max(1200, distance * 1.25);
    if (options.preserveCameraMode !== true) activeCameraPreset = null;
    finishCameraView(position, center, up, {
        notify: options.notify,
        source: options.source ?? "fit",
    });
    return true;
}

/** Fits every visible model while preserving the current viewing direction. */
function fitModels(options = {}) {
    return frameBounds(modelBounds(), {
        ...options,
        source: options.source ?? "fit-models",
    });
}

/** Fits the selected model; returns false without changing the camera if none is selected. */
function fitSelectedModel(options = {}) {
    return frameBounds(selectedModelBounds(), {
        ...options,
        source: options.source ?? "fit-selected-model",
    });
}

/** Fits the complete configured print bed while preserving the current viewing direction. */
function showWholeBed(options = {}) {
    return frameBounds(printBedBounds(), {
        ...options,
        source: options.source ?? "show-whole-bed",
    });
}

/** Backward-compatible fit-all command: models first, then the empty print bed. */
function frameAll(options = {}) {
    return fitModels(options) || showWholeBed({
        ...options,
        source: options.source ?? "show-whole-bed",
    });
}

function activeViewBounds() {
    if (viewMode === "toolpath" && toolpathLines) {
        const toolpathBounds = new THREE.Box3().setFromObject(toolpathLines);
        if (!toolpathBounds.isEmpty()) return toolpathBounds;
    }
    const bounds = modelBounds();
    if (bounds) return bounds;
    return printBedBounds();
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

function cameraStateSnapshot(source = "api", interactionActive = false) {
    if (!camera || !controls) return null;
    return {
        version: 1,
        position: camera.position.toArray(),
        target: controls.target.toArray(),
        up: camera.up.toArray(),
        fieldOfViewDegrees: camera.fov,
        mode: activeCameraPreset == null ? "free" : "preset",
        preset: activeCameraPreset,
        source: String(source || "api"),
        interactionActive: Boolean(interactionActive),
    };
}

function reportCameraState(source = "api", interactionActive = false) {
    if (disposed) return null;
    const payload = cameraStateSnapshot(source, interactionActive);
    if (payload && window.AndroidBridge?.onCameraState) {
        window.AndroidBridge.onCameraState(JSON.stringify(payload));
    }
    return payload;
}

function cameraVectorFromState(source, name) {
    const value = source?.[name];
    if (!Array.isArray(value) || value.length !== 3) {
        throw new TypeError(`Camera ${name} must contain three coordinates`);
    }
    const coordinates = value.map(Number);
    if (!coordinates.every(Number.isFinite)) {
        throw new TypeError(`Camera ${name} coordinates must be finite`);
    }
    return new THREE.Vector3(...coordinates);
}

function normalizeRestorableCameraState(input) {
    const source = typeof input === "string" ? JSON.parse(input) : input;
    if (!source || typeof source !== "object") {
        throw new TypeError("Camera state must be an object");
    }
    const position = cameraVectorFromState(source, "position");
    const target = cameraVectorFromState(source, "target");
    const up = cameraVectorFromState(source, "up");
    const direction = target.clone().sub(position);
    if (direction.lengthSq() <= 1e-12) {
        throw new RangeError("Camera position and target must be different");
    }
    if (up.lengthSq() <= 1e-12 || direction.clone().cross(up).lengthSq() <= 1e-12) {
        throw new RangeError("Camera up vector must be non-zero and non-collinear");
    }
    const fieldOfViewDegrees = Number(source.fieldOfViewDegrees ?? 38);
    if (!Number.isFinite(fieldOfViewDegrees) || fieldOfViewDegrees < 1 || fieldOfViewDegrees > 179) {
        throw new RangeError("Camera field of view must be between 1 and 179 degrees");
    }
    const mode = source.mode === "preset" ? "preset" : "free";
    const preset = mode === "preset" ? normalizeCameraViewPreset(source.preset) : null;
    return { position, target, up: up.normalize(), fieldOfViewDegrees, mode, preset };
}

/** Restores an exact camera snapshot without reloading scene resources. */
function restoreCameraState(input, options = {}) {
    if (!camera || !controls) return false;
    try {
        const state = normalizeRestorableCameraState(input);
        cancelCameraTransition();
        if (options.trackChange !== false) cameraMutationRevision += 1;
        activeCameraPreset = state.preset;
        if (state.preset != null) cameraViewPreference.select(state.preset);
        camera.position.copy(state.position);
        camera.up.copy(state.up);
        camera.fov = state.fieldOfViewDegrees;
        controls.target.copy(state.target);
        const distance = camera.position.distanceTo(controls.target);
        camera.near = Math.max(0.1, distance / 1000);
        camera.far = Math.max(3000, distance * 20);
        camera.updateProjectionMatrix();
        controls.maxDistance = Math.max(1200, distance * 1.25);
        controls.update();
        requestRender();
        if (options.notify !== false) reportCameraState(options.source ?? "restore", false);
        return true;
    } catch (error) {
        reportError(error.message || "Cannot restore camera state");
        return false;
    }
}

function finishCameraView(position, target, up, options = {}) {
    camera.position.copy(position);
    camera.up.copy(up);
    controls.target.copy(target);
    camera.lookAt(target);
    controls.update();
    controls.enabled = true;
    cameraTransitionFrame = null;
    requestRender();
    if (options.notify !== false) reportCameraState(options.source ?? "api", false);
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
        finishCameraView(destination, target, up, options);
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
            finishCameraView(destination, target, up, options);
        }
    };
    cameraTransitionFrame = requestAnimationFrame(animateCamera);
    return true;
}

function setCameraView(preset, options = {}) {
    const definition = cameraViewPreference.select(preset);
    if (options.trackChange !== false) cameraMutationRevision += 1;
    activeCameraPreset = definition.preset;
    return applyCameraViewDefinition(definition, {
        ...options,
        source: options.source ?? "preset",
    });
}

function restoreCameraViewAfterFraming(options = {}) {
    if (activeCameraPreset == null) return false;
    const definition = cameraViewPreference.current();
    return applyCameraViewDefinition(definition, {
        ...options,
        durationMs: 0,
        source: options.source ?? "preset",
    });
}

function settleCameraAfterSceneChange(
    restoredState = null,
    shouldFrame = true,
    expectedCameraRevision = null,
) {
    if (expectedCameraRevision != null && cameraMutationRevision !== expectedCameraRevision) {
        reportCameraState("scene-command-preserved", false);
        return false;
    }
    if (shouldFrame) {
        frameAll({
            notify: false,
            preserveCameraMode: true,
            source: "scene",
            trackChange: false,
        });
    }
    if (restoredState != null) {
        return restoreCameraState(restoredState, { source: "restore", trackChange: false });
    }
    if (restoreCameraViewAfterFraming({ source: "preset", trackChange: false })) return true;
    reportCameraState("scene", false);
    return true;
}

function onCameraControlsStart() {
    cameraGestureActive = true;
    cameraGestureMoved = false;
}

function onCameraControlsChange() {
    requestRender();
    if (!cameraGestureActive || cameraGestureMoved) return;
    cameraGestureMoved = true;
    cameraMutationRevision += 1;
    activeCameraPreset = null;
    reportCameraState("manual", true);
}

function onCameraControlsEnd() {
    if (cameraGestureMoved) reportCameraState("manual", false);
    cameraGestureActive = false;
    cameraGestureMoved = false;
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
    modelLoadInProgress = false;
    pendingObjectSelection = undefined;
    pendingObjectTransforms.clear();
    disposeLoadedModels();
    clearToolpath();
    setViewMode("model");
    reportObjectSelection(null, "api");
    statusElement.style.color = "#4f5852";
    statusElement.textContent = viewerText.waitingModel;
    settleCameraAfterSceneChange();
}

function modelRequestUrl(url, version) {
    const resolved = new URL(url, window.location.href);
    if (resolved.origin !== window.location.origin) {
        throw new Error("STL URL must use the viewer origin");
    }
    resolved.searchParams.set("v", version);
    return resolved.href;
}

async function loadModels(input, initialCameraState = null) {
    const payload = normalizeModelObjectsPayload(input, transformState);
    const payloadObjectIds = new Set(payload.objects.map((descriptor) => descriptor.objectId));
    for (const objectId of pendingObjectTransforms.keys()) {
        if (!payloadObjectIds.has(objectId)) pendingObjectTransforms.delete(objectId);
    }
    if (
        pendingObjectSelection != null &&
        !payloadObjectIds.has(String(pendingObjectSelection))
    ) {
        pendingObjectSelection = undefined;
    }
    const cameraRevisionAtLoadStart = cameraMutationRevision;
    const requestToken = modelRequestGate.begin();
    modelLoadInProgress = true;
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
        modelLoadInProgress = false;
        pendingObjectSelection = undefined;
        pendingObjectTransforms.clear();
        reportObjectSelection(null, "api");
        settleCameraAfterSceneChange(
            initialCameraState,
            payload.frameAll,
            cameraRevisionAtLoadStart,
        );
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
            const pendingTransform = pendingObjectTransforms.get(descriptor.objectId);
            return createModelRecord(
                pendingTransform == null
                    ? descriptor
                    : {
                        ...descriptor,
                        transform: normalizeModelTransform(pendingTransform, descriptor.transform),
                    },
                geometry,
            );
        });

        const requestedSelection = pendingObjectSelection !== undefined
            ? pendingObjectSelection
            : payload.selectedObjectId;
        disposeLoadedModels();
        for (const record of loadedRecords) {
            modelObjects.set(record.objectId, record);
            scene.add(record.group);
            applyObjectTransform(record, record.transform, false);
            pendingObjectTransforms.delete(record.objectId);
        }
        modelLoadInProgress = false;
        pendingObjectSelection = undefined;
        setViewMode(viewMode);
        selectObject(requestedSelection, true, "api");
        reportSceneState(true);
        settleCameraAfterSceneChange(
            initialCameraState,
            payload.frameAll,
            cameraRevisionAtLoadStart,
        );
        statusElement.textContent = payload.objects.length > 1
            ? `${payload.objects.length} ${viewerText.models}`
            : viewerText.gestureHint;
        requestRender();
    } catch (error) {
        if (!modelRequestGate.isCurrent(requestToken)) return;
        modelLoadInProgress = false;
        pendingObjectSelection = undefined;
        for (const objectId of payloadObjectIds) pendingObjectTransforms.delete(objectId);
        disposeLoadedModels();
        reportObjectSelection(null, "api");
        reportError(error.message || "Cannot display STL model");
    }
}

async function loadModel(version = Date.now(), initialCameraState = null) {
    return loadModels({
        version,
        objects: [{
            objectId: LEGACY_OBJECT_ID,
            url: "../../model/current.stl",
            transform: transformState,
        }],
        selectedObjectId: LEGACY_OBJECT_ID,
        frameAll: true,
    }, initialCameraState);
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
    if (disposed) return;
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
    if (!disposed && window.AndroidBridge?.onObjectSelected) {
        window.AndroidBridge.onObjectSelected(JSON.stringify(objectSelectionPayload(objectId, source)));
    }
}

function selectObject(objectId, notify = true, source = "api") {
    const requestedObjectId = objectId == null ? null : String(objectId);
    if (modelLoadInProgress) pendingObjectSelection = requestedObjectId;
    if (requestedObjectId != null && !modelObjects.has(requestedObjectId)) {
        // Compose can issue selection while the matching STL fetch is still in flight. Keep the
        // latest request and apply it atomically when that scene commits instead of reporting a
        // transient unknown-object error or selecting the wrong model.
        pendingObjectSelection = requestedObjectId;
        return requestedObjectId;
    }
    const normalizedObjectId = requestedObjectId;
    const changed = selectedObjectId !== normalizedObjectId;
    selectedObjectId = normalizedObjectId;
    for (const record of modelObjects.values()) updateModelAppearance(record);
    if (notify && shouldReportObjectSelection(changed, source)) {
        reportObjectSelection(selectedObjectId, source);
    }
    requestRender();
    return selectedObjectId;
}

function updateObjectTransform(objectId, payload) {
    const normalizedObjectId = String(objectId);
    const record = modelObjects.get(normalizedObjectId);
    if (!record) {
        // Resource loading and Compose effects are intentionally independent. A transform may
        // arrive before its STL record; queue the latest value so the first rendered frame already
        // matches the native plate state.
        pendingObjectTransforms.set(normalizedObjectId, payload);
        return false;
    }
    pendingObjectTransforms.delete(normalizedObjectId);
    applyObjectTransform(record, payload, true);
    return true;
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
    if (toolpathRenderedFrame != null) {
        cancelAnimationFrame(toolpathRenderedFrame);
        toolpathRenderedFrame = null;
    }
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
    toolpathCommandSourceGeneration += 1;
    toolpathData = null;
    toolpathSource = null;
    toolpathLineStarts = null;
    toolpathVersion = null;
    disposeToolpathLines();
    reportToolpathSelection([], []);
    requestRender();
}

function indexToolpathSource(source, lineCount) {
    const count = Math.max(0, Number(lineCount) || 0);
    const starts = new Uint32Array(count + 1);
    let nextLine = 1;
    for (let index = 0; index < source.length && nextLine < count; index += 1) {
        if (source.charCodeAt(index) !== 10) continue;
        starts[nextLine] = index + 1;
        nextLine += 1;
    }
    starts[count] = source.length;
    return starts;
}

function toolpathSourceLine(lineNumber) {
    if (!toolpathSource || !toolpathLineStarts) return "";
    const normalized = Math.trunc(Number(lineNumber));
    const lineCount = toolpathLineStarts.length - 1;
    if (!Number.isFinite(normalized) || normalized < 1 || normalized > lineCount) return "";
    const start = toolpathLineStarts[normalized - 1];
    let end = toolpathLineStarts[normalized];
    if (end > start && toolpathSource.charCodeAt(end - 1) === 10) end -= 1;
    if (end > start && toolpathSource.charCodeAt(end - 1) === 13) end -= 1;
    return toolpathSource.slice(start, end).trim().slice(0, 180);
}

function toolpathCommandWindow(eligible, visible) {
    if (!toolpathPreview.includeCommands || visible.length === 0) return [];
    const selectedIndex = visible.length - 1;
    const selectedLineNumber = visible[selectedIndex].lineNumber;
    const seen = new Set([selectedLineNumber]);
    const before = [];
    const after = [];

    for (let index = selectedIndex - 1; index >= 0 && before.length < 5; index -= 1) {
        const lineNumber = eligible[index].lineNumber;
        if (seen.has(lineNumber)) continue;
        seen.add(lineNumber);
        before.unshift({
            lineNumber,
            source: toolpathSourceLine(lineNumber),
            active: false,
        });
    }
    for (let index = selectedIndex + 1; index < eligible.length && after.length < 5; index += 1) {
        const lineNumber = eligible[index].lineNumber;
        if (seen.has(lineNumber)) continue;
        seen.add(lineNumber);
        after.push({
            lineNumber,
            source: toolpathSourceLine(lineNumber),
            active: false,
        });
    }
    return [
        ...before,
        {
            lineNumber: selectedLineNumber,
            source: toolpathSourceLine(selectedLineNumber),
            active: true,
        },
        ...after,
    ];
}

async function ensureToolpathCommandSource() {
    if (!toolpathPreview.includeCommands || toolpathSource || !toolpathData || toolpathVersion == null) return;
    const generation = ++toolpathCommandSourceGeneration;
    const expectedData = toolpathData;
    const version = toolpathVersion;
    try {
        const response = await fetch(`../../model/current.gcode?v=${encodeURIComponent(version)}`, { cache: "no-store" });
        if (!response.ok) return;
        const source = await response.text();
        if (
            generation !== toolpathCommandSourceGeneration ||
            !toolpathPreview.includeCommands ||
            toolpathData !== expectedData
        ) return;
        toolpathSource = source;
        toolpathLineStarts = indexToolpathSource(source, expectedData.lineCount);
        rebuildToolpath();
    } catch (_) {
        // Command text is optional. The rendered toolpath remains authoritative
        // even if a second, on-demand source read fails.
    }
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
    if (!disposed && window.AndroidBridge?.onToolpathSelection) {
        window.AndroidBridge.onToolpathSelection(
            JSON.stringify(toolpathSelectionPayload(eligible, visible, {
                lineCount: toolpathData?.lineCount ?? 0,
                commands: toolpathCommandWindow(eligible, visible),
            })),
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
    const requestedRatio = Number(toolpathPreview.maximumSegmentRatio);
    const maximumSegmentRatio = Number.isFinite(requestedRatio)
        ? Math.min(1, Math.max(0, requestedRatio))
        : 1;
    const visible = eligible.length === 0 || maximumSegmentRatio >= 1
        ? eligible
        : eligible.slice(0, Math.round((eligible.length - 1) * maximumSegmentRatio) + 1);
    reportToolpathSelection(eligible, visible);
    const maximumSpeed = maximumExtrusionSpeed(eligible);
    const lineWidthRange = positiveValueRange(eligible, "lineWidth");
    const layerHeightRange = positiveValueRange(eligible, "layerHeight");
    // Build the GPU attributes in their final representation. Plain JavaScript number arrays
    // briefly retain boxed values and Three.js then copies them into Float32Array, which can
    // double the post-slice peak for large toolpaths and make Android kill the renderer.
    const positions = new Float32Array(visible.length * 6);
    const colors = new Float32Array(visible.length * 6);
    let offset = 0;
    for (const segment of visible) {
        positions.set(segment.start, offset);
        positions.set(segment.end, offset + 3);
        const color = segmentColor(segment, maximumSpeed, lineWidthRange, layerHeightRange);
        colors[offset] = color.r;
        colors[offset + 1] = color.g;
        colors[offset + 2] = color.b;
        colors[offset + 3] = color.r;
        colors[offset + 4] = color.g;
        colors[offset + 5] = color.b;
        offset += 6;
    }
    if (positions.length === 0) {
        statusElement.textContent = viewerText.noVisibleToolpaths;
        requestRender();
        return;
    }
    const geometry = new THREE.BufferGeometry();
    geometry.setAttribute("position", new THREE.BufferAttribute(positions, 3));
    geometry.setAttribute("color", new THREE.BufferAttribute(colors, 3));
    toolpathLines = new THREE.LineSegments(
        geometry,
        new THREE.LineBasicMaterial({ vertexColors: true, transparent: true, opacity: 0.94 })
    );
    toolpathSegmentCount = visible.length;
    toolpathEligibleSegmentCount = eligible.length;
    scene.add(toolpathLines);
    toolpathLines.visible = viewMode === "toolpath";
    statusElement.textContent = `${toolpathSegmentCount} / ${toolpathEligibleSegmentCount} ${viewerText.segments} · ${viewerText.layers} ${toolpathPreview.minimumLayer + 1}–${Math.min(toolpathPreview.maximumLayer + 1, toolpathData.layerCount)}`;
    requestRender();
    const renderedLines = toolpathLines;
    toolpathRenderedFrame = requestAnimationFrame(() => {
        toolpathRenderedFrame = null;
        if (!disposed && toolpathLines === renderedLines && window.AndroidBridge?.onToolpathRendered) {
            window.AndroidBridge.onToolpathRendered(toolpathSegmentCount);
        }
    });
}

function setToolpathPreview(payload) {
    Object.assign(toolpathPreview, payload || {});
    if (!toolpathPreview.includeCommands) {
        toolpathCommandSourceGeneration += 1;
        toolpathSource = null;
        toolpathLineStarts = null;
    }
    rebuildToolpath();
    void ensureToolpathCommandSource();
}

async function loadToolpath(version = Date.now()) {
    const requestToken = gcodeRequestGate.begin();
    toolpathCommandSourceGeneration += 1;
    toolpathSource = null;
    toolpathLineStarts = null;
    toolpathVersion = null;
    statusElement.textContent = viewerText.loadingGcode;
    try {
        const response = await fetch(`../../model/current.gcode?v=${encodeURIComponent(version)}`, { cache: "no-store" });
        if (!response.ok) throw new Error(`G-code request failed (${response.status})`);
        const source = await response.text();
        if (!gcodeRequestGate.isCurrent(requestToken)) return;
        const parsed = parseToolpathDetailed(source, bedWidth, bedDepth);
        if (!parsed.segments.some((segment) => segment.extrusion)) throw new Error("G-code has no extrusion paths");
        toolpathData = parsed;
        toolpathVersion = version;
        if (toolpathPreview.includeCommands) {
            toolpathSource = source;
            toolpathLineStarts = indexToolpathSource(source, parsed.lineCount);
        } else {
            toolpathSource = null;
            toolpathLineStarts = null;
        }
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
        pointerDown = {
            pointerId: event.pointerId,
            x: event.clientX,
            y: event.clientY,
            button: event.button,
            startedAt: event.timeStamp,
            maximumDistance: 0,
            cameraRevision: cameraMutationRevision,
        };
    }
}

function onPointerMove(event) {
    if (pointerDown?.pointerId !== event.pointerId) return;
    pointerDown.maximumDistance = Math.max(
        pointerDown.maximumDistance,
        Math.hypot(event.clientX - pointerDown.x, event.clientY - pointerDown.y),
    );
}

function onPointerUp(event) {
    const pointer = pointerDown?.pointerId === event.pointerId ? pointerDown : null;
    const releaseDistance = pointer
        ? Math.hypot(event.clientX - pointer.x, event.clientY - pointer.y)
        : Number.POSITIVE_INFINITY;
    const isTap = pointerGestureIsTap({
        isPrimary: event.isPrimary,
        button: pointer?.button,
        maximumDistance: Math.max(pointer?.maximumDistance ?? 0, releaseDistance),
        durationMs: pointer ? event.timeStamp - pointer.startedAt : Number.POSITIVE_INFINITY,
        hadMultiplePointers: gestureHadMultiplePointers,
        cameraChanged: pointer ? cameraMutationRevision !== pointer.cameraRevision : true,
    });
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

/**
 * Permanently releases this viewer instance before Android destroys its WebView.
 * The operation is intentionally idempotent because the JavaScript callback and
 * Android's bounded fallback may race with a renderer-process termination.
 */
function disposeViewer() {
    if (disposed) return false;
    disposed = true;

    modelRequestGate.invalidate();
    gcodeRequestGate.invalidate();
    toolpathCommandSourceGeneration += 1;
    cancelCameraTransition();

    if (renderFrame != null) cancelAnimationFrame(renderFrame);
    renderFrame = null;
    renderQueued = false;
    if (toolpathRenderedFrame != null) cancelAnimationFrame(toolpathRenderedFrame);
    toolpathRenderedFrame = null;

    resizeObserver?.disconnect();
    resizeObserver = null;
    resetButton.removeEventListener("click", frameAll);
    canvas.removeEventListener("pointerdown", onPointerDown);
    canvas.removeEventListener("pointermove", onPointerMove);
    canvas.removeEventListener("pointerup", onPointerUp);
    canvas.removeEventListener("pointercancel", onPointerCancel);
    window.removeEventListener("resize", resize);

    if (controls) {
        controls.removeEventListener("start", onCameraControlsStart);
        controls.removeEventListener("change", onCameraControlsChange);
        controls.removeEventListener("end", onCameraControlsEnd);
        controls.dispose();
    }
    controls = null;
    activePointers.clear();
    pointerDown = null;
    gestureHadMultiplePointers = false;
    cameraGestureActive = false;
    cameraGestureMoved = false;

    modelLoadInProgress = false;
    pendingObjectSelection = undefined;
    pendingObjectTransforms.clear();
    disposeLoadedModels();

    toolpathData = null;
    toolpathSource = null;
    toolpathLineStarts = null;
    toolpathVersion = null;
    disposeToolpathLines();

    if (bedGroup) {
        scene.remove(bedGroup);
        disposeObject3DResources(bedGroup);
        bedGroup = null;
    }
    scene.clear();

    const rendererToDispose = renderer;
    renderer = null;
    camera = null;
    if (rendererToDispose) {
        rendererToDispose.setAnimationLoop(null);
        rendererToDispose.renderLists?.dispose();
        rendererToDispose.dispose();
        rendererToDispose.forceContextLoss();
    }
    return true;
}

window.FeresaSlicerViewer = {
    dispose: disposeViewer,
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
    getCameraState: cameraStateSnapshot,
    restoreCameraState,
    fitModels,
    fitSelectedModel,
    showWholeBed,
    frameAll,
    resetCamera,
};

resetButton.addEventListener("click", frameAll);
canvas.addEventListener("pointerdown", onPointerDown);
canvas.addEventListener("pointermove", onPointerMove);
canvas.addEventListener("pointerup", onPointerUp);
canvas.addEventListener("pointercancel", onPointerCancel);
window.addEventListener("resize", resize);
resizeObserver = new ResizeObserver(resize);
resizeObserver.observe(document.body);

if (initRenderer()) {
    setTheme(darkTheme);
    // Do not publish the temporary reset-camera pose here. Android can already hold a camera
    // snapshot from the WebView that is being recreated; reporting this bootstrap pose before
    // loadModels() restores that snapshot would overwrite it. The first authoritative camera
    // callback is emitted by settleCameraAfterSceneChange() after the scene has either restored
    // the saved camera or auto-fitted the newly loaded model/bed.
    if (!disposed && window.AndroidBridge?.onReady) window.AndroidBridge.onReady();
    requestRender();
}
