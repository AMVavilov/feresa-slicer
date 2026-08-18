// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.catalog

import org.junit.Assert.assertEquals
import org.junit.Test

class OrcaSystemPrinterCatalogTest {
    @Test
    fun filterPrintersPreservesProvenanceAndRecalculatesVendorCount() {
        val catalog = OrcaSystemPrinterCatalog(
            source = "OrcaSlicer",
            sourceCommit = "abc123",
            printers = listOf(
                printer(name = "Supported A", vendor = "Vendor A"),
                printer(name = "Unsupported", vendor = "Vendor B"),
                printer(name = "Supported B", vendor = "Vendor A"),
            ),
            vendorCount = 2,
        )

        val filtered = catalog.filterPrinters { it.name.startsWith("Supported") }

        assertEquals("OrcaSlicer", filtered.source)
        assertEquals("abc123", filtered.sourceCommit)
        assertEquals(listOf("Supported A", "Supported B"), filtered.printers.map { it.name })
        assertEquals(1, filtered.vendorCount)
    }

    private fun printer(name: String, vendor: String) = OrcaSystemPrinterProfile(
        name = name,
        model = name,
        family = "Test",
        vendor = vendor,
        nozzleDiameter = 0.4,
        bedWidth = 220.0,
        bedDepth = 220.0,
        printableHeight = 250.0,
        gcodeFlavor = "marlin",
        defaultPrintProfile = "",
    )
}
