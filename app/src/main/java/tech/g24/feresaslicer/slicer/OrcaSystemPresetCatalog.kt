// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.slicer

import android.content.Context
import java.io.BufferedReader
import java.io.StringReader
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
import tech.g24.feresaslicer.auth.OrcaCloudProfile
import tech.g24.feresaslicer.auth.OrcaProfileType

internal data class OrcaSystemPreset(
    val sourceBundle: String,
    val vendor: String,
    val type: OrcaProfileType,
    val name: String,
    val settings: Map<String, String>,
    val inheritedNames: List<String>,
) {
    val identity: String = "$sourceBundle:${type.wireValue}:$name"
}

/**
 * Offline catalog for resolving OrcaCloud diffs whose `inherits` parent is a system preset.
 *
 * Integration root:
 * `OrcaSystemPresetCatalog.load(context).augment(selectedProfiles)` returns an
 * [OrcaSelectedProfiles] ready for [OrcaDynamicPrintConfigBuilder]. Missing or ambiguous parents
 * and cycles are errors; the resolver never substitutes runtime defaults silently.
 */
class OrcaSystemPresetCatalog private constructor(
    presets: List<OrcaSystemPreset>,
) {
    private val presets = presets.sortedWith(
        compareBy<OrcaSystemPreset>({ it.type.ordinal }, { it.name }, { it.sourceBundle }),
    )
    private val presetsByTypeAndName: Map<Pair<OrcaProfileType, String>, List<OrcaSystemPreset>> =
        this.presets.groupBy { preset -> preset.type to preset.name }

    fun augment(selected: OrcaSelectedProfiles): OrcaSelectedProfiles {
        val selectedList = listOfNotNull(selected.printer, selected.process, selected.filament)
        val cloudProfiles = (selected.availableCloudProfiles + selectedList).distinctBy(::profileIdentity)
        val aliases = linkedMapOf<String, OrcaCloudProfile>()
        val cloudByTypeAndName = cloudProfiles.groupBy { it.type to it.name }
        val printerHint = selected.printer?.name.orEmpty()

        fun verifyCloudChain(profile: OrcaCloudProfile, visiting: MutableSet<String>) {
            val identity = profileIdentity(profile)
            require(visiting.add(identity)) {
                "Cyclic OrcaCloud profile inheritance detected at '${profile.name}'"
            }
            try {
                val inheritedName = profile.inheritedProfileName() ?: return
                val cloudCandidates = cloudByTypeAndName[profile.type to inheritedName].orEmpty()
                require(cloudCandidates.size <= 1) {
                    "Ambiguous OrcaCloud ${profile.type.wireValue} parent '$inheritedName'"
                }
                val cloudParent = cloudCandidates.singleOrNull()
                if (cloudParent != null) {
                    verifyCloudChain(cloudParent, visiting)
                    return
                }

                val systemParent = resolveSystemPreset(
                    type = profile.type,
                    requestedName = inheritedName,
                    contextHint = "$inheritedName $printerHint",
                )
                val flattened = flattenSystemPreset(systemParent, linkedSetOf())
                val aliasKey = "${profile.type}:$inheritedName"
                aliases.putIfAbsent(aliasKey, flattened.toCloudAlias(inheritedName))
            } finally {
                visiting.remove(identity)
            }
        }

        selectedList.forEach { verifyCloudChain(it, linkedSetOf()) }
        return selected.copy(
            availableCloudProfiles = (cloudProfiles + aliases.values).distinctBy(::profileIdentity),
        )
    }

    /** Returns one complete bundled Orca preset for a catalog selection. */
    fun bundledProfile(
        type: OrcaProfileType,
        name: String,
        contextHint: String = name,
    ): OrcaCloudProfile {
        val preset = resolveSystemPreset(
            type = type,
            requestedName = name,
            contextHint = contextHint,
        )
        return flattenSystemPreset(preset, linkedSetOf()).toCloudAlias(name)
    }

    fun hasBundledProfile(
        type: OrcaProfileType,
        name: String,
        contextHint: String = name,
    ): Boolean = type != OrcaProfileType.OTHER && matchingSystemPresets(
        type = type,
        requestedName = name,
        contextHint = contextHint,
    ).size == 1

    private fun flattenSystemPreset(
        preset: OrcaSystemPreset,
        visiting: MutableSet<String>,
    ): OrcaSystemPreset {
        require(visiting.add(preset.identity)) {
            "Cyclic system preset inheritance detected at '${preset.name}' in ${preset.sourceBundle}"
        }
        try {
            val merged = linkedMapOf<String, String>()
            preset.inheritedNames.forEach { inheritedName ->
                val parent = resolveSystemPreset(
                    type = preset.type,
                    requestedName = inheritedName,
                    contextHint = "${preset.vendor} ${preset.sourceBundle}",
                    preferredBundle = preset.sourceBundle,
                )
                // Earlier parents win, matching the pinned mobile parser's multi-parent merge.
                flattenSystemPreset(parent, visiting).settings.forEach { (key, value) ->
                    merged.putIfAbsent(key, value)
                }
            }
            merged.putAll(preset.settings)
            return preset.copy(settings = merged.toMap(), inheritedNames = emptyList())
        } finally {
            visiting.remove(preset.identity)
        }
    }

    private fun resolveSystemPreset(
        type: OrcaProfileType,
        requestedName: String,
        contextHint: String,
        preferredBundle: String? = null,
    ): OrcaSystemPreset {
        require(type != OrcaProfileType.OTHER) {
            "Orca system presets cannot resolve profile type $type"
        }
        val candidates = matchingSystemPresets(
            type = type,
            requestedName = requestedName,
            contextHint = contextHint,
            preferredBundle = preferredBundle,
        )
        require(candidates.isNotEmpty()) {
            "Missing Orca system ${type.wireValue} parent '$requestedName'"
        }
        require(candidates.size == 1) {
            "Ambiguous Orca system ${type.wireValue} parent '$requestedName': " +
                candidates.joinToString { "${it.vendor}/${it.sourceBundle}" }
        }
        return candidates.single()
    }

    private fun matchingSystemPresets(
        type: OrcaProfileType,
        requestedName: String,
        contextHint: String,
        preferredBundle: String? = null,
    ): List<OrcaSystemPreset> {
        var candidates = presetsByTypeAndName[type to requestedName].orEmpty()
        if (candidates.isEmpty() && type != OrcaProfileType.PRINTER) {
            val portableBaseName = requestedName.substringBefore(" @").trim()
            candidates = presetsByTypeAndName[type to portableBaseName].orEmpty()
        }
        if (preferredBundle != null) {
            val sameBundle = candidates.filter { it.sourceBundle == preferredBundle }
            if (sameBundle.isNotEmpty()) candidates = sameBundle
        }
        if (candidates.size > 1) {
            val normalizedHint = normalizeForMatch(contextHint)
            val matched = candidates.filter { candidate ->
                val vendor = normalizeForMatch(candidate.vendor)
                val bundle = normalizeForMatch(candidate.sourceBundle.substringBeforeLast('.'))
                (vendor.isNotEmpty() && normalizedHint.contains(vendor)) ||
                    (bundle.isNotEmpty() && normalizedHint.contains(bundle))
            }
            if (matched.isNotEmpty()) candidates = matched
        }
        return candidates
    }

    private fun OrcaSystemPreset.toCloudAlias(requestedName: String): OrcaCloudProfile {
        val content = JSONObject()
            .put("name", requestedName)
            .put("type", type.wireValue)
        // JSONArray marks values as already serialized. OrcaCloud profile conversion keeps array
        // items verbatim, so existing `\\n` in native G-code defaults is not double-escaped.
        settings.toSortedMap().forEach { (key, value) ->
            content.put(key, JSONArray().put(value))
        }
        return OrcaCloudProfile(
            id = "system:${sourceBundle}:${type.wireValue}:$requestedName",
            name = requestedName,
            type = type,
            contentJson = content.toString(),
            updatedTime = 0L,
        )
    }

    private fun profileIdentity(profile: OrcaCloudProfile): String =
        "${profile.type}:${profile.id.ifBlank { profile.name }}"

    private fun normalizeForMatch(value: String): String = value
        .lowercase(Locale.ROOT)
        .filter(Char::isLetterOrDigit)

    companion object {
        const val AssetDirectory = "orca_profiles"
        const val ExpectedBundleCount = 64

        fun load(context: Context): OrcaSystemPresetCatalog {
            val assetNames = context.assets.list(AssetDirectory)
                .orEmpty()
                .filter { it.endsWith(".ini", ignoreCase = true) }
                .sorted()
            require(assetNames.size == ExpectedBundleCount) {
                "Incomplete Orca system preset bundle: expected $ExpectedBundleCount INI files, " +
                    "found ${assetNames.size}"
            }
            val parsed = assetNames.flatMap { name ->
                context.assets.open("$AssetDirectory/$name").bufferedReader().use { reader ->
                    parseBundle(name, reader)
                }
            }
            require(parsed.isNotEmpty()) { "Orca system preset bundle contains no usable profiles" }
            return OrcaSystemPresetCatalog(parsed)
        }

        internal fun fromIniBundles(bundles: Map<String, String>): OrcaSystemPresetCatalog =
            OrcaSystemPresetCatalog(
                bundles.toSortedMap().flatMap { (name, ini) ->
                    parseBundle(name, BufferedReader(StringReader(ini)))
                },
            )

        internal fun parseBundle(
            sourceBundle: String,
            reader: BufferedReader,
        ): List<OrcaSystemPreset> {
            var vendor = sourceBundle.substringBeforeLast('.')
            var sectionKind = ""
            var sectionName = ""
            var currentType: OrcaProfileType? = null
            var settings = linkedMapOf<String, String>()
            var inheritedNames = emptyList<String>()
            val result = mutableListOf<OrcaSystemPreset>()

            fun flushPreset() {
                val type = currentType ?: return
                result += OrcaSystemPreset(
                    sourceBundle = sourceBundle,
                    vendor = vendor,
                    type = type,
                    name = sectionName,
                    settings = settings.toMap(),
                    inheritedNames = inheritedNames,
                )
            }

            reader.forEachLine { rawLine ->
                val line = rawLine.trimEnd()
                if (line.startsWith('[') && line.endsWith(']')) {
                    flushPreset()
                    val section = line.substring(1, line.length - 1)
                    sectionKind = section.substringBefore(':')
                    sectionName = section.substringAfter(':', "")
                    currentType = when (sectionKind) {
                        "printer" -> OrcaProfileType.PRINTER
                        "filament" -> OrcaProfileType.FILAMENT
                        "print" -> OrcaProfileType.PROCESS
                        else -> null
                    }
                    settings = linkedMapOf()
                    inheritedNames = emptyList()
                    return@forEachLine
                }

                val separator = line.indexOf(" = ")
                if (separator < 0) return@forEachLine
                val rawKey = line.substring(0, separator)
                val value = line.substring(separator + 3).trim()
                if (sectionKind == "vendor" && rawKey == "name") {
                    vendor = value
                    return@forEachLine
                }
                if (currentType == null) return@forEachLine
                if (rawKey == "inherits") {
                    inheritedNames = value.split(';').map(String::trim).filter(String::isNotEmpty)
                    return@forEachLine
                }
                if (rawKey == "arc_fitting") return@forEachLine

                val key = OrcaLegacyOptionNames.migrate(rawKey)
                settings[key] = normalizeSerializedValue(key, value)
            }
            flushPreset()
            return result
        }

        private fun normalizeSerializedValue(key: String, serialized: String): String {
            var value = serialized
            if (key.endsWith("_gcode")) {
                value = value
                    .replace("[bed_temperature_initial_layer_single]", "{first_layer_bed_temperature[0]}")
                    .replace("[bed_temperature_initial_layer]", "{first_layer_bed_temperature[0]}")
                    .replace("[nozzle_temperature_initial_layer]", "{first_layer_temperature[0]}")
            }
            return value
        }
    }
}
