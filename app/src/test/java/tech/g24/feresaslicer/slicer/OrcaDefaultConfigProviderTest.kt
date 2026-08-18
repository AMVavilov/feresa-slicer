// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.slicer

import java.util.TreeMap
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.ytkab0bp.slicebeam.slic3r.ConfigOptionDef

class OrcaDefaultConfigProviderTest {
    @Test
    fun `selects serialized FFF shared and common defaults but excludes SLA`() {
        val definitions = linkedMapOf(
            "sla_key" to definition(ConfigOptionDef.PrinterTechnology.SLA, "sla"),
            "fff_without_default" to definition(ConfigOptionDef.PrinterTechnology.FFF, null),
            "shared_key" to definition(ConfigOptionDef.PrinterTechnology.ANY, "shared"),
            "unknown_technology" to definition(ConfigOptionDef.PrinterTechnology.UNKNOWN, "unknown"),
            "wall_loops" to definition(ConfigOptionDef.PrinterTechnology.FFF, "2"),
        )

        assertEquals(
            linkedMapOf(
                "shared_key" to "shared",
                "unknown_technology" to "unknown",
                "wall_loops" to "2",
            ),
            OrcaDefaultConfigProvider.selectFffDefaults(definitions),
        )

        assertEquals(
            sortedSetOf(
                "fff_without_default",
                "shared_key",
                "unknown_technology",
                "wall_loops",
            ),
            OrcaDefaultConfigProvider.selectFffOptionKeys(definitions),
        )
    }

    @Test
    fun `does not synthesize mobile skip-list defaults`() {
        val definitions = linkedMapOf(
            "wall_loops" to definition(ConfigOptionDef.PrinterTechnology.FFF, "2"),
            "tower_speed" to definition(ConfigOptionDef.PrinterTechnology.UNKNOWN, "10"),
        )

        val defaults = OrcaDefaultConfigProvider.selectFffDefaults(definitions)
        val keys = OrcaDefaultConfigProvider.selectFffOptionKeys(definitions)

        assertEquals(mapOf("wall_loops" to "2"), defaults)
        assertTrue("tower_speed" in keys)
    }

    @Test
    fun `overlay wins and may supply a null-default FFF option`() {
        val definitions = linkedMapOf(
            "wall_loops" to definition(ConfigOptionDef.PrinterTechnology.FFF, "2"),
            "support_material" to definition(ConfigOptionDef.PrinterTechnology.FFF, "0"),
            "machine_start_gcode" to definition(ConfigOptionDef.PrinterTechnology.FFF, null),
        )

        val result = OrcaDefaultConfigProvider.selectFffDefaults(
            definitions,
            linkedMapOf(
                "wall_loops" to "5",
                "support_material" to "1",
                "machine_start_gcode" to "G28\nG29",
            ),
        )

        assertEquals("5", result["wall_loops"])
        assertEquals("1", result["support_material"])
        assertEquals("G28\nG29", result["machine_start_gcode"])
        assertEquals(result.keys.sorted(), result.keys.toList())
    }

    @Test
    fun `unknown and non-FFF overlay keys fail instead of disappearing`() {
        val definitions = linkedMapOf(
            "wall_loops" to definition(ConfigOptionDef.PrinterTechnology.FFF, "2"),
            "sla_key" to definition(ConfigOptionDef.PrinterTechnology.SLA, "sla"),
        )

        val unknown = assertThrows(IllegalArgumentException::class.java) {
            OrcaDefaultConfigProvider.selectFffDefaults(
                definitions,
                mapOf("typo_wall_loops" to "5"),
            )
        }
        assertTrue(unknown.message.orEmpty().contains("Unknown Orca FFF"))

        val wrongTechnology = assertThrows(IllegalArgumentException::class.java) {
            OrcaDefaultConfigProvider.selectFffDefaults(
                definitions,
                mapOf("sla_key" to "value"),
            )
        }
        assertTrue(wrongTechnology.message.orEmpty().contains("not valid for FFF"))
    }

    @Test
    fun `returned defaults and keys are immutable snapshots`() {
        val definitions = TreeMap<String, ConfigOptionDef>().apply {
            put("wall_loops", definition(ConfigOptionDef.PrinterTechnology.FFF, "2"))
        }
        val defaults = OrcaDefaultConfigProvider.selectFffDefaults(definitions)
        val keys = OrcaDefaultConfigProvider.selectFffOptionKeys(definitions)

        definitions["wall_loops"]?.defaultValue = "9"
        definitions["later"] = definition(ConfigOptionDef.PrinterTechnology.FFF, "1")

        assertEquals(mapOf("wall_loops" to "2"), defaults)
        assertFalse("later" in keys)
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (defaults as MutableMap<String, String>)["wall_loops"] = "8"
        }
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (keys as MutableSet<String>).add("later")
        }
    }

    @Test
    fun `native serialized defaults are written without double escaping`() {
        val settings = linkedMapOf(
            "machine_start_gcode" to "G28\\nM190 S[first_layer_bed_temperature]",
            "filename_format" to "folder\\\"quoted\"\\{name}.gcode",
            "actual_newline_overlay" to "before\nafter",
        )

        val ini = OrcaProcessSettingsIni.encodeSerializedValues(settings)

        assertEquals(
            "actual_newline_overlay = before\\nafter\n" +
                "filename_format = folder\\\"quoted\"\\{name}.gcode\n" +
                "machine_start_gcode = G28\\nM190 S[first_layer_bed_temperature]\n",
            ini,
        )
        assertFalse(ini.contains("G28\\\\n"))
        assertEquals(
            ini,
            String(
                OrcaProcessSettingsIni.encodeSerializedValuesUtf8(settings),
                StandardCharsets.UTF_8,
            ),
        )
    }

    private fun definition(
        technology: ConfigOptionDef.PrinterTechnology,
        defaultValue: String?,
    ) = ConfigOptionDef().apply {
        printerTechnology = technology
        this.defaultValue = defaultValue
    }
}
