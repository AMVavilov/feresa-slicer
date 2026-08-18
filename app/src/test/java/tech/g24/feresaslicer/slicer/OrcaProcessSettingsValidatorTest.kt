// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.slicer

import org.junit.Assert.assertThrows
import org.junit.Test
import ru.ytkab0bp.slicebeam.slic3r.ConfigOptionDef

class OrcaProcessSettingsValidatorTest {
    @Test
    fun acceptsValidNumbersPercentBooleansAndEnums() {
        OrcaProcessSettingsValidator.validateOrThrow(
            OrcaProcessSettingsPayload.from(
                mapOf(
                    "wall_loops" to "5",
                    "density" to "35%",
                    "support" to "1",
                    "pattern" to "gyroid",
                ),
            ),
            mapOf(
                "wall_loops" to definition(ConfigOptionDef.ConfigOptionType.INT, min = 1f, max = 20f),
                "density" to definition(ConfigOptionDef.ConfigOptionType.PERCENT, min = 0f, max = 100f),
                "support" to definition(ConfigOptionDef.ConfigOptionType.BOOL),
                "pattern" to definition(
                    ConfigOptionDef.ConfigOptionType.ENUM,
                    enumValues = arrayOf("grid", "gyroid"),
                ),
            ),
        )
    }

    @Test
    fun rejectsMalformedOutOfRangeAndUnsupportedValuesBeforeNativeSlice() {
        val definitions = mapOf(
            "wall_loops" to definition(ConfigOptionDef.ConfigOptionType.INT, min = 1f, max = 20f),
            "pattern" to definition(
                ConfigOptionDef.ConfigOptionType.ENUM,
                enumValues = arrayOf("grid", "gyroid"),
            ),
        )

        assertThrows(IllegalArgumentException::class.java) {
            OrcaProcessSettingsValidator.validateOrThrow(
                OrcaProcessSettingsPayload.from(mapOf("wall_loops" to "five")),
                definitions,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            OrcaProcessSettingsValidator.validateOrThrow(
                OrcaProcessSettingsPayload.from(mapOf("wall_loops" to "0")),
                definitions,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            OrcaProcessSettingsValidator.validateOrThrow(
                OrcaProcessSettingsPayload.from(mapOf("pattern" to "not-real")),
                definitions,
            )
        }
    }

    private fun definition(
        type: ConfigOptionDef.ConfigOptionType,
        min: Float = Float.MIN_VALUE,
        max: Float = Float.MAX_VALUE,
        enumValues: Array<String>? = null,
    ) = ConfigOptionDef().apply {
        this.type = type
        this.min = min
        this.max = max
        this.enumValues = enumValues
    }
}
