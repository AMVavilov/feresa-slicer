// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class OrcaCloudProfileSettingsTest {
    @Test
    fun convertsCompleteJsonArraysAndFiltersOnlyByRuntimeSupportedKeys() {
        val profile = OrcaCloudProfile(
            id = "printer-1",
            name = "Array printer",
            type = OrcaProfileType.PRINTER,
            contentJson = """
                {
                  "name":"Array printer",
                  "type":"machine",
                  "inherits":"System base",
                  "print_host":"192.0.2.4",
                  "nozzle_diameter":["0.4","0.6"],
                  "printable_height":"[220,250]",
                  "machine_start_gcode":"G28\nG90",
                  "future_orca_option":"kept",
                  "unsupported_setting":"discarded"
                }
            """.trimIndent(),
            updatedTime = 1L,
        )

        val settings = profile.settingsMap(
            setOf("nozzle_diameter", "printable_height", "machine_start_gcode", "future_orca_option"),
        )

        assertEquals(
            linkedMapOf(
                "future_orca_option" to "kept",
                "machine_start_gcode" to "G28\\nG90",
                "nozzle_diameter" to "0.4,0.6",
                "printable_height" to "220,250",
            ),
            settings,
        )
        assertFalse("name" in settings)
        assertFalse("type" in settings)
        assertFalse("inherits" in settings)
        assertFalse("print_host" in settings)
        assertFalse("unsupported_setting" in settings)
    }

    @Test
    fun rejectsNestedValuesForARealEngineOption() {
        val profile = OrcaCloudProfile(
            id = "broken",
            name = "Broken profile",
            type = OrcaProfileType.PROCESS,
            contentJson = """{"wall_loops":{"value":5}}""",
            updatedTime = 1L,
        )

        assertThrows(IllegalArgumentException::class.java) {
            profile.settingsMap(setOf("wall_loops"))
        }
    }
}
