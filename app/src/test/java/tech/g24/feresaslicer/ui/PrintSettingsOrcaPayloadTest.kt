// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.ui

import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.g24.feresaslicer.slicer.OrcaProcessSettingsIni
import tech.g24.feresaslicer.slicer.OrcaProcessSettingsPayload

class PrintSettingsOrcaPayloadTest {
    @Test
    fun mapsEveryCurrentControlToItsOrcaWireFormat() {
        val payload = PrintSettingsState(
            wallLoops = " 5 ",
            topShellLayers = "6",
            bottomShellLayers = "4",
            infillDensity = "35%",
            infillWallOverlap = "18",
            seamGap = "7",
            enableSupport = true,
            supportType = "tree(auto)",
            supportOnBuildPlateOnly = true,
            printSpeed = "999",
            outerWallSpeed = "35",
            innerWallSpeed = "60",
            infillSpeed = "90",
            travelSpeed = "180",
            acceleration = "1200",
            spiralMode = true,
            fuzzySkin = "allwalls",
            gcodeComments = false,
        ).toOrcaProcessSettingsPayload()

        assertEquals(72, payload.size)
        assertEquals("5", payload["wall_loops"])
        assertEquals("6", payload["top_shell_layers"])
        assertEquals("4", payload["bottom_shell_layers"])
        assertEquals("35%", payload["sparse_infill_density"])
        assertEquals("18%", payload["infill_wall_overlap"])
        assertEquals("7%", payload["seam_gap"])
        assertEquals("1", payload["enable_support"])
        assertEquals("tree(auto)", payload["support_type"])
        assertEquals("1", payload["support_on_build_plate_only"])
        assertEquals("35", payload["outer_wall_speed"])
        assertEquals("60", payload["inner_wall_speed"])
        assertEquals("90", payload["sparse_infill_speed"])
        assertEquals("180", payload["travel_speed"])
        assertEquals("1200", payload["default_acceleration"])
        assertEquals("1", payload["spiral_mode"])
        assertEquals("allwalls", payload["fuzzy_skin"])
        assertEquals("0", payload["gcode_comments"])
        assertFalse("print_speed" in payload)
    }

    @Test
    fun completeUiPayloadHasStableJsonRoundTrip() {
        val original = PrintSettingsState(
            filenameFormat = "{input_filename_base}_тест.gcode",
            enablePrimeTower = true,
            flushIntoInfill = true,
        ).toOrcaProcessSettingsPayload()

        val parsed = OrcaProcessSettingsPayload.parse(original.toCanonicalJson())

        assertEquals(original, parsed)
        assertEquals(original.keys, parsed.keys)
        assertEquals("1", parsed["enable_prime_tower"])
        assertEquals("1", parsed["flush_into_infill"])
    }

    @Test
    fun hydratesVisibleControlsFromResolvedProfileValues() {
        val hydrated = PrintSettingsState().applyOrcaSettings(
            mapOf(
                "layer_height" to "0.16",
                "wall_loops" to "5",
                "top_shell_layers" to "7",
                "sparse_infill_density" to "35%",
                "enable_support" to "1",
                "support_type" to "tree(manual)",
                "fuzzy_skin" to "external",
            ),
        )

        assertEquals("0.16", hydrated.layerHeight)
        assertEquals("5", hydrated.wallLoops)
        assertEquals("7", hydrated.topShellLayers)
        assertEquals("35", hydrated.infillDensity)
        assertTrue(hydrated.enableSupport)
        assertEquals("tree(auto)", hydrated.supportType)
        assertEquals("external", hydrated.fuzzySkin)
    }

    @Test
    fun iniOverlayIsSortedEscapedAndOverridesBaseProfile() {
        val process = PrintSettingsState(
            wallLoops = "5",
            topShellLayers = "7",
            bottomShellLayers = "6",
            enableSupport = true,
            outerWallSpeed = "38",
            filenameFormat = "folder\\\"quoted\"\n{name}.gcode",
        ).toOrcaProcessSettingsPayload()
        val base = linkedMapOf<String, Any?>(
            "z_custom_base" to "preserved",
            "wall_loops" to "2",
            "enable_support" to false,
            "a_machine_value" to 42,
        )

        val ini = OrcaProcessSettingsIni.encode(process, base)

        assertTrue(ini.startsWith("a_machine_value = 42\n"))
        assertTrue(ini.contains("wall_loops = 5\n"))
        assertTrue(ini.contains("top_shell_layers = 7\n"))
        assertTrue(ini.contains("bottom_shell_layers = 6\n"))
        assertTrue(ini.contains("enable_support = 1\n"))
        assertTrue(ini.contains("outer_wall_speed = 38\n"))
        assertTrue(ini.contains("filename_format = folder\\\\\\\"quoted\\\"\\n{name}.gcode\n"))
        assertTrue(ini.endsWith("z_custom_base = preserved\n"))
        assertEquals(ini, OrcaProcessSettingsIni.encode(process, base.toMap()))
        assertEquals(ini, String(OrcaProcessSettingsIni.encodeUtf8(process, base), StandardCharsets.UTF_8))
    }

    @Test
    fun nullProcessValueRemovesInheritedIniKey() {
        val process = OrcaProcessSettingsPayload.from(
            mapOf("inherits" to null, "wall_loops" to "5"),
        )

        val ini = OrcaProcessSettingsIni.encode(
            process,
            mapOf("inherits" to "base process", "layer_height" to "0.2"),
        )

        assertFalse(ini.contains("inherits"))
        assertEquals("layer_height = 0.2\nwall_loops = 5\n", ini)
    }

    @Test
    fun iniConverterRejectsKeysAndValuesBoostIniCannotRepresentSafely() {
        assertThrows(IllegalArgumentException::class.java) {
            OrcaProcessSettingsIni.encode(
                OrcaProcessSettingsPayload.from(mapOf("bad-key" to "value")),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            OrcaProcessSettingsIni.encode(
                OrcaProcessSettingsPayload.from(mapOf("valid_key" to "bad\u0001value")),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            OrcaProcessSettingsIni.encode(
                OrcaProcessSettingsPayload.from(emptyMap<String, Any?>()),
                mapOf("nested" to listOf("value")),
            )
        }
    }
}
