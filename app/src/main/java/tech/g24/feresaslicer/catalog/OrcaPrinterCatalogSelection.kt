// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.catalog

import java.util.Locale

/** Pure catalog projections used by the compact, cascading printer picker. */
internal object OrcaPrinterCatalogSelection {
    fun matchingProfiles(
        catalog: OrcaSystemPrinterCatalog,
        query: String,
    ): List<OrcaSystemPrinterProfile> {
        val normalizedQuery = query.trim().lowercase(Locale.ROOT)
        if (normalizedQuery.isBlank()) return catalog.printers
        return catalog.printers.filter { profile ->
            listOf(profile.vendor, profile.model, profile.family, profile.name).any { value ->
                value.lowercase(Locale.ROOT).contains(normalizedQuery)
            }
        }
    }

    fun vendors(profiles: List<OrcaSystemPrinterProfile>): List<String> = profiles
        .map(OrcaSystemPrinterProfile::vendor)
        .filter(String::isNotBlank)
        .distinct()
        .sortedWith(String.CASE_INSENSITIVE_ORDER)

    fun models(
        profiles: List<OrcaSystemPrinterProfile>,
        vendor: String,
    ): List<String> = profiles
        .asSequence()
        .filter { it.vendor == vendor }
        .map { it.model.ifBlank { it.name } }
        .distinct()
        .sortedWith(String.CASE_INSENSITIVE_ORDER)
        .toList()

    fun profiles(
        profiles: List<OrcaSystemPrinterProfile>,
        vendor: String,
        model: String,
    ): List<OrcaSystemPrinterProfile> = profiles
        .filter { profile ->
            profile.vendor == vendor && profile.model.ifBlank { profile.name } == model
        }
        .sortedWith(
            compareBy<OrcaSystemPrinterProfile> { it.nozzleDiameter }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
        )
}
