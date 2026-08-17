// SPDX-License-Identifier: AGPL-3.0-only
import * as THREE from "three";
import { OrbitControls } from "three/addons/controls/OrbitControls.js";
import { STLLoader } from "three/addons/loaders/STLLoader.js";
import { parseToolpathDetailed } from "./toolpath-parser.mjs";

const canvas = document.getElementById("scene");
const statusElement = document.getElementById("status");
const fallbackElement = document.getElementById("fallback");
const resetButton = document.getElementById("reset");

let renderer;
let camera;
let controls;
let modelMesh = null;
let modelEdges = null;
let toolpathLines = null;
let toolpathData = null;
let toolpathSegmentCount = 0;
let modelVersion = 0;
let gcodeVersion = 0;
let viewMode = "model";
let bedWidth = 220;
let bedDepth = 220;
let bedGroup = null;
let renderQueued = false;
let darkTheme = new URLSearchParams(window.location.search).get("theme") === "dark";
const toolpathPreview = {
    minimumLayer: 0,
    maximumLayer: Number.MAX_SAFE_INTEGER,
    colorMode: "lineType",
    showExtrusion: true,
    showTravel: false,
};

const transformState = {
    positionX: 110,
    positionY: 110,
    rotationDegrees: 0,
    scale: 1,
};

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
    controls.maxPolarAngle = Math.PI * 0.49;
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
    if (bedGroup) scene.remove(bedGroup);
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
    const span = Math.max(bedWidth, bedDepth);
    camera.position.set(span * 0.72, span * 0.62, span * 0.82);
    controls.target.set(0, 12, 0);
    controls.update();
    requestRender();
}

function fitModelCamera() {
    if (!modelMesh || !camera || !controls) return;
    const bounds = new THREE.Box3().setFromObject(modelMesh);
    const size = bounds.getSize(new THREE.Vector3());
    const center = bounds.getCenter(new THREE.Vector3());
    const radius = Math.max(size.x, size.y, size.z, 10);
    camera.position.set(center.x + radius * 1.7, center.y + radius * 1.3, center.z + radius * 1.8);
    controls.target.copy(center);
    controls.update();
    requestRender();
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

function clearModel() {
    if (modelMesh) {
        scene.remove(modelMesh);
        modelMesh.geometry.dispose();
        modelMesh.material.dispose();
        modelMesh = null;
    }
    if (modelEdges) {
        scene.remove(modelEdges);
        modelEdges.geometry.dispose();
        modelEdges.material.dispose();
        modelEdges = null;
    }
}

async function loadModel(version = Date.now()) {
    modelVersion = version;
    statusElement.style.color = "#4f5852";
    statusElement.textContent = "Загрузка STL…";
    try {
        const response = await fetch(`../../model/current.stl?v=${encodeURIComponent(version)}`, { cache: "no-store" });
        if (!response.ok) throw new Error(`STL request failed (${response.status})`);
        const buffer = await response.arrayBuffer();
        if (version !== modelVersion) return;
        const geometry = new STLLoader().parse(buffer);
        transformGeometryFromStl(geometry);
        clearModel();

        modelMesh = new THREE.Mesh(
            geometry,
            new THREE.MeshStandardMaterial({
                color: 0x2a9d83,
                roughness: 0.52,
                metalness: 0.03,
                side: THREE.DoubleSide,
            })
        );
        modelEdges = new THREE.LineSegments(
            new THREE.EdgesGeometry(geometry, 32),
            new THREE.LineBasicMaterial({ color: 0x134d42, transparent: true, opacity: 0.5 })
        );
        scene.add(modelMesh);
        scene.add(modelEdges);
        applyTransform(true);
        setViewMode(viewMode);
        fitModelCamera();
        statusElement.textContent = "Проведите для вращения · сведите пальцы для масштаба";
        render();
    } catch (error) {
        clearModel();
        reportError(error.message || "Cannot display STL model");
    }
}

function applyTransform(notify = true) {
    if (!modelMesh || !modelEdges) return;
    const positionX = transformState.positionX - bedWidth / 2;
    const positionZ = transformState.positionY - bedDepth / 2;
    const rotation = -THREE.MathUtils.degToRad(transformState.rotationDegrees);
    for (const object of [modelMesh, modelEdges]) {
        object.position.set(positionX, 0, positionZ);
        object.rotation.set(0, rotation, 0);
        object.scale.setScalar(transformState.scale);
        object.updateMatrixWorld(true);
    }
    reportSceneState(notify);
    render();
}

function reportSceneState(notify = true) {
    if (!modelMesh) return;
    const bounds = new THREE.Box3().setFromObject(modelMesh);
    const payload = {
        minimumX: bounds.min.x + bedWidth / 2,
        maximumX: bounds.max.x + bedWidth / 2,
        minimumY: bounds.min.z + bedDepth / 2,
        maximumY: bounds.max.z + bedDepth / 2,
        height: bounds.max.y,
        insideBed: bounds.min.x >= -bedWidth / 2 - 0.001 &&
            bounds.max.x <= bedWidth / 2 + 0.001 &&
            bounds.min.z >= -bedDepth / 2 - 0.001 &&
            bounds.max.z <= bedDepth / 2 + 0.001,
    };
    modelMesh.material.color.setHex(payload.insideBed ? 0x2a9d83 : 0xd45445);
    if (notify && window.AndroidBridge?.onSceneState) {
        window.AndroidBridge.onSceneState(JSON.stringify(payload));
    }
}

function updateTransform(payload) {
    Object.assign(transformState, payload || {});
    applyTransform(true);
}

function setBed(width, depth) {
    bedWidth = Math.max(10, Number(width) || 220);
    bedDepth = Math.max(10, Number(depth) || 220);
    rebuildBed();
    applyTransform(true);
}

function setTheme(enabled) {
    darkTheme = Boolean(enabled);
    document.documentElement.dataset.theme = darkTheme ? "dark" : "light";
    scene.background.setHex(darkTheme ? 0x202421 : 0xeef1ec);
    rebuildBed();
    requestRender();
}

function clearToolpath() {
    if (!toolpathLines) return;
    scene.remove(toolpathLines);
    toolpathLines.geometry.dispose();
    toolpathLines.material.dispose();
    toolpathLines = null;
    toolpathSegmentCount = 0;
}

function segmentColor(segment, maximumSpeed) {
    if (!segment.extrusion) return new THREE.Color(0x3c8fd8);
    if (toolpathPreview.colorMode === "speed") {
        const ratio = Math.min(1, Math.max(0, segment.speed / Math.max(maximumSpeed, 1)));
        return new THREE.Color().setHSL(0.62 - ratio * 0.62, 0.88, 0.53);
    }
    if (toolpathPreview.colorMode === "layerHeight") {
        return new THREE.Color(0x8e63d2);
    }
    return new THREE.Color(0xf26b38);
}

function rebuildToolpath() {
    clearToolpath();
    if (!toolpathData) return;
    const visible = toolpathData.segments.filter((segment) =>
        segment.layer >= toolpathPreview.minimumLayer &&
        segment.layer <= toolpathPreview.maximumLayer &&
        ((segment.extrusion && toolpathPreview.showExtrusion) ||
            (!segment.extrusion && toolpathPreview.showTravel))
    );
    const maximumSpeed = Math.max(1, ...toolpathData.segments
        .filter((segment) => segment.extrusion)
        .map((segment) => segment.speed));
    const positions = [];
    const colors = [];
    for (const segment of visible) {
        positions.push(...segment.start, ...segment.end);
        const color = segmentColor(segment, maximumSpeed);
        colors.push(color.r, color.g, color.b, color.r, color.g, color.b);
    }
    if (positions.length === 0) {
        statusElement.textContent = "Нет видимых траекторий";
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
    scene.add(toolpathLines);
    toolpathLines.visible = viewMode === "toolpath";
    statusElement.textContent = `${toolpathSegmentCount} сегментов · слои ${toolpathPreview.minimumLayer + 1}–${Math.min(toolpathPreview.maximumLayer + 1, toolpathData.layerCount)}`;
    requestRender();
}

function setToolpathPreview(payload) {
    Object.assign(toolpathPreview, payload || {});
    rebuildToolpath();
}

async function loadToolpath(version = Date.now()) {
    gcodeVersion = version;
    statusElement.textContent = "Загрузка просмотра G-code…";
    try {
        const response = await fetch(`../../model/current.gcode?v=${encodeURIComponent(version)}`, { cache: "no-store" });
        if (!response.ok) throw new Error(`G-code request failed (${response.status})`);
        const parsed = parseToolpathDetailed(await response.text(), bedWidth, bedDepth);
        if (version !== gcodeVersion) return;
        if (!parsed.segments.some((segment) => segment.extrusion)) throw new Error("G-code has no extrusion paths");
        toolpathData = parsed;
        toolpathPreview.minimumLayer = 0;
        toolpathPreview.maximumLayer = Math.max(0, parsed.layerCount - 1);
        rebuildToolpath();
        setViewMode("toolpath");
        requestRender();
    } catch (error) {
        clearToolpath();
        reportError(error.message || "Cannot display G-code preview");
    }
}

function setViewMode(mode) {
    viewMode = mode === "toolpath" ? "toolpath" : "model";
    if (modelMesh) modelMesh.visible = viewMode === "model";
    if (modelEdges) modelEdges.visible = viewMode === "model";
    if (toolpathLines) toolpathLines.visible = viewMode === "toolpath";
    statusElement.textContent = viewMode === "toolpath" && toolpathSegmentCount > 0
        ? `${toolpathSegmentCount} сегментов экструзии`
        : "Проведите для вращения · сведите пальцы для масштаба";
    requestRender();
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
    loadToolpath,
    updateTransform,
    setBed,
    setTheme,
    setViewMode,
    setToolpathPreview,
    resetCamera,
};

resetButton.addEventListener("click", resetCamera);
window.addEventListener("resize", resize);
new ResizeObserver(resize).observe(document.body);

if (initRenderer()) {
    setTheme(darkTheme);
    if (window.AndroidBridge?.onReady) window.AndroidBridge.onReady();
    requestRender();
}
