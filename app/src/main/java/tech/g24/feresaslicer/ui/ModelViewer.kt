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
    val displayedSegmentCount: Int,
    val eligibleSegmentCount: Int,
    val layer: Int,
    val x: Double,
    val y: Double,
    val z: Double,
    val speedMmSeconds: Double,
    val extrusion: Boolean,
    val lineType: String,
    val lineTypeLabel: String,
    val lineWidthMm: Double?,
    val layerHeightMm: Double?,
)

internal fun parseViewerToolpathSelection(payload: String): ViewerToolpathSelection? {
    val json = JSONObject(payload)
    if (!json.optBoolean("selected", false)) return null

    fun optionalFiniteDouble(name: String): Double? = if (json.isNull(name)) {
        null
    } else {
        json.optDouble(name, Double.NaN).takeIf { it.isFinite() }
    }

    return ViewerToolpathSelection(
        displayedSegmentCount = json.getInt("displayedSegmentCount"),
        eligibleSegmentCount = json.getInt("eligibleSegmentCount"),
        layer = json.getInt("layer"),
        x = json.getDouble("x"),
        y = json.getDouble("y"),
        z = json.getDouble("z"),
        speedMmSeconds = json.getDouble("speed"),
        extrusion = json.getBoolean("extrusion"),
        lineType = json.getString("lineType"),
        lineTypeLabel = json.getString("lineTypeLabel"),
        lineWidthMm = optionalFiniteDouble("lineWidth"),
        layerHeightMm = optionalFiniteDouble("layerHeight"),
    )
}

enum class ViewerMode(val wireValue: String) {
    MODEL("model"),
    TOOLPATH("toolpath"),
}

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
    cameraResetRequest: Int = 0,
    onSceneState: (ViewerSceneState) -> Unit,
    onToolpathSelection: (ViewerToolpathSelection?) -> Unit = {},
    onError: (String) -> Unit,
    viewerHeight: Dp = 330.dp,
    modifier: Modifier = Modifier,
    modelObjects: List<ViewerModelObject> = emptyList(),
    selectedObjectId: String? = null,
    onObjectSelected: (ViewerObjectSelection) -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val viewerContentDescription = if (currentUiLanguage() == UiLanguage.RUSSIAN) {
        "Предпросмотр 3D-модели и траектории"
    } else {
        "3D model and toolpath preview"
    }
    val resources = remember { ViewerResources(context.applicationContext) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var rendererLifecycle by remember { mutableStateOf(ViewerRendererLifecycle()) }
    val viewerReady = rendererLifecycle.ready
    val rendererGeneration = rendererLifecycle.generation
    val currentOnSceneState by rememberUpdatedState(onSceneState)
    val currentOnObjectSelected by rememberUpdatedState(onObjectSelected)
    val currentOnToolpathSelection by rememberUpdatedState(onToolpathSelection)
    val currentOnError by rememberUpdatedState(onError)
    val bridge = remember(rendererGeneration) {
        ViewerJavascriptBridge(
            onReadyCallback = {
                rendererLifecycle = rendererLifecycle.onReady(rendererGeneration)
            },
            onSceneStateCallback = { currentOnSceneState(it) },
            onObjectSelectedCallback = { currentOnObjectSelected(it) },
            onToolpathSelectionCallback = { currentOnToolpathSelection(it) },
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
                    addJavascriptInterface(bridge, "AndroidBridge")
                    webViewClient = object : WebViewClient() {
                        override fun onRenderProcessGone(
                            view: WebView?,
                            detail: RenderProcessGoneDetail?,
                        ): Boolean {
                            val callbackIsCurrent = rendererLifecycle.generation == rendererGeneration
                            val failedView = view ?: webView.takeIf { callbackIsCurrent }
                            failedView?.removeJavascriptInterface("AndroidBridge")
                            failedView?.destroy()
                            val nextLifecycle = rendererLifecycle.onRenderProcessGone(rendererGeneration)
                            if (nextLifecycle.generation != rendererLifecycle.generation) {
                                if (webView === failedView) webView = null
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
                            if (darkTheme) "dark" else "light",
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
                .height(viewerHeight)
                .semantics { contentDescription = viewerContentDescription },
        )
    }

    val modelObjectFilesKey = modelObjects.map { model ->
        listOf(model.objectId, model.file.absolutePath, model.file.lastModified(), model.visible)
    }
    val modelObjectTransformsKey = modelObjects.map { model -> model.objectId to model.transform }
    val legacyModelFile = viewerLegacyModelFile(modelFile, modelObjects)

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
                "window.FeresaSlicerViewer.loadModels($payload)",
                null,
            )
        } else if (legacyModelFile != null) {
            webView?.evaluateJavascript(
                "window.FeresaSlicerViewer.loadModel(${System.nanoTime()})",
                null,
            )
        } else {
            val payload = JSONObject()
                .put("version", System.nanoTime().toString())
                .put("objects", JSONArray())
                .put("selectedObjectId", JSONObject.NULL)
                .put("frameAll", true)
            webView?.evaluateJavascript(
                "window.FeresaSlicerViewer.loadModels($payload)",
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
    ) {
        if (viewerReady) {
            val payload = JSONObject()
                .put("minimumLayer", toolpathMinimumLayer.coerceAtLeast(0))
                .put("maximumLayer", toolpathMaximumLayer.coerceAtLeast(toolpathMinimumLayer))
                .put("colorMode", toolpathColorMode.wireValue)
                .put("maximumSegmentRatio", toolpathProgress.coerceIn(0f, 1f))
                .put("showExtrusion", showExtrusion)
                .put("showTravel", showTravel)
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

    DisposableEffect(Unit) {
        onDispose {
            webView?.removeJavascriptInterface("AndroidBridge")
            webView?.destroy()
            webView = null
        }
    }
}
