// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

data class ModelTransform(
    val positionX: Double = 110.0,
    val positionY: Double = 110.0,
    val rotationDegrees: Double = 0.0,
    val scale: Double = 1.0,
    val positionZ: Double = 0.0,
    val rotationXDegrees: Double = 0.0,
    val rotationYDegrees: Double = 0.0,
    val scaleX: Double? = null,
    val scaleY: Double? = null,
    val scaleZ: Double? = null,
)

/**
 * Viewer wire format. Legacy rotationDegrees is print-space Z rotation and
 * legacy scale remains the uniform fallback for callers that do not provide
 * per-axis scale values.
 */
internal fun ModelTransform.toViewerJson(): JSONObject = JSONObject()
    .put("positionX", positionX)
    .put("positionY", positionY)
    .put("positionZ", positionZ)
    .put("rotationXDegrees", rotationXDegrees)
    .put("rotationYDegrees", rotationYDegrees)
    .put("rotationZDegrees", rotationDegrees)
    .put("scaleX", scaleX ?: scale)
    .put("scaleY", scaleY ?: scale)
    .put("scaleZ", scaleZ ?: scale)
    .put("rotationDegrees", rotationDegrees)
    .put("scale", scale)

data class ViewerModelObject(
    val objectId: String,
    val file: File,
    val transform: ModelTransform,
    val visible: Boolean = true,
)

/**
 * The legacy single-file resource is not part of a multi-object scene's load identity.
 * In the app it follows the selected object, so using it as a multi-object LaunchedEffect key
 * would refetch every STL and reset the camera on each selection change.
 */
internal fun viewerLegacyModelFile(
    modelFile: File?,
    modelObjects: List<ViewerModelObject>,
): File? = modelFile.takeIf { modelObjects.isEmpty() }

data class ViewerObjectSelection(
    val objectId: String?,
    val source: String,
)

internal fun parseViewerObjectSelection(payload: String): ViewerObjectSelection {
    val json = JSONObject(payload)
    return ViewerObjectSelection(
        objectId = json.optString("objectId").takeUnless { json.isNull("objectId") || it.isBlank() },
        source = json.optString("source", "api"),
    )
}

data class ViewerSceneState(
    val minimumX: Double,
    val maximumX: Double,
    val minimumY: Double,
    val maximumY: Double,
    val height: Double,
    val insideBed: Boolean,
    val minimumZ: Double = 0.0,
    val maximumZ: Double = height,
)

data class ViewerToolpathSelection(
    val selected: Boolean,
    val displayedSegmentCount: Int,
    val eligibleSegmentCount: Int,
    val lineCount: Int,
    val minimumLayerZ: Double?,
    val maximumLayerZ: Double?,
    val layer: Int,
    val lineNumber: Int,
    val x: Double,
    val y: Double,
    val z: Double,
    val speedMmSeconds: Double,
    val extrusion: Boolean,
    val lineType: String,
    val lineTypeLabel: String,
    val lineWidthMm: Double?,
    val layerHeightMm: Double?,
    val commands: List<ViewerGcodeCommand>,
)

data class ViewerGcodeCommand(
    val lineNumber: Int,
    val source: String,
    val active: Boolean,
)

internal fun parseViewerToolpathSelection(payload: String): ViewerToolpathSelection? {
    val json = JSONObject(payload)
    val selected = json.optBoolean("selected", false)

    fun optionalFiniteDouble(name: String): Double? = if (json.isNull(name)) {
        null
    } else {
        json.optDouble(name, Double.NaN).takeIf { it.isFinite() }
    }

    val commandsJson = json.optJSONArray("commands")
    val commands = buildList {
        if (commandsJson != null) {
            for (index in 0 until commandsJson.length()) {
                val command = commandsJson.optJSONObject(index) ?: continue
                add(
                    ViewerGcodeCommand(
                        lineNumber = command.optInt("lineNumber", 0),
                        source = command.optString("source", ""),
                        active = command.optBoolean("active", false),
                    ),
                )
            }
        }
    }

    return ViewerToolpathSelection(
        selected = selected,
        displayedSegmentCount = json.optInt("displayedSegmentCount", 0),
        eligibleSegmentCount = json.optInt("eligibleSegmentCount", 0),
        lineCount = json.optInt("lineCount", 0),
        minimumLayerZ = optionalFiniteDouble("minimumLayerZ"),
        maximumLayerZ = optionalFiniteDouble("maximumLayerZ"),
        layer = json.optInt("layer", 0),
        lineNumber = json.optInt("lineNumber", 0),
        x = json.optDouble("x", 0.0),
        y = json.optDouble("y", 0.0),
        z = json.optDouble("z", 0.0),
        speedMmSeconds = json.optDouble("speed", 0.0),
        extrusion = json.optBoolean("extrusion", false),
        lineType = json.optString("lineType", ""),
        lineTypeLabel = json.optString("lineTypeLabel", ""),
        lineWidthMm = optionalFiniteDouble("lineWidth"),
        layerHeightMm = optionalFiniteDouble("layerHeight"),
        commands = commands,
    )
}

enum class ViewerMode(val wireValue: String) {
    MODEL("model"),
    TOOLPATH("toolpath"),
}

enum class CameraViewPreset(val wireValue: String) {
    ISOMETRIC("isometric"),
    TOP("top"),
    BOTTOM("bottom"),
    FRONT("front"),
    BACK("back"),
    LEFT("left"),
    RIGHT("right"),
}

/**
 * A requestId makes repeated selections of the same camera preset observable
 * by Compose and therefore repeatable by the embedded viewer.
 */
data class CameraViewRequest(
    val requestId: Int,
    val preset: CameraViewPreset,
)

enum class ViewerCameraMode(val wireValue: String) {
    FREE("free"),
    PRESET("preset"),
}

data class ViewerCameraVector(
    val x: Double,
    val y: Double,
    val z: Double,
)

/**
 * Serializable camera snapshot shared with the embedded viewer. Keeping the
 * orbit target is essential: position alone cannot faithfully restore an
 * OrbitControls camera after its WebView has been recreated.
 */
data class ViewerCameraState(
    val position: ViewerCameraVector,
    val target: ViewerCameraVector,
    val up: ViewerCameraVector,
    val fieldOfViewDegrees: Double = 38.0,
    val mode: ViewerCameraMode = ViewerCameraMode.FREE,
    val preset: CameraViewPreset? = null,
    val source: String = "api",
    val interactionActive: Boolean = false,
)

internal fun ViewerCameraState.toViewerJson(): JSONObject = JSONObject()
    .put("version", 1)
    .put("position", JSONArray(listOf(position.x, position.y, position.z)))
    .put("target", JSONArray(listOf(target.x, target.y, target.z)))
    .put("up", JSONArray(listOf(up.x, up.y, up.z)))
    .put("fieldOfViewDegrees", fieldOfViewDegrees)
    .put("mode", mode.wireValue)
    .put("preset", preset?.wireValue ?: JSONObject.NULL)
    .put("source", source)
    .put("interactionActive", interactionActive)

private fun JSONObject.viewerCameraVector(name: String): ViewerCameraVector {
    val values = getJSONArray(name)
    require(values.length() == 3) { "$name must contain three coordinates" }
    return ViewerCameraVector(
        x = values.getDouble(0),
        y = values.getDouble(1),
        z = values.getDouble(2),
    ).also { vector ->
        require(listOf(vector.x, vector.y, vector.z).all(Double::isFinite)) {
            "$name coordinates must be finite"
        }
    }
}

internal fun parseViewerCameraState(payload: String): ViewerCameraState {
    val json = JSONObject(payload)
    val modeValue = json.optString("mode", ViewerCameraMode.FREE.wireValue)
    val mode = ViewerCameraMode.entries.firstOrNull { it.wireValue == modeValue }
        ?: error("Unsupported viewer camera mode: $modeValue")
    val presetValue = json.optString("preset").takeUnless {
        json.isNull("preset") || it.isBlank()
    }
    val preset = presetValue?.let { wireValue ->
        CameraViewPreset.entries.firstOrNull { it.wireValue == wireValue }
            ?: error("Unsupported viewer camera preset: $wireValue")
    }
    require(mode != ViewerCameraMode.PRESET || preset != null) {
        "Preset camera mode requires a preset"
    }
    return ViewerCameraState(
        position = json.viewerCameraVector("position"),
        target = json.viewerCameraVector("target"),
        up = json.viewerCameraVector("up"),
        fieldOfViewDegrees = json.optDouble("fieldOfViewDegrees", 38.0).also { value ->
            require(value.isFinite() && value in 1.0..179.0) {
                "Camera field of view must be between 1 and 179 degrees"
            }
        },
        mode = mode,
        preset = preset,
        source = json.optString("source", "api"),
        interactionActive = json.optBoolean("interactionActive", false),
    )
}

enum class CameraFramingTarget(val wireValue: String) {
    MODELS("models"),
    SELECTED_MODEL("selectedModel"),
    PRINT_BED("printBed"),
}

/** Repeated camera commands remain observable through their requestId. */
data class CameraFramingRequest(
    val requestId: Int,
    val target: CameraFramingTarget,
)

/** Restores a camera snapshot without recreating or reloading the WebView. */
data class CameraRestoreRequest(
    val requestId: Int,
    val state: ViewerCameraState,
)

enum class ToolpathColorMode(val wireValue: String, val label: String) {
    LINE_WIDTH("lineWidth", "Ширина линии"),
    LINE_TYPE("lineType", "Тип линии"),
    SPEED("speed", "Скорость"),
    LAYER_HEIGHT("layerHeight", "Высота слоя"),
}

internal data class ViewerRendererLifecycle(
    val generation: Int = 0,
    val ready: Boolean = false,
) {
    fun onReady(callbackGeneration: Int): ViewerRendererLifecycle =
        if (callbackGeneration == generation) copy(ready = true) else this

    fun onRenderProcessGone(callbackGeneration: Int): ViewerRendererLifecycle =
        if (callbackGeneration == generation) copy(generation = generation + 1, ready = false) else this
}

private class DynamicModelPathHandler(
    private val modelFile: AtomicReference<File?>,
    private val gcodeFile: AtomicReference<File?>,
    private val objectFiles: AtomicReference<Map<String, File>>,
) : WebViewAssetLoader.PathHandler {
    override fun handle(path: String): WebResourceResponse? {
        val (file, mimeType) = when (path) {
            "current.stl" -> modelFile.get() to "model/stl"
            "current.gcode" -> gcodeFile.get() to "text/plain"
            else -> objectFiles.get()[path] to if (path.endsWith(".stl")) {
                "model/stl"
            } else {
                "application/octet-stream"
            }
        }
        val resolvedFile = file?.takeIf { it.isFile } ?: return null
        return runCatching {
            WebResourceResponse(
                mimeType,
                "utf-8",
                200,
                "OK",
                mapOf("Cache-Control" to "no-store"),
                FileInputStream(resolvedFile),
            )
        }.getOrNull()
    }
}

private class ViewerResources(context: Context) {
    val modelFile = AtomicReference<File?>(null)
    val gcodeFile = AtomicReference<File?>(null)
    val objectFiles = AtomicReference<Map<String, File>>(emptyMap())

    val assetLoader: WebViewAssetLoader = WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
        .addPathHandler("/model/", DynamicModelPathHandler(modelFile, gcodeFile, objectFiles))
        .build()
}

private class ViewerJavascriptBridge(
    private val onReadyCallback: () -> Unit,
    private val onSceneStateCallback: (ViewerSceneState) -> Unit,
    private val onObjectSelectedCallback: (ViewerObjectSelection) -> Unit,
    private val onToolpathSelectionCallback: (ViewerToolpathSelection?) -> Unit,
    private val onToolpathRenderedCallback: (Int) -> Unit,
    private val onCameraStateCallback: (ViewerCameraState) -> Unit,
    private val onErrorCallback: (String) -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onReady() {
        mainHandler.post(onReadyCallback)
    }

    @JavascriptInterface
    fun onSceneState(payload: String) {
        runCatching {
            val json = JSONObject(payload)
            ViewerSceneState(
                minimumX = json.getDouble("minimumX"),
                maximumX = json.getDouble("maximumX"),
                minimumY = json.getDouble("minimumY"),
                maximumY = json.getDouble("maximumY"),
                height = json.getDouble("height"),
                insideBed = json.getBoolean("insideBed"),
                minimumZ = json.optDouble("minimumZ", 0.0),
                maximumZ = json.optDouble("maximumZ", json.getDouble("height")),
            )
        }.onSuccess { state ->
            mainHandler.post { onSceneStateCallback(state) }
        }.onFailure { error ->
            mainHandler.post { onErrorCallback(error.message ?: "Invalid viewer state") }
        }
    }

    @JavascriptInterface
    fun onObjectSelected(payload: String) {
        runCatching { parseViewerObjectSelection(payload) }
            .onSuccess { selection ->
                mainHandler.post { onObjectSelectedCallback(selection) }
            }
            .onFailure { error ->
                mainHandler.post { onErrorCallback(error.message ?: "Invalid object selection") }
            }
    }

    @JavascriptInterface
    fun onToolpathSelection(payload: String) {
        runCatching { parseViewerToolpathSelection(payload) }
            .onSuccess { selection ->
                mainHandler.post { onToolpathSelectionCallback(selection) }
            }
            .onFailure { error ->
                mainHandler.post { onErrorCallback(error.message ?: "Invalid toolpath selection") }
            }
    }

    @JavascriptInterface
    fun onToolpathRendered(segmentCount: Int) {
        mainHandler.post { onToolpathRenderedCallback(segmentCount) }
    }

    @JavascriptInterface
    fun onCameraState(payload: String) {
        runCatching { parseViewerCameraState(payload) }
            .onSuccess { state ->
                mainHandler.post { onCameraStateCallback(state) }
            }
            .onFailure { error ->
                mainHandler.post { onErrorCallback(error.message ?: "Invalid camera state") }
            }
    }

    @JavascriptInterface
    fun onError(message: String) {
        mainHandler.post { onErrorCallback(message) }
    }
}

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun ModelViewer(
    modelFile: File?,
    gcodeFile: File?,
    transform: ModelTransform,
    bedWidth: Double,
    bedDepth: Double,
    mode: ViewerMode,
    darkTheme: Boolean,
    toolpathMinimumLayer: Int = 0,
    toolpathMaximumLayer: Int = Int.MAX_VALUE,
    toolpathColorMode: ToolpathColorMode = ToolpathColorMode.LINE_WIDTH,
    toolpathProgress: Float = 1f,
    showExtrusion: Boolean = true,
    showTravel: Boolean = false,
    includeToolpathCommands: Boolean = false,
    cameraResetRequest: Int = 0,
    cameraViewRequest: CameraViewRequest? = null,
    cameraFramingRequest: CameraFramingRequest? = null,
    cameraRestoreRequest: CameraRestoreRequest? = null,
    initialCameraState: ViewerCameraState? = null,
    onSceneState: (ViewerSceneState) -> Unit,
    onToolpathSelection: (ViewerToolpathSelection?) -> Unit = {},
    onToolpathRendered: (Int) -> Unit = {},
    onCameraStateChange: (ViewerCameraState) -> Unit = {},
    onError: (String) -> Unit,
    viewerHeight: Dp? = 330.dp,
    showStatus: Boolean = true,
    modifier: Modifier = Modifier,
    modelObjects: List<ViewerModelObject> = emptyList(),
    selectedObjectId: String? = null,
    onObjectSelected: (ViewerObjectSelection) -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val uiLanguage = currentUiLanguage()
    val viewerLanguage = if (uiLanguage == UiLanguage.RUSSIAN) "ru" else "en"
    val viewerContentDescription = if (uiLanguage == UiLanguage.RUSSIAN) {
        "Предпросмотр 3D-модели и траектории"
    } else {
        "3D model and toolpath preview"
    }
    val resources = remember { ViewerResources(context.applicationContext) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var rendererLifecycle by remember { mutableStateOf(ViewerRendererLifecycle()) }
    val rendererDisposing = remember { AtomicBoolean(false) }
    val destroyedWebViews = remember { ConcurrentHashMap.newKeySet<WebView>() }
    val viewerMainHandler = remember { Handler(Looper.getMainLooper()) }

    fun destroyWebViewOnce(view: WebView?) {
        if (view == null || !destroyedWebViews.add(view)) return
        runCatching { view.stopLoading() }
        runCatching { view.removeJavascriptInterface("AndroidBridge") }
        runCatching { view.destroy() }
    }

    val viewerReady = rendererLifecycle.ready
    val rendererGeneration = rendererLifecycle.generation
    val currentOnSceneState by rememberUpdatedState(onSceneState)
    val currentOnObjectSelected by rememberUpdatedState(onObjectSelected)
    val currentOnToolpathSelection by rememberUpdatedState(onToolpathSelection)
    val currentOnToolpathRendered by rememberUpdatedState(onToolpathRendered)
    val currentOnCameraStateChange by rememberUpdatedState(onCameraStateChange)
    val currentOnError by rememberUpdatedState(onError)
    val bridge = remember(rendererGeneration) {
        ViewerJavascriptBridge(
            onReadyCallback = {
                rendererLifecycle = rendererLifecycle.onReady(rendererGeneration)
            },
            onSceneStateCallback = { currentOnSceneState(it) },
            onObjectSelectedCallback = { currentOnObjectSelected(it) },
            onToolpathSelectionCallback = { currentOnToolpathSelection(it) },
            onToolpathRenderedCallback = { currentOnToolpathRendered(it) },
            onCameraStateCallback = { currentOnCameraStateChange(it) },
            onErrorCallback = { currentOnError(it) },
        )
    }

    key(rendererGeneration) {
        AndroidView(
            factory = { androidContext ->
                WebView(androidContext).apply {
                    setBackgroundColor(if (darkTheme) Color.rgb(32, 36, 33) else Color.rgb(238, 241, 236))
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = false
                    settings.allowContentAccess = false
                    settings.allowFileAccess = false
                    settings.mediaPlaybackRequiresUserGesture = true
                    setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
                    addJavascriptInterface(bridge, "AndroidBridge")
                    webViewClient = object : WebViewClient() {
                        override fun onRenderProcessGone(
                            view: WebView?,
                            detail: RenderProcessGoneDetail?,
                        ): Boolean {
                            val callbackIsCurrent = rendererLifecycle.generation == rendererGeneration
                            val failedView = view ?: webView.takeIf { callbackIsCurrent }
                            destroyWebViewOnce(failedView)
                            if (webView === failedView) webView = null
                            if (rendererDisposing.get()) return true
                            val nextLifecycle = rendererLifecycle.onRenderProcessGone(rendererGeneration)
                            if (nextLifecycle.generation != rendererLifecycle.generation) {
                                rendererLifecycle = nextLifecycle
                                currentOnError("3D renderer stopped and is restarting.")
                            }
                            return true
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): Boolean = request?.url?.host != "appassets.androidplatform.net"

                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): WebResourceResponse? {
                            val uri: Uri = request?.url ?: return null
                            return resources.assetLoader.shouldInterceptRequest(uri)
                        }
                    }
                    loadUrl(
                        "https://appassets.androidplatform.net/assets/viewer/index.html?theme=" +
                            (if (darkTheme) "dark" else "light") +
                            "&lang=$viewerLanguage" +
                            "&status=${if (showStatus) "visible" else "hidden"}",
                    )
                    webView = this
                }
            },
            update = { view ->
                view.setBackgroundColor(if (darkTheme) Color.rgb(32, 36, 33) else Color.rgb(238, 241, 236))
                view.evaluateJavascript(
                    "window.FeresaSlicerViewer?.setTheme(${if (darkTheme) "true" else "false"})",
                    null,
                )
            },
            modifier = modifier
                .fillMaxWidth()
                .then(
                    if (viewerHeight != null) {
                        Modifier.height(viewerHeight)
                    } else {
                        Modifier.fillMaxHeight()
                    },
                )
                .semantics { contentDescription = viewerContentDescription },
        )
    }

    val modelObjectFilesKey = modelObjects.map { model ->
        listOf(model.objectId, model.file.absolutePath, model.file.lastModified(), model.visible)
    }
    val modelObjectTransformsKey = modelObjects.map { model -> model.objectId to model.transform }
    val legacyModelFile = viewerLegacyModelFile(modelFile, modelObjects)
    // Intentionally not part of the load effect key. Camera callbacks may update
    // this value continuously and must never cause STL resources to reload.
    val initialCameraJson = initialCameraState?.toViewerJson()?.toString() ?: "null"

    LaunchedEffect(viewerReady, legacyModelFile, modelObjectFilesKey) {
        resources.modelFile.set(legacyModelFile)
        resources.objectFiles.set(
            modelObjects.mapIndexed { index, model -> "objects/$index.stl" to model.file }.toMap(),
        )
        if (!viewerReady) return@LaunchedEffect

        if (modelObjects.isNotEmpty()) {
            val objectIds = modelObjects.map { it.objectId }
            if (objectIds.any { it.isBlank() } || objectIds.distinct().size != objectIds.size) {
                currentOnError("Viewer model objectId values must be non-empty and unique")
                return@LaunchedEffect
            }
            val objects = JSONArray()
            modelObjects.forEachIndexed { index, model ->
                objects.put(
                    JSONObject()
                        .put("objectId", model.objectId)
                        .put("url", "../../model/objects/$index.stl")
                        .put("visible", model.visible)
                        .put(
                            "transform",
                            model.transform.toViewerJson(),
                        ),
                )
            }
            val payload = JSONObject()
                .put("version", System.nanoTime().toString())
                .put("objects", objects)
                .put("selectedObjectId", selectedObjectId ?: JSONObject.NULL)
                .put("frameAll", true)
            webView?.evaluateJavascript(
                "window.FeresaSlicerViewer.loadModels($payload,$initialCameraJson)",
                null,
            )
        } else if (legacyModelFile != null) {
            webView?.evaluateJavascript(
                "window.FeresaSlicerViewer.loadModel(${System.nanoTime()},$initialCameraJson)",
                null,
            )
        } else {
            val payload = JSONObject()
                .put("version", System.nanoTime().toString())
                .put("objects", JSONArray())
                .put("selectedObjectId", JSONObject.NULL)
                .put("frameAll", true)
            webView?.evaluateJavascript(
                "window.FeresaSlicerViewer.loadModels($payload,$initialCameraJson)",
                null,
            )
        }
    }

    LaunchedEffect(viewerReady, gcodeFile) {
        resources.gcodeFile.set(gcodeFile)
        if (viewerReady) {
            val script = if (gcodeFile != null) {
                "window.FeresaSlicerViewer.loadToolpath(${System.nanoTime()})"
            } else {
                "window.FeresaSlicerViewer.clearToolpath()"
            }
            webView?.evaluateJavascript(script, null)
        }
    }

    LaunchedEffect(viewerReady, transform, modelObjectTransformsKey) {
        if (viewerReady && modelObjects.isNotEmpty()) {
            modelObjects.forEach { model ->
                val payload = model.transform.toViewerJson()
                webView?.evaluateJavascript(
                    "window.FeresaSlicerViewer.updateObjectTransform(${JSONObject.quote(model.objectId)},$payload)",
                    null,
                )
            }
        } else if (viewerReady) {
            val payload = transform.toViewerJson()
            webView?.evaluateJavascript(
                "window.FeresaSlicerViewer.updateTransform($payload)",
                null,
            )
        }
    }

    LaunchedEffect(viewerReady, selectedObjectId, modelObjectFilesKey) {
        if (viewerReady && modelObjects.isNotEmpty()) {
            val selection = selectedObjectId?.let(JSONObject::quote) ?: "null"
            webView?.evaluateJavascript(
                "window.FeresaSlicerViewer.selectObject($selection)",
                null,
            )
        }
    }

    LaunchedEffect(viewerReady, bedWidth, bedDepth) {
        if (viewerReady) {
            webView?.evaluateJavascript(
                "window.FeresaSlicerViewer.setBed($bedWidth,$bedDepth)",
                null,
            )
        }
    }

    LaunchedEffect(viewerReady, mode) {
        if (viewerReady) {
            webView?.evaluateJavascript(
                "window.FeresaSlicerViewer.setViewMode('${mode.wireValue}')",
                null,
            )
        }
    }


    LaunchedEffect(
        viewerReady,
        toolpathMinimumLayer,
        toolpathMaximumLayer,
        toolpathColorMode,
        toolpathProgress,
        showExtrusion,
        showTravel,
        includeToolpathCommands,
    ) {
        if (viewerReady) {
            val payload = JSONObject()
                .put("minimumLayer", toolpathMinimumLayer.coerceAtLeast(0))
                .put("maximumLayer", toolpathMaximumLayer.coerceAtLeast(toolpathMinimumLayer))
                .put("colorMode", toolpathColorMode.wireValue)
                .put("maximumSegmentRatio", toolpathProgress.coerceIn(0f, 1f))
                .put("showExtrusion", showExtrusion)
                .put("showTravel", showTravel)
                .put("includeCommands", includeToolpathCommands)
            webView?.evaluateJavascript(
                "window.FeresaSlicerViewer.setToolpathPreview($payload)",
                null,
            )
        }
    }

    LaunchedEffect(viewerReady, cameraResetRequest) {
        if (viewerReady && cameraResetRequest > 0) {
            webView?.evaluateJavascript(
                "window.FeresaSlicerViewer.resetCamera()",
                null,
            )
        }
    }

    LaunchedEffect(viewerReady, cameraViewRequest) {
        if (viewerReady && cameraViewRequest != null) {
            val preset = JSONObject.quote(cameraViewRequest.preset.wireValue)
            webView?.evaluateJavascript(
                "window.FeresaSlicerViewer.setCameraView($preset)",
                null,
            )
        }
    }

    LaunchedEffect(viewerReady, cameraFramingRequest) {
        if (viewerReady && cameraFramingRequest != null) {
            val functionName = when (cameraFramingRequest.target) {
                CameraFramingTarget.MODELS -> "fitModels"
                CameraFramingTarget.SELECTED_MODEL -> "fitSelectedModel"
                CameraFramingTarget.PRINT_BED -> "showWholeBed"
            }
            webView?.evaluateJavascript(
                "window.FeresaSlicerViewer.$functionName()",
                null,
            )
        }
    }

    LaunchedEffect(viewerReady, cameraRestoreRequest) {
        if (viewerReady && cameraRestoreRequest != null) {
            val payload = cameraRestoreRequest.state.toViewerJson()
            webView?.evaluateJavascript(
                "window.FeresaSlicerViewer.restoreCameraState($payload)",
                null,
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            rendererDisposing.set(true)
            val view = webView
            webView = null
            if (view != null) {
                val destroyFallback = Runnable { destroyWebViewOnce(view) }
                viewerMainHandler.postDelayed(destroyFallback, VIEWER_DISPOSE_FALLBACK_MILLIS)
                runCatching {
                    view.evaluateJavascript(
                        "window.FeresaSlicerViewer?.dispose?.()",
                    ) {
                        viewerMainHandler.removeCallbacks(destroyFallback)
                        destroyFallback.run()
                    }
                }.onFailure {
                    viewerMainHandler.removeCallbacks(destroyFallback)
                    destroyFallback.run()
                }
            }
        }
    }
}

private const val VIEWER_DISPOSE_FALLBACK_MILLIS = 250L
