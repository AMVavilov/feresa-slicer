// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.catalog

import org.junit.Assert.assertEquals
import org.junit.Test

class OrcaPrinterCatalogSelectionTest {
    private val catalog = OrcaSystemPrinterCatalog(
        printers = listOf(
            profile("Kingroon", "KP3S 3.0", 0.6),
            profile("Kingroon", "KP3S 3.0", 0.4),
            profile("Kingroon", "KLP1", 0.4),
            profile("Anker", "M5", 0.4),
        ),
    )

    @Test
    fun `catalog is projected into stable cascading options`() {
        val matches = OrcaPrinterCatalogSelection.matchingProfiles(catalog, "")

        assertEquals(listOf("Anker", "Kingroon"), OrcaPrinterCatalogSelection.vendors(matches))
        assertEquals(
            listOf("KLP1", "KP3S 3.0"),
            OrcaPrinterCatalogSelection.models(matches, "Kingroon"),
        )
        assertEquals(
            listOf(0.4, 0.6),
            OrcaPrinterCatalogSelection.profiles(matches, "Kingroon", "KP3S 3.0")
                .map { it.nozzleDiameter },
        )
    }

    @Test
    fun `query searches vendor model family and full profile name`() {
        assertEquals(
            listOf("KP3S 3.0", "KP3S 3.0"),
            OrcaPrinterCatalogSelection.matchingProfiles(catalog, "kp3s").map { it.model },
        )
        assertEquals(
            listOf("M5"),
            OrcaPrinterCatalogSelection.matchingProfiles(catalog, "anker").map { it.model },
        )
        assertEquals(emptyList<OrcaSystemPrinterProfile>(), OrcaPrinterCatalogSelection.matchingProfiles(catalog, "missing"))
    }

    private fun profile(vendor: String, model: String, nozzle: Double) = OrcaSystemPrinterProfile(
        name = "$model ${nozzle} nozzle",
        model = model,
        family = model.substringBefore(' '),
        vendor = vendor,
        nozzleDiameter = nozzle,
        bedWidth = 220.0,
        bedDepth = 220.0,
        printableHeight = 250.0,
        gcodeFlavor = "marlin",
        defaultPrintProfile = "0.20mm Standard",
    )
}
