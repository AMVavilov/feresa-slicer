// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.catalog

import android.content.Context
import org.json.JSONObject

data class OrcaSystemPrinterProfile(
    val name: String,
    val model: String,
    val family: String,
    val vendor: String,
    val nozzleDiameter: Double,
    val bedWidth: Double,
    val bedDepth: Double,
    val printableHeight: Double,
    val gcodeFlavor: String,
    val defaultPrintProfile: String,
)

data class OrcaSystemPrinterCatalog(
    val source: String = "",
    val sourceCommit: String = "",
    val printers: List<OrcaSystemPrinterProfile> = emptyList(),
    val vendorCount: Int = 0,
) {
    companion object {
        fun load(context: Context): OrcaSystemPrinterCatalog = runCatching {
            val root = context.assets.open("orca-printer-catalog.json")
                .bufferedReader()
                .use { JSONObject(it.readText()) }
            val vendors = root.optJSONArray("vendors")
            val printers = buildList {
                if (vendors != null) {
                    for (vendorIndex in 0 until vendors.length()) {
                        val vendor = vendors.optJSONObject(vendorIndex) ?: continue
                        val vendorName = vendor.optString("name")
                        val vendorPrinters = vendor.optJSONArray("printers") ?: continue
                        for (printerIndex in 0 until vendorPrinters.length()) {
                            val printer = vendorPrinters.optJSONObject(printerIndex) ?: continue
                            add(
                                OrcaSystemPrinterProfile(
                                    name = printer.optString("name"),
                                    model = printer.optString("model"),
                                    family = printer.optString("family"),
                                    vendor = vendorName,
                                    nozzleDiameter = printer.optDouble("nozzle", 0.4),
                                    bedWidth = printer.optDouble("bed_width", 220.0),
                                    bedDepth = printer.optDouble("bed_depth", 220.0),
                                    printableHeight = printer.optDouble("printable_height", 250.0),
                                    gcodeFlavor = printer.optString("gcode_flavor", "marlin"),
                                    defaultPrintProfile = printer.optString("default_print_profile"),
                                ),
                            )
                        }
                    }
                }
            }
            OrcaSystemPrinterCatalog(
                source = root.optString("source"),
                sourceCommit = root.optString("source_commit"),
                printers = printers,
                vendorCount = vendors?.length() ?: printers.map { it.vendor }.distinct().size,
            )
        }.getOrDefault(OrcaSystemPrinterCatalog())
    }
}
