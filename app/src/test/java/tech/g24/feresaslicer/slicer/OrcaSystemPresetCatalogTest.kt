// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.slicer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.g24.feresaslicer.auth.OrcaCloudProfile
import tech.g24.feresaslicer.auth.OrcaProfileType

class OrcaSystemPresetCatalogTest {
    @Test
    fun userProcessInheritsFlattenedVendorSystemPreset() {
        val catalog = OrcaSystemPresetCatalog.fromIniBundles(
            mapOf(
                "Generic.ini" to """
                    [vendor]
                    name = Generic

                    [print:0.20mm Standard]
                    perimeters = 2
                    top_solid_layers = 4
                    fill_density = 15%
                """.trimIndent(),
                "Kingroon.ini" to """
                    [vendor]
                    name = Kingroon

                    [print:Base quality]
                    perimeters = 3
                    top_solid_layers = 5

                    [print:0.20mm Standard]
                    inherits = Base quality
                    fill_density = 20%
                    start_gcode = G28\nG90
                """.trimIndent(),
            ),
        )
        val child = cloudProfile(
            name = "Dasha quality",
            json = """{"inherits":"0.20mm Standard @Kingroon KP3S 3.0","wall_loops":["6"]}""",
        )

        val augmented = catalog.augment(
            OrcaSelectedProfiles(
                process = child,
                availableCloudProfiles = listOf(child),
            ),
        )
        val systemAlias = augmented.availableCloudProfiles.single {
            it.name == "0.20mm Standard @Kingroon KP3S 3.0"
        }
        val settings = systemAlias.settingsMap(
            setOf(
                "wall_loops",
                "top_shell_layers",
                "sparse_infill_density",
                "machine_start_gcode",
            ),
        )

        assertEquals("3", settings["wall_loops"])
        assertEquals("5", settings["top_shell_layers"])
        assertEquals("20%", settings["sparse_infill_density"])
        assertEquals("G28\\nG90", settings["machine_start_gcode"])
    }

    @Test
    fun missingSystemParentIsNeverSilentlyIgnored() {
        val catalog = OrcaSystemPresetCatalog.fromIniBundles(emptyMap())
        val child = cloudProfile(
            name = "Dasha quality",
            json = """{"inherits":"Missing @Kingroon"}""",
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            catalog.augment(OrcaSelectedProfiles(process = child))
        }

        assertTrue(error.message.orEmpty().contains("Missing Orca system process parent"))
    }

    @Test
    fun systemPresetCyclesFailBeforeSlicing() {
        val catalog = OrcaSystemPresetCatalog.fromIniBundles(
            mapOf(
                "Loop.ini" to """
                    [vendor]
                    name = Loop
                    [print:A]
                    inherits = B
                    [print:B]
                    inherits = A
                """.trimIndent(),
            ),
        )
        val child = cloudProfile("Child", """{"inherits":"A"}""")

        val error = assertThrows(IllegalArgumentException::class.java) {
            catalog.augment(OrcaSelectedProfiles(process = child))
        }

        assertTrue(error.message.orEmpty().contains("Cyclic system preset inheritance"))
    }

    @Test
    fun indexedLookupPreservesPortableFallbackAndDuplicateDisambiguation() {
        val catalog = OrcaSystemPresetCatalog.fromIniBundles(
            mapOf(
                "Generic.ini" to """
                    [vendor]
                    name = Generic
                    [print:0.20mm Standard]
                    perimeters = 2
                """.trimIndent(),
                "Kingroon.ini" to """
                    [vendor]
                    name = Kingroon
                    [print:0.20mm Standard]
                    perimeters = 5
                """.trimIndent(),
            ),
        )

        val profile = catalog.bundledProfile(
            type = OrcaProfileType.PROCESS,
            name = "0.20mm Standard @Kingroon KP3S 3.0",
            contextHint = "Kingroon KP3S 3.0",
        )

        assertEquals(
            "5",
            profile.settingsMap(setOf("wall_loops"))["wall_loops"],
        )
        assertTrue(
            catalog.hasBundledProfile(
                type = OrcaProfileType.PROCESS,
                name = "0.20mm Standard @Kingroon KP3S 3.0",
                contextHint = "Kingroon KP3S 3.0",
            ),
        )
    }

    private fun cloudProfile(name: String, json: String) = OrcaCloudProfile(
        id = "cloud:$name",
        name = name,
        type = OrcaProfileType.PROCESS,
        contentJson = json,
        updatedTime = 1L,
    )
}
