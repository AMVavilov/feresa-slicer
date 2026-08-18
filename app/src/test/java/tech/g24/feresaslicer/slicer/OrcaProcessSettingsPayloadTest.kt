// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.slicer

import java.nio.charset.StandardCharsets
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OrcaProcessSettingsPayloadTest {
    @Test
    fun canonicalJsonSortsKeysAndPreservesWireValues() {
        val payload = OrcaProcessSettingsPayload.builder()
            .put("wall_loops", 5)
            .put("sparse_infill_density", "20%")
            .put("enable_support", true)
            .put("filename_format", "{input_filename_base}_\"draft\".gcode")
            .put("optional_setting", null)
            .build()

        val golden = """{"enable_support":true,"filename_format":"{input_filename_base}_\"draft\".gcode","optional_setting":null,"sparse_infill_density":"20%","wall_loops":5}"""
        assertEquals(golden, payload.toCanonicalJson())
        assertArrayEquals(golden.toByteArray(StandardCharsets.UTF_8), payload.toUtf8Payload())
    }

    @Test
    fun parsesCanonicalPayloadWithoutRenamingKeysOrStringValues() {
        val original = OrcaProcessSettingsPayload.from(
            linkedMapOf(
                "layer_height" to "0.20",
                "infill_direction" to "45,135",
                "sparse_infill_pattern" to "gyroid",
                "gcode_comments" to false,
                "default_acceleration" to 1000L,
                "bridge_flow" to 1.25,
                "пользовательский_ключ" to "значение\nсо второй строкой",
                "unset" to null,
            ),
        )

        val parsed = OrcaProcessSettingsPayload.parse(original.toCanonicalJson())

        assertEquals(original, parsed)
        assertEquals(original.toCanonicalJson(), parsed.toCanonicalJson())
        assertEquals("0.20", parsed["layer_height"])
        assertEquals("45,135", parsed["infill_direction"])
        assertEquals("gyroid", parsed["sparse_infill_pattern"])
        assertEquals(false, parsed["gcode_comments"])
        assertEquals(1000, (parsed["default_acceleration"] as Number).toInt())
        assertEquals(1.25, (parsed["bridge_flow"] as Number).toDouble(), 0.0)
        assertEquals("значение\nсо второй строкой", parsed["пользовательский_ключ"])
        assertTrue("unset" in parsed)
        assertNull(parsed["unset"])
    }

    @Test
    fun acceptsCompleteCurrentOrcaProcessNamespaceWithoutAWhitelist() {
        val currentKeys = listOf(
            "layer_height", "initial_layer_print_height", "line_width",
            "initial_layer_line_width", "outer_wall_line_width", "seam_position", "seam_gap",
            "staggered_inner_seams", "resolution", "enable_arc_fitting", "precise_outer_wall",
            "xy_hole_compensation", "elefant_foot_compensation", "wall_generator",
            "wall_sequence", "is_infill_first", "only_one_wall_top", "detect_overhang_wall",
            "bridge_flow", "wall_loops", "alternate_extra_wall", "detect_thin_wall",
            "top_shell_layers", "bottom_shell_layers", "top_surface_pattern",
            "bottom_surface_pattern", "sparse_infill_density", "sparse_infill_pattern",
            "infill_direction", "infill_wall_overlap", "enable_support", "support_type",
            "support_threshold_angle", "support_on_build_plate_only", "raft_layers",
            "support_top_z_distance", "support_bottom_z_distance",
            "support_interface_top_layers", "support_interface_bottom_layers",
            "support_interface_spacing", "tree_support_tip_diameter",
            "tree_support_branch_distance", "tree_support_branch_angle", "enable_prime_tower",
            "prime_tower_width", "prime_volume", "prime_tower_brim_width", "flush_into_infill",
            "flush_into_support", "ooze_prevention", "standby_temperature_delta",
            "outer_wall_filament_id", "sparse_infill_filament_id", "skirt_loops",
            "skirt_distance", "min_skirt_length", "brim_type", "brim_width", "brim_object_gap",
            "outer_wall_speed", "inner_wall_speed", "sparse_infill_speed", "travel_speed",
            "default_acceleration", "slicing_mode", "print_sequence", "spiral_mode",
            "fuzzy_skin", "gcode_comments", "gcode_label_objects", "exclude_object",
            "filename_format",
        )
        val values = currentKeys.withIndex().associateTo(linkedMapOf()) { (index, key) ->
            key to "value-$index"
        }

        val parsed = OrcaProcessSettingsPayload.parse(
            OrcaProcessSettingsPayload.from(values).toCanonicalJson(),
        )

        assertEquals(currentKeys.size, parsed.size)
        assertEquals(currentKeys.toSet(), parsed.keys)
        currentKeys.forEachIndexed { index, key -> assertEquals("value-$index", parsed[key]) }
    }

    @Test
    fun snapshotsInputAndBuilderState() {
        val mutable = linkedMapOf<String, Any?>("wall_loops" to 2)
        val payload = OrcaProcessSettingsPayload.from(mutable)
        mutable["wall_loops"] = 99

        val builder = OrcaProcessSettingsPayload.builder().put("layer_height", "0.20")
        val first = builder.build()
        builder.put("layer_height", "0.30")

        assertEquals(2, payload["wall_loops"])
        assertEquals("0.20", first["layer_height"])
        assertFalse(first.asMap() === first.asMap())
    }

    @Test
    fun rejectsInvalidKeysAndNonPrimitiveValues() {
        assertThrows(IllegalArgumentException::class.java) {
            OrcaProcessSettingsPayload.from(mapOf(" " to "value"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            OrcaProcessSettingsPayload.from(mapOf("bad\nkey" to "value"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            OrcaProcessSettingsPayload.from(mapOf("nested" to mapOf("value" to 1)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            OrcaProcessSettingsPayload.from(mapOf("array" to listOf("value")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            OrcaProcessSettingsPayload.from(mapOf("speed" to Double.NaN))
        }
        assertThrows(IllegalArgumentException::class.java) {
            OrcaProcessSettingsPayload.from(mapOf("speed" to Float.POSITIVE_INFINITY))
        }
    }

    @Test
    fun parserRejectsNonObjectNestedAndTrailingJson() {
        assertThrows(IllegalArgumentException::class.java) {
            OrcaProcessSettingsPayload.parse("[]")
        }
        assertThrows(IllegalArgumentException::class.java) {
            OrcaProcessSettingsPayload.parse("{\"nested\":{\"value\":1}}")
        }
        assertThrows(IllegalArgumentException::class.java) {
            OrcaProcessSettingsPayload.parse("{\"layer_height\":\"0.2\"} trailing")
        }
    }
}
