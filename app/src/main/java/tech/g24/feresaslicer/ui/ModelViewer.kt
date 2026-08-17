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
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.atomic.AtomicReference

data class ModelTransform(
    val positionX: Double = 110.0,
    val positionY: Double = 110.0,
    val rotationDegrees: Double = 0.0,
    val scale: Double = 1.0,
)

data class ViewerSceneState(
    val minimumX: Double,
    val maximumX: Double,
    val minimumY: Double,
    val maximumY: Double,
    val height: Double,
    val insideBed: Boolean,
)

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

private class DynamicModelPathHandler(
    private val modelFile: AtomicReference<File?>,
    private val gcodeFile: AtomicReference<File?>,
) : WebViewAssetLoader.PathHandler {
    override fun handle(path: String): WebResourceResponse? {
        val (file, mimeType) = when (path) {
            "current.stl" -> modelFile.get() to "model/stl"
            "current.gcode" -> gcodeFile.get() to "text/plain"
            else -> null to "application/octet-stream"
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

    val assetLoader: WebViewAssetLoader = WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
        .addPathHandler("/model/", DynamicModelPathHandler(modelFile, gcodeFile))
        .build()
}

private class ViewerJavascriptBridge(
    private val onReadyCallback: () -> Unit,
    private val onSceneStateCallback: (ViewerSceneState) -> Unit,
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
            )
        }.onSuccess { state ->
            mainHandler.post { onSceneStateCallback(state) }
        }.onFailure { error ->
            mainHandler.post { onErrorCallback(error.message ?: "Invalid viewer state") }
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
    onError: (String) -> Unit,
    viewerHeight: Dp = 330.dp,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val resources = remember { ViewerResources(context.applicationContext) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var viewerReady by remember { mutableStateOf(false) }
    val currentOnSceneState by rememberUpdatedState(onSceneState)
    val currentOnError by rememberUpdatedState(onError)
    val bridge = remember {
        ViewerJavascriptBridge(
            onReadyCallback = { viewerReady = true },
            onSceneStateCallback = { currentOnSceneState(it) },
            onErrorCallback = { currentOnError(it) },
        )
    }

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
                        view?.destroy()
                        currentOnError("3D renderer stopped. Numeric placement controls remain available.")
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
            .semantics { contentDescription = "3D model and toolpath preview" },
    )

    LaunchedEffect(viewerReady, modelFile) {
        resources.modelFile.set(modelFile)
        if (viewerReady && modelFile != null) {
            webView?.evaluateJavascript(
                "window.FeresaSlicerViewer.loadModel(${System.nanoTime()})",
                null,
            )
        }
    }

    LaunchedEffect(viewerReady, gcodeFile) {
        resources.gcodeFile.set(gcodeFile)
        if (viewerReady && gcodeFile != null) {
            webView?.evaluateJavascript(
                "window.FeresaSlicerViewer.loadToolpath(${System.nanoTime()})",
                null,
            )
        }
    }

    LaunchedEffect(viewerReady, transform) {
        if (viewerReady) {
            val payload = JSONObject()
                .put("positionX", transform.positionX)
                .put("positionY", transform.positionY)
                .put("rotationDegrees", transform.rotationDegrees)
                .put("scale", transform.scale)
            webView?.evaluateJavascript(
                "window.FeresaSlicerViewer.updateTransform($payload)",
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
