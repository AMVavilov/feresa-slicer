// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.slicer

import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import tech.g24.feresaslicer.auth.OrcaCloudProfile
import tech.g24.feresaslicer.auth.OrcaProfileType

class OrcaDynamicPrintConfigBuilderTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun overlaysDefaultsPrinterProcessFilamentScalarsAndLiveProcessInOrcaOrder() {
        val supportedKeys = RequiredMachineKeys + setOf(
            "default_only",
            "shared_profile_option",
            "wall_loops",
        )
        val config = OrcaDynamicPrintConfigBuilder.build(
            runtimeDefaults = mapOf(
                "default_only" to "preserved",
                "shared_profile_option" to "default",
                "wall_loops" to "2",
            ),
            supportedKeys = supportedKeys,
            profiles = OrcaSelectedProfiles(
                printer = profile(
                    OrcaProfileType.PRINTER,
                    """{"shared_profile_option":"printer","wall_loops":"3","name":"metadata"}""",
                ),
                process = profile(
                    OrcaProfileType.PROCESS,
                    """{"shared_profile_option":"process","wall_loops":"4"}""",
                ),
                filament = profile(
                    OrcaProfileType.FILAMENT,
                    """{"shared_profile_option":"filament","wall_loops":"5"}""",
                ),
            ),
            machineFilament = scalarSettings(nozzleDiameter = "0.60"),
            liveProcessSettings = OrcaProcessSettingsPayload.from(mapOf("wall_loops" to "6")),
        )

        assertEquals("preserved", config.settings["default_only"])
        assertEquals("filament", config.settings["shared_profile_option"])
        assertEquals("0.6", config.settings["nozzle_diameter"])
        assertEquals("6", config.settings["wall_loops"])
        assertTrue(config.ini.contains("default_only = preserved\n"))
        assertTrue(config.ini.contains("wall_loops = 6\n"))
        assertEquals(config.settings.keys.sorted(), config.settings.keys.toList())
    }

    @Test
    fun machineAndFilamentScalarsUseCurrentOrcaKeysAndCanonicalValues() {
        val settings = OrcaMachineFilamentScalars(
            bedWidthMm = 220.0,
            bedDepthMm = 180.5,
            printableHeightMm = 250.0,
            nozzleDiameterMm = " 0.40 ",
            filamentDiameterMm = "1.750",
            nozzleTemperatureC = "210.0",
            bedTemperatureC = "60",
            gcodeFlavor = " marlin2 ",
        ).toOrcaSettings()

        assertEquals("FFF", settings["printer_technology"])
        assertEquals("0x0,220x0,220x180.5,0x180.5", settings["printable_area"])
        assertEquals("250", settings["printable_height"])
        assertEquals("marlin2", settings["gcode_flavor"])
        assertEquals("0.4", settings["nozzle_diameter"])
        assertEquals("1.75", settings["filament_diameter"])
        assertEquals("210", settings["nozzle_temperature"])
        assertEquals("210", settings["nozzle_temperature_initial_layer"])
        assertEquals("High Temp Plate", settings["curr_bed_type"])
        assertEquals("60", settings["hot_plate_temp"])
        assertEquals("60", settings["hot_plate_temp_initial_layer"])
        assertEquals(RequiredMachineKeys, settings.keys)
    }

    @Test
    fun failsFastWhenAnExposedPrintControlIsNotSupportedByLoadedEngine() {
        assertThrows(IllegalArgumentException::class.java) {
            OrcaDynamicPrintConfigBuilder.build(
                runtimeDefaults = emptyMap(),
                supportedKeys = RequiredMachineKeys,
                profiles = OrcaSelectedProfiles(),
                machineFilament = scalarSettings(),
                liveProcessSettings = OrcaProcessSettingsPayload.from(
                    mapOf("not_in_this_engine" to "1"),
                ),
            )
        }
    }

    @Test
    fun resolvesCloudParentBeforeChildAndLetsChildOverrideIt() {
        val parent = OrcaCloudProfile(
            id = "process-parent",
            name = "Parent quality",
            type = OrcaProfileType.PROCESS,
            contentJson = """{"wall_loops":"3","top_shell_layers":"6"}""",
            updatedTime = 1L,
        )
        val child = OrcaCloudProfile(
            id = "process-child",
            name = "Child quality",
            type = OrcaProfileType.PROCESS,
            contentJson = """{"inherits":"Parent quality","wall_loops":"5"}""",
            updatedTime = 2L,
        )
        val config = OrcaDynamicPrintConfigBuilder.build(
            runtimeDefaults = mapOf("wall_loops" to "2", "top_shell_layers" to "4"),
            supportedKeys = RequiredMachineKeys + setOf("wall_loops", "top_shell_layers"),
            profiles = OrcaSelectedProfiles(
                process = child,
                availableCloudProfiles = listOf(parent, child),
            ),
            machineFilament = scalarSettings(),
            liveProcessSettings = OrcaProcessSettingsPayload.from(emptyMap<String, Any?>()),
        )

        assertEquals("5", config.settings["wall_loops"])
        assertEquals("6", config.settings["top_shell_layers"])
    }

    @Test
    fun rejectsCyclesInCloudProfileInheritance() {
        val first = OrcaCloudProfile(
            id = "first",
            name = "First",
            type = OrcaProfileType.PROCESS,
            contentJson = """{"inherits":"Second","wall_loops":"3"}""",
            updatedTime = 1L,
        )
        val second = OrcaCloudProfile(
            id = "second",
            name = "Second",
            type = OrcaProfileType.PROCESS,
            contentJson = """{"inherits":"First","wall_loops":"4"}""",
            updatedTime = 1L,
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            OrcaDynamicPrintConfigBuilder.build(
                runtimeDefaults = mapOf("wall_loops" to "2"),
                supportedKeys = RequiredMachineKeys + "wall_loops",
                profiles = OrcaSelectedProfiles(
                    process = first,
                    availableCloudProfiles = listOf(first, second),
                ),
                machineFilament = scalarSettings(),
                liveProcessSettings = OrcaProcessSettingsPayload.from(emptyMap<String, Any?>()),
            )
        }
        assertTrue(error.message.orEmpty().contains("Cyclic Orca profile inheritance"))
    }

    @Test
    fun rejectsMissingCloudProfileParentInsteadOfSilentlyUsingDefaults() {
        val child = OrcaCloudProfile(
            id = "process-child",
            name = "Child quality",
            type = OrcaProfileType.PROCESS,
            contentJson = """{"inherits":"Missing system quality","wall_loops":"5"}""",
            updatedTime = 2L,
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            OrcaDynamicPrintConfigBuilder.build(
                runtimeDefaults = mapOf("wall_loops" to "2"),
                supportedKeys = RequiredMachineKeys + "wall_loops",
                profiles = OrcaSelectedProfiles(process = child),
                machineFilament = scalarSettings(),
                liveProcessSettings = OrcaProcessSettingsPayload.from(emptyMap<String, Any?>()),
            )
        }

        assertTrue(error.message.orEmpty().contains("inherits missing preset"))
    }

    @Test
    fun rejectsAmbiguousCloudProfileParentInsteadOfChoosingArbitrarily() {
        val parentOne = OrcaCloudProfile(
            id = "parent-1",
            name = "Shared parent",
            type = OrcaProfileType.PROCESS,
            contentJson = """{"wall_loops":"3"}""",
            updatedTime = 1L,
        )
        val parentTwo = parentOne.copy(id = "parent-2", contentJson = """{"wall_loops":"4"}""")
        val child = OrcaCloudProfile(
            id = "child",
            name = "Child",
            type = OrcaProfileType.PROCESS,
            contentJson = """{"inherits":"Shared parent"}""",
            updatedTime = 2L,
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            OrcaProfileSettingsResolver.resolve(
                profile = child,
                availableProfiles = listOf(parentOne, parentTwo, child),
                supportedKeys = setOf("wall_loops"),
            )
        }

        assertTrue(error.message.orEmpty().contains("inherits ambiguous preset"))
    }

    @Test
    fun writesTheExactCompleteUtf8IniConsumedByNativeSlice() {
        val config = OrcaDynamicPrintConfigBuilder.build(
            // Runtime defaults come from ConfigOption::serialize() and are already C-style
            // escaped. The builder must not turn this into `G28\\\\nG90`.
            runtimeDefaults = mapOf("machine_start_gcode" to "G28\\nG90"),
            supportedKeys = RequiredMachineKeys + "machine_start_gcode",
            profiles = OrcaSelectedProfiles(),
            machineFilament = scalarSettings(),
            liveProcessSettings = OrcaProcessSettingsPayload.from(emptyMap<String, Any?>()),
        )
        val output = temporaryFolder.newFile("orca-current.ini")

        config.writeTo(output)

        assertEquals(config.ini, output.readText(StandardCharsets.UTF_8))
        assertTrue(output.readText().contains("machine_start_gcode = G28\\nG90\n"))
        assertTrue(!output.readText().contains("machine_start_gcode = G28\\\\nG90\n"))
    }

    @Test
    fun completesRelativeExtrusionDefaultsForGenericPrinterButPreservesPresetResetGcode() {
        val supported = RequiredMachineKeys + setOf(
            "use_relative_e_distances",
            "before_layer_change_gcode",
        )
        val generic = OrcaDynamicPrintConfigBuilder.build(
            runtimeDefaults = mapOf(
                "use_relative_e_distances" to "1",
                "before_layer_change_gcode" to "",
            ),
            supportedKeys = supported,
            profiles = OrcaSelectedProfiles(),
            machineFilament = scalarSettings(),
            liveProcessSettings = OrcaProcessSettingsPayload.from(emptyMap<String, Any?>()),
        )
        val printerPreset = profile(
            OrcaProfileType.PRINTER,
            """{"before_layer_change_gcode":"M117 next layer\nG92 E0"}""",
        )
        val configured = OrcaDynamicPrintConfigBuilder.build(
            runtimeDefaults = mapOf(
                "use_relative_e_distances" to "1",
                "before_layer_change_gcode" to "",
            ),
            supportedKeys = supported,
            profiles = OrcaSelectedProfiles(printer = printerPreset),
            machineFilament = scalarSettings(),
            liveProcessSettings = OrcaProcessSettingsPayload.from(emptyMap<String, Any?>()),
        )

        assertEquals("G92 E0", generic.settings["before_layer_change_gcode"])
        assertEquals("M117 next layer\\nG92 E0", configured.settings["before_layer_change_gcode"])
    }

    @Test
    fun appendsRelativeExtrusionResetWithoutDeletingExistingBeforeLayerGcode() {
        val config = OrcaDynamicPrintConfigBuilder.build(
            runtimeDefaults = mapOf(
                "use_relative_e_distances" to "1",
                "before_layer_change_gcode" to "M117 next layer",
            ),
            supportedKeys = RequiredMachineKeys + setOf(
                "use_relative_e_distances",
                "before_layer_change_gcode",
            ),
            profiles = OrcaSelectedProfiles(),
            machineFilament = scalarSettings(),
            liveProcessSettings = OrcaProcessSettingsPayload.from(emptyMap<String, Any?>()),
        )

        assertEquals("M117 next layer\\nG92 E0", config.settings["before_layer_change_gcode"])
    }

    @Test
    fun usesAbsoluteExtrusionWhenPinnedEngineDoesNotExposeLayerGcode() {
        val config = OrcaDynamicPrintConfigBuilder.build(
            runtimeDefaults = mapOf("use_relative_e_distances" to "1"),
            supportedKeys = RequiredMachineKeys + "use_relative_e_distances",
            profiles = OrcaSelectedProfiles(),
            machineFilament = scalarSettings(),
            liveProcessSettings = OrcaProcessSettingsPayload.from(emptyMap<String, Any?>()),
        )

        assertEquals("0", config.settings["use_relative_e_distances"])
        assertFalse("before_layer_change_gcode" in config.settings)
    }

    private fun profile(type: OrcaProfileType, json: String): OrcaCloudProfile = OrcaCloudProfile(
        id = "$type-id",
        name = "$type profile",
        type = type,
        contentJson = json,
        updatedTime = 1L,
    )

    private fun scalarSettings(nozzleDiameter: String = "0.4") = OrcaMachineFilamentScalars(
        bedWidthMm = 220.0,
        bedDepthMm = 220.0,
        printableHeightMm = 250.0,
        nozzleDiameterMm = nozzleDiameter,
        filamentDiameterMm = "1.75",
        nozzleTemperatureC = "210",
        bedTemperatureC = "60",
        gcodeFlavor = "marlin",
    )

    companion object {
        private val RequiredMachineKeys = linkedSetOf(
            "printer_technology",
            "printable_area",
            "printable_height",
            "gcode_flavor",
            "nozzle_diameter",
            "filament_diameter",
            "nozzle_temperature",
            "nozzle_temperature_initial_layer",
            "curr_bed_type",
            "hot_plate_temp",
            "hot_plate_temp_initial_layer",
        )
    }
}
