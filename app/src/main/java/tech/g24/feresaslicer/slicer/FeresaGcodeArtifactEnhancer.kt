// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.slicer

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import tech.g24.feresaslicer.BuildConfig

internal data class OrcaGcodeEnhancementRequest(
    val gcode: File,
    val report: SliceReport,
    val modelNames: List<String>,
    val profiles: OrcaSelectedProfiles,
    val nativeSettings: Map<String, String>,
)

internal fun interface OrcaGcodeArtifactEnhancer {
    fun enhance(request: OrcaGcodeEnhancementRequest)
}

/**
 * JVM pipeline tests use this default on the injectable overload, avoiding android.graphics stubs.
 * The public production overload supplies [FeresaAndroidGcodeArtifactEnhancer] explicitly.
 */
internal val NoOpGcodeArtifactEnhancer = OrcaGcodeArtifactEnhancer { }

internal object FeresaAndroidGcodeArtifactEnhancer : OrcaGcodeArtifactEnhancer {
    override fun enhance(request: OrcaGcodeEnhancementRequest) {
        // A renderer failure must not discard valid native G-code. Identification and print
        // metadata are still useful, so write them with an empty thumbnail list as a fallback.
        val thumbnails = runCatching {
            AndroidGcodeThumbnailRenderer.render(request.gcode)
        }.getOrDefault(emptyList())
        FeresaGcodePostProcessor.enhance(
            gcode = request.gcode,
            thumbnails = thumbnails,
            metadata = FeresaGcodeMetadata(
                applicationVersion = BuildConfig.VERSION_NAME,
                generatedAt = LocalDateTime.now().format(OrcaHeaderTimestamp),
                engineName = OrcaMobileEngineName,
                engineRevision = OrcaMobileEngineRevision,
                modelNames = request.modelNames,
                printerProfileName = request.profiles.printer?.name,
                processProfileName = request.profiles.process?.name,
                filamentProfileName = request.profiles.filament?.name,
                nativeSettings = request.nativeSettings,
                report = request.report,
            ),
        )
    }
}

private val OrcaHeaderTimestamp =
    DateTimeFormatter.ofPattern("yyyy-MM-dd 'at' HH:mm:ss", Locale.US)
private const val OrcaMobileEngineName = "OrcaSlicer Mobile"
private const val OrcaMobileEngineRevision = "6fc2e14b9a222301f4432cee26d7ab37d3be86d0"
