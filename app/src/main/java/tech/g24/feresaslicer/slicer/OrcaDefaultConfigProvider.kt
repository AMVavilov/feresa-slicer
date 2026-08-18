// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.slicer

import java.util.Collections
import java.util.TreeMap
import java.util.TreeSet
import ru.ytkab0bp.slicebeam.slic3r.ConfigOptionDef
import ru.ytkab0bp.slicebeam.slic3r.PrintConfigDef

/**
 * Reads Orca's own runtime option definitions instead of maintaining a second Kotlin default
 * table. Values are returned exactly as serialized by Orca's `ConfigOption::serialize()`.
 *
 * The bridge is pinned to CodeMasterCody3D/OrcaSlicer-Mobile commit
 * `6fc2e14b9a222301f4432cee26d7ab37d3be86d0`.
 */
object OrcaDefaultConfigProvider {
    /** All native-supported FFF/common keys, including options whose native default is null. */
    @JvmStatic
    fun fffOptionKeys(): Set<String> =
        selectFffOptionKeys(PrintConfigDef.getInstance().options)

    /**
     * Returns Orca FFF defaults with [overlay] applied last by exact wire key.
     *
     * Unknown and SLA-only overlay keys fail fast instead of being silently ignored by slicing.
     */
    @JvmStatic
    @JvmOverloads
    fun fffDefaults(overlay: Map<String, String> = emptyMap()): Map<String, String> =
        selectFffDefaults(PrintConfigDef.getInstance().options, overlay)

    internal fun selectFffOptionKeys(
        definitions: Map<String, ConfigOptionDef>,
    ): Set<String> {
        val keys = TreeSet<String>()
        definitions.forEach { (key, definition) ->
            require(key.isNotEmpty()) { "Orca returned an empty configuration key" }
            if (definition.supportsFff()) keys += key
        }
        return Collections.unmodifiableSet(keys)
    }

    internal fun selectFffDefaults(
        definitions: Map<String, ConfigOptionDef>,
        overlay: Map<String, String> = emptyMap(),
    ): Map<String, String> {
        val fffKeys = selectFffOptionKeys(definitions)
        val merged = TreeMap<String, String>()

        fffKeys.forEach { key ->
            if (key !in SkipNativeDefaults) {
                definitions.getValue(key).defaultValue?.let { merged[key] = it }
            }
        }

        overlay.forEach { (key, value) ->
            require(key in fffKeys) {
                val definition = definitions[key]
                if (definition == null) {
                    "Unknown Orca FFF configuration key '$key'"
                } else {
                    "Orca configuration key '$key' is not valid for FFF"
                }
            }
            merged[key] = value
        }

        return Collections.unmodifiableMap(merged)
    }

    // Orca marks many printer/process options (including layer_gcode) as UNKNOWN rather than FFF.
    // Its own mobile build includes those common options and excludes only explicit SLA options.
    private fun ConfigOptionDef.supportsFff(): Boolean =
        printerTechnology != ConfigOptionDef.PrinterTechnology.SLA

    // Mirrored from the pinned mobile port's PrintConfigDef.SKIP_DEFAULT_OPTIONS. These resin
    // motion defaults are not a valid synthetic preset even if a future native definition marks
    // one of them as common; explicit profiles may still provide them where appropriate.
    private val SkipNativeDefaults = setOf(
        "tilt_up_initial_speed",
        "tilt_up_finish_speed",
        "tilt_down_initial_speed",
        "tilt_down_finish_speed",
        "tower_speed",
    )
}
