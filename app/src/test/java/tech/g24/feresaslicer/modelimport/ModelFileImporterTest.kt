// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.modelimport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import tech.g24.feresaslicer.slicer.StlPlateComposer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ModelFileImporterTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `reports extensions accepted by the file picker`() {
        assertEquals(setOf("stl", "obj", "3mf"), ModelFileImporter.acceptedExtensions)
        assertTrue(ModelFileImporter.supports("gear.STL"))
        assertTrue(ModelFileImporter.supports("project.3Mf"))
        assertTrue(!ModelFileImporter.supports("toolpath.gcode"))
    }

    @Test
    fun `normalizes ASCII STL into validated binary STL`() {
        val source = temporaryFolder.newFile("offset.stl").apply {
            writeText(
                """
                solid offset
                  facet normal 0 0 1
                    outer loop
                      vertex 10 20 5
                      vertex 14 20 5
                      vertex 10 22 7
                    endloop
                  endfacet
                endsolid offset
                """.trimIndent(),
            )
        }
        val output = temporaryFolder.root.resolve("converted.stl")

        val imported = ModelFileImporter.convertToBinaryStl(source, output)

        assertEquals(ModelSourceFormat.STL, imported.sourceFormat)
        assertEquals(source.length(), imported.originalSizeBytes)
        assertEquals("offset.stl", imported.displayName)
        assertEquals(1L, imported.triangleCount)
        assertEquals(84L + 50L, output.length())
        assertEquals(-2.0, imported.bounds.minimumX, 0.0001)
        assertEquals(2.0, imported.bounds.maximumX, 0.0001)
        assertEquals(-1.0, imported.bounds.minimumY, 0.0001)
        assertEquals(1.0, imported.bounds.maximumY, 0.0001)
        assertEquals(0.0, imported.bounds.minimumZ, 0.0001)
        assertEquals(2.0, imported.bounds.maximumZ, 0.0001)
        assertEquals(1L, StlPlateComposer.inspect(output).triangleCount)
    }

    @Test
    fun `triangulates an OBJ polygon with negative indices`() {
        val obj = """
            # A four-sided face intentionally uses negative OBJ indices.
            v 10 20 3
            v 14 20 3
            v 14 22 5
            v 10 22 5
            f -4/1 -3/2 -2/3 -1/4
        """.trimIndent().encodeToByteArray()
        val output = temporaryFolder.root.resolve("quad.stl")

        val imported = ModelFileImporter.convertToBinaryStl(
            input = ByteArrayInputStream(obj),
            originalFileName = "quad.obj",
            destination = output,
            knownSizeBytes = obj.size.toLong(),
        )

        assertEquals(ModelSourceFormat.OBJ, imported.sourceFormat)
        assertEquals(obj.size.toLong(), imported.originalSizeBytes)
        assertEquals(2L, imported.triangleCount)
        assertEquals(-2.0, imported.bounds.minimumX, 0.0001)
        assertEquals(2.0, imported.bounds.maximumX, 0.0001)
        assertEquals(-1.0, imported.bounds.minimumY, 0.0001)
        assertEquals(1.0, imported.bounds.maximumY, 0.0001)
        assertEquals(0.0, imported.bounds.minimumZ, 0.0001)
        assertEquals(2.0, imported.bounds.maximumZ, 0.0001)
    }

    @Test
    fun `sniffs OBJ when provider supplies no display name and records actual byte size`() {
        val obj = """
            v 0 0 0
            v 2 0 0
            v 0 1 0
            f 1 2 3
        """.trimIndent().encodeToByteArray()

        val imported = ModelFileImporter.convertToBinaryStl(
            input = ByteArrayInputStream(obj),
            originalFileName = "",
            destination = temporaryFolder.root.resolve("sniffed.stl"),
            knownSizeBytes = 1L,
        )

        assertEquals(ModelSourceFormat.OBJ, imported.sourceFormat)
        assertEquals("model.obj", imported.displayName)
        assertEquals(obj.size.toLong(), imported.originalSizeBytes)
        assertEquals(1L, imported.triangleCount)
    }

    @Test
    fun `rejects content that does not match the claimed extension`() {
        val obj = """
            v 0 0 0
            v 1 0 0
            v 0 1 0
            f 1 2 3
        """.trimIndent().encodeToByteArray()

        val error = runCatching {
            ModelFileImporter.convertToBinaryStl(
                ByteArrayInputStream(obj),
                "spoofed.stl",
                temporaryFolder.root.resolve("spoofed-output.stl"),
            )
        }.exceptionOrNull() as ModelImportException

        assertEquals(ModelImportError.FORMAT_MISMATCH, error.error)
    }

    @Test
    fun `loads 3MF components build transforms and model units`() {
        val modelXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <model unit="centimeter" xmlns="http://schemas.microsoft.com/3dmanufacturing/core/2015/02">
              <resources>
                <object id="1" type="model">
                  <mesh>
                    <vertices>
                      <vertex x="0" y="0" z="0"/>
                      <vertex x="2" y="0" z="0"/>
                      <vertex x="0" y="1" z="1"/>
                    </vertices>
                    <triangles><triangle v1="0" v2="1" v3="2"/></triangles>
                  </mesh>
                </object>
                <object id="2" type="model">
                  <components>
                    <component objectid="1" transform="0 1 0 -1 0 0 0 0 1 5 6 2"/>
                  </components>
                </object>
              </resources>
              <build><item objectid="2"/></build>
            </model>
        """.trimIndent()
        val archive = threeMfArchive(modelXml)
        val output = temporaryFolder.root.resolve("project.stl")

        val imported = ModelFileImporter.convertToBinaryStl(
            input = ByteArrayInputStream(archive),
            originalFileName = "project.3mf",
            destination = output,
            knownSizeBytes = archive.size.toLong(),
        )

        assertEquals(ModelSourceFormat.THREE_MF, imported.sourceFormat)
        assertEquals(1L, imported.triangleCount)
        assertEquals(-5.0, imported.bounds.minimumX, 0.0001)
        assertEquals(5.0, imported.bounds.maximumX, 0.0001)
        assertEquals(-10.0, imported.bounds.minimumY, 0.0001)
        assertEquals(10.0, imported.bounds.maximumY, 0.0001)
        assertEquals(0.0, imported.bounds.minimumZ, 0.0001)
        assertEquals(10.0, imported.bounds.maximumZ, 0.0001)
        assertEquals(1L, StlPlateComposer.inspect(output).triangleCount)
    }

    @Test
    fun `uses OPC root relationship instead of first model zip entry`() {
        val decoy = simpleThreeMf(width = 1)
        val selected = simpleThreeMf(width = 7)
        val relationships = """
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rel0"
                Type="http://schemas.microsoft.com/3dmanufacturing/2013/01/3dmodel"
                Target="/3D/selected.model"/>
            </Relationships>
        """.trimIndent()
        val archive = zipArchive(
            "3D/decoy.model" to decoy.encodeToByteArray(),
            "_rels/.rels" to relationships.encodeToByteArray(),
            "3D/selected.model" to selected.encodeToByteArray(),
        )

        val imported = ModelFileImporter.convertToBinaryStl(
            ByteArrayInputStream(archive),
            "rooted.3mf",
            temporaryFolder.root.resolve("rooted.stl"),
        )

        assertEquals(7.0, imported.bounds.width, 0.0001)
        assertEquals(1L, imported.triangleCount)
    }

    @Test
    fun `rejects ambiguous model parts when OPC root relationship is absent`() {
        val archive = zipArchive(
            "3D/first.model" to simpleThreeMf(width = 1).encodeToByteArray(),
            "3D/second.model" to simpleThreeMf(width = 2).encodeToByteArray(),
        )

        val error = runCatching {
            ModelFileImporter.convertToBinaryStl(
                ByteArrayInputStream(archive),
                "ambiguous.3mf",
                temporaryFolder.root.resolve("ambiguous.stl"),
            )
        }.exceptionOrNull() as ModelImportException

        assertEquals(ModelImportError.MALFORMED_MODEL, error.error)
    }

    @Test
    fun `rejects traversal in OPC model relationship`() {
        val relationships = """
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rel0"
                Type="http://schemas.microsoft.com/3dmanufacturing/2013/01/3dmodel"
                Target="../outside.model"/>
            </Relationships>
        """.trimIndent()
        val archive = zipArchive(
            "_rels/.rels" to relationships.encodeToByteArray(),
            "3D/model.model" to simpleThreeMf(width = 1).encodeToByteArray(),
        )

        val error = runCatching {
            ModelFileImporter.convertToBinaryStl(
                ByteArrayInputStream(archive),
                "traversal.3mf",
                temporaryFolder.root.resolve("traversal.stl"),
            )
        }.exceptionOrNull() as ModelImportException

        assertEquals(ModelImportError.MALFORMED_MODEL, error.error)
    }

    @Test
    fun `rejects DOCTYPE and internal entities before SAX expansion`() {
        val dangerous = """
            <?xml version="1.0"?>
            <!DOCTYPE model [<!ENTITY repeated "1234567890">]>
            <model xmlns="http://schemas.microsoft.com/3dmanufacturing/core/2015/02">
              <resources><object id="&repeated;"><mesh/></object></resources>
            </model>
        """.trimIndent()
        val archive = zipArchive("3D/model.model" to dangerous.encodeToByteArray())

        val error = runCatching {
            ModelFileImporter.convertToBinaryStl(
                ByteArrayInputStream(archive),
                "entity.3mf",
                temporaryFolder.root.resolve("entity.stl"),
            )
        }.exceptionOrNull() as ModelImportException

        assertEquals(ModelImportError.MALFORMED_MODEL, error.error)
    }

    @Test
    fun `bounds one oversized OBJ face before triangulation`() {
        val obj = buildString {
            appendLine("v 0 0 0")
            appendLine("v 1 0 0")
            appendLine("v 0 1 0")
            append("f")
            repeat(16_385) { append(" 1") }
        }.encodeToByteArray()

        val error = runCatching {
            ModelFileImporter.convertToBinaryStl(
                ByteArrayInputStream(obj),
                "oversized.obj",
                temporaryFolder.root.resolve("oversized.stl"),
            )
        }.exceptionOrNull() as ModelImportException

        assertEquals(ModelImportError.MODEL_TOO_LARGE, error.error)
    }

    @Test
    fun `bounds the number of 3MF build items`() {
        val model = buildString {
            append("""<model xmlns="http://schemas.microsoft.com/3dmanufacturing/core/2015/02"><resources>""")
            append("""<object id="1"><mesh><vertices>""")
            append("""<vertex x="0" y="0" z="0"/><vertex x="1" y="0" z="0"/><vertex x="0" y="1" z="0"/>""")
            append("""</vertices><triangles><triangle v1="0" v2="1" v3="2"/></triangles></mesh></object>""")
            append("</resources><build>")
            repeat(10_001) { append("<item objectid=\"1\"/>") }
            append("</build></model>")
        }
        val archive = zipArchive("3D/model.model" to model.encodeToByteArray())

        val error = runCatching {
            ModelFileImporter.convertToBinaryStl(
                ByteArrayInputStream(archive),
                "too-many-items.3mf",
                temporaryFolder.root.resolve("too-many-items.stl"),
            )
        }.exceptionOrNull() as ModelImportException

        assertEquals(ModelImportError.MODEL_TOO_LARGE, error.error)
    }

    @Test
    fun `unsupported input has a user-safe structured error`() {
        val error = runCatching {
            ModelFileImporter.convertToBinaryStl(
                ByteArrayInputStream(byteArrayOf(1, 2, 3)),
                "secret-path/toolpath.gcode",
                temporaryFolder.root.resolve("unused.stl"),
            )
        }.exceptionOrNull() as ModelImportException

        assertEquals(ModelImportError.UNSUPPORTED_FORMAT, error.error)
        assertTrue(error.message.orEmpty().contains("STL, OBJ или 3MF"))
        assertTrue(!error.message.orEmpty().contains("secret-path"))
    }

    @Test
    fun `invalid 3MF index is rejected without leaking parser details to UI`() {
        val invalid = threeMfArchive(
            """
            <model xmlns="http://schemas.microsoft.com/3dmanufacturing/core/2015/02">
              <resources><object id="1"><mesh>
                <vertices>
                  <vertex x="0" y="0" z="0"/>
                  <vertex x="1" y="0" z="0"/>
                  <vertex x="0" y="1" z="0"/>
                </vertices>
                <triangles><triangle v1="0" v2="1" v3="99"/></triangles>
              </mesh></object></resources>
              <build><item objectid="1"/></build>
            </model>
            """.trimIndent(),
        )

        val error = runCatching {
            ModelFileImporter.convertToBinaryStl(
                ByteArrayInputStream(invalid),
                "invalid.3mf",
                temporaryFolder.root.resolve("invalid.stl"),
            )
        }.exceptionOrNull() as ModelImportException

        assertEquals(ModelImportError.MALFORMED_MODEL, error.error)
        assertEquals("Файл повреждён или содержит неподдерживаемую геометрию.", error.message)
    }

    private fun simpleThreeMf(width: Int): String = """
        <model xmlns="http://schemas.microsoft.com/3dmanufacturing/core/2015/02">
          <resources><object id="1"><mesh>
            <vertices>
              <vertex x="0" y="0" z="0"/>
              <vertex x="$width" y="0" z="0"/>
              <vertex x="0" y="1" z="0"/>
            </vertices>
            <triangles><triangle v1="0" v2="1" v3="2"/></triangles>
          </mesh></object></resources>
          <build><item objectid="1"/></build>
        </model>
    """.trimIndent()

    private fun threeMfArchive(modelXml: String): ByteArray = zipArchive(
        "[Content_Types].xml" to "<Types/>".encodeToByteArray(),
        "3D/3dmodel.model" to modelXml.encodeToByteArray(),
    )

    private fun zipArchive(vararg entries: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().use { bytes ->
        ZipOutputStream(bytes).use { zip ->
            entries.forEach { (name, contents) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(contents)
                zip.closeEntry()
            }
        }
        bytes.toByteArray()
    }
}
