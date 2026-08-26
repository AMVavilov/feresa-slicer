// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.slicer

import java.io.File

/** Files owned by one slicing attempt. The caller keeps [gcode] only after a successful report. */
data class OrcaPlateSliceFiles(
    val config: File,
    val composedPlate: File,
    val gcode: File,
) {
    init {
        val paths = listOf(config, composedPlate, gcode).map { it.absoluteFile.normalize().path }
        require(paths.distinct().size == paths.size) { "Slice files must use distinct paths" }
    }
}

data class OrcaPlateSliceResult(
    val report: SliceReport,
    val composition: StlPlateComposition,
    val files: OrcaPlateSliceFiles,
    /** Exact native-ready settings written to [OrcaPlateSliceFiles.config]. */
    val nativeSettings: Map<String, String>,
)

internal fun interface OrcaPlateSliceEngine {
    fun slice(
        inputPath: String,
        configPath: String,
        outputPath: String,
        settings: SlicerSettings,
        onProgress: ((progress: Int, stage: String) -> Unit)?,
    ): SliceReport
}

/**
 * The production Model-screen pipeline shared with the device release tests.
 *
 * Keeping composition, full Orca configuration and the JNI invocation here prevents release tests
 * from accidentally exercising an easier path than the one reached by the UI.
 */
object OrcaPlateSlicePipeline {
    fun slice(
        placements: List<StlPlatePlacement>,
        profiles: OrcaSelectedProfiles,
        machineFilament: OrcaMachineFilamentScalars,
        liveProcessSettings: OrcaProcessSettingsPayload,
        baseSettings: SlicerSettings,
        files: OrcaPlateSliceFiles,
        modelNames: List<String> = placements.map { it.file.nameWithoutExtension },
        onProgress: ((progress: Int, stage: String) -> Unit)? = null,
    ): OrcaPlateSliceResult = slice(
        placements = placements,
        profiles = profiles,
        machineFilament = machineFilament,
        liveProcessSettings = liveProcessSettings,
        baseSettings = baseSettings,
        files = files,
        modelNames = modelNames,
        onProgress = onProgress,
        engine = OrcaPlateSliceEngine { inputPath, configPath, outputPath, settings, callback ->
            OrcaNativeEngine().sliceModel(
                inputPath = inputPath,
                configPath = configPath,
                outputPath = outputPath,
                settings = settings,
                onProgress = callback,
            )
        },
        artifactEnhancer = FeresaAndroidGcodeArtifactEnhancer,
    )

    internal fun slice(
        placements: List<StlPlatePlacement>,
        profiles: OrcaSelectedProfiles,
        machineFilament: OrcaMachineFilamentScalars,
        liveProcessSettings: OrcaProcessSettingsPayload,
        baseSettings: SlicerSettings,
        files: OrcaPlateSliceFiles,
        modelNames: List<String> = placements.map { it.file.nameWithoutExtension },
        onProgress: ((progress: Int, stage: String) -> Unit)? = null,
        engine: OrcaPlateSliceEngine,
        artifactEnhancer: OrcaGcodeArtifactEnhancer = NoOpGcodeArtifactEnhancer,
    ): OrcaPlateSliceResult {
        require(placements.isNotEmpty()) { "At least one model is required for slicing" }
        placements.forEach { placement ->
            require(placement.file.isFile) { "Model file does not exist: ${placement.file}" }
            require(placement.file.absoluteFile.normalize() !in listOf(
                files.config.absoluteFile.normalize(),
                files.composedPlate.absoluteFile.normalize(),
                files.gcode.absoluteFile.normalize(),
            )) { "A slice output path must not overwrite a source model" }
        }

        val dynamicConfig = OrcaDynamicPrintConfigBuilder.build(
            profiles = profiles,
            machineFilament = machineFilament,
            liveProcessSettings = liveProcessSettings,
        )
        dynamicConfig.writeTo(files.config)
        val composition = StlPlateComposer.compose(placements, files.composedPlate)
        val preparedSettings = baseSettings.copy(
            modelPositionXmm = composition.bounds.centerX,
            modelPositionYmm = composition.bounds.centerY,
            modelRotationDegrees = 0.0,
            modelScale = 1.0,
            // The composer already applied full XYZ/non-uniform transforms. Moving the result to
            // Z=0 here would silently undo intentionally raised objects and support scenarios.
            ensureModelOnBed = false,
        )
        val nativeReport = engine.slice(
            inputPath = composition.file.path,
            configPath = files.config.path,
            outputPath = files.gcode.path,
            settings = preparedSettings,
            onProgress = onProgress,
        )
        val enhancementFailure = if (nativeReport.success) {
            runCatching {
                artifactEnhancer.enhance(
                    OrcaGcodeEnhancementRequest(
                        gcode = files.gcode,
                        report = nativeReport,
                        modelNames = modelNames.filter(String::isNotBlank).distinct()
                            .ifEmpty { placements.map { it.file.nameWithoutExtension }.distinct() },
                        profiles = profiles,
                        nativeSettings = dynamicConfig.settings,
                    ),
                )
            }.exceptionOrNull()
        } else {
            null
        }
        val report = if (enhancementFailure == null) {
            nativeReport
        } else {
            nativeReport.copy(
                message = "${nativeReport.message}; preview metadata could not be embedded",
            )
        }
        return OrcaPlateSliceResult(
            report = report,
            composition = composition,
            files = files,
            nativeSettings = dynamicConfig.settings,
        )
    }
}
