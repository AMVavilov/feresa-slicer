// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.modelimport

import org.xml.sax.Attributes
import org.xml.sax.EntityResolver
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.SAXNotRecognizedException
import org.xml.sax.SAXNotSupportedException
import org.xml.sax.XMLReader
import org.xml.sax.helpers.DefaultHandler
import tech.g24.feresaslicer.slicer.StlMeshBounds
import tech.g24.feresaslicer.slicer.StlPlateComposer
import tech.g24.feresaslicer.slicer.StlPlatePlacement
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.net.URI
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.xml.parsers.SAXParserFactory
import kotlin.math.sqrt

enum class ModelSourceFormat(val extension: String) {
    STL("stl"),
    OBJ("obj"),
    THREE_MF("3mf"),
}

enum class ModelImportError {
    UNSUPPORTED_FORMAT,
    FORMAT_MISMATCH,
    EMPTY_MODEL,
    MALFORMED_MODEL,
    MODEL_TOO_LARGE,
    CANNOT_WRITE_OUTPUT,
}

/** A message safe to show in the UI; parser details remain available through [cause]. */
class ModelImportException(
    val error: ModelImportError,
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

/**
 * Result of importing any supported model. [binaryStlFile] is normalized around the XY origin and
 * rests on Z=0, so it can be passed directly to the existing plate workspace and native slicer.
 */
data class ImportedModel(
    val binaryStlFile: File,
    val displayName: String,
    val sourceFormat: ModelSourceFormat,
    /** Number of source-container bytes actually read and validated by the importer. */
    val originalSizeBytes: Long?,
    val triangleCount: Long,
    val bounds: StlMeshBounds,
)

/** Android-compatible, offline model conversion into the slicer's binary-STL interchange format. */
object ModelFileImporter {
    val acceptedExtensions: Set<String> = ModelSourceFormat.entries
        .mapTo(linkedSetOf()) { it.extension }

    fun supports(fileName: String): Boolean = extensionOf(fileName) in acceptedExtensions

    fun convertToBinaryStl(
        source: File,
        destination: File,
        displayName: String = source.name,
    ): ImportedModel {
        if (!source.isFile) {
            throw ModelImportException(
                ModelImportError.MALFORMED_MODEL,
                "Не удалось прочитать выбранную модель.",
            )
        }
        source.inputStream().use { input ->
            return convertToBinaryStl(input, displayName, destination, source.length())
        }
    }

    fun convertToBinaryStl(
        input: InputStream,
        originalFileName: String,
        destination: File,
        knownSizeBytes: Long? = null,
    ): ImportedModel {
        require(knownSizeBytes == null || knownSizeBytes >= 0L) {
            "knownSizeBytes must not be negative"
        }
        val claimedFormat = declaredFormat(originalFileName)
        val parent = destination.absoluteFile.parentFile
            ?: throw ModelImportException(
                ModelImportError.CANNOT_WRITE_OUTPUT,
                "Не удалось подготовить место для импортированной модели.",
            )
        if (!parent.exists() && !parent.mkdirs()) {
            throw ModelImportException(
                ModelImportError.CANNOT_WRITE_OUTPUT,
                "Не удалось подготовить место для импортированной модели.",
            )
        }
        val temporary = try {
            File.createTempFile("feresa-model-", ".stl.part", parent)
        } catch (cause: Exception) {
            throw ModelImportException(
                ModelImportError.CANNOT_WRITE_OUTPUT,
                "Не удалось создать файл импортированной модели.",
                cause,
            )
        }
        val sourceCopy = try {
            File.createTempFile("feresa-source-", ".model", parent)
        } catch (cause: Exception) {
            temporary.delete()
            throw ModelImportException(
                ModelImportError.CANNOT_WRITE_OUTPUT,
                "Не удалось подготовить выбранную модель.",
                cause,
            )
        }

        try {
            sourceCopy.outputStream().use { output ->
                copyWithLimit(input, output, MaximumSourceBytes)
            }
            val detectedFormat = detectModelFormat(sourceCopy)
            if (claimedFormat != null && claimedFormat != detectedFormat) {
                throw ModelImportException(
                    ModelImportError.FORMAT_MISMATCH,
                    "Содержимое файла не соответствует его расширению.",
                )
            }
            val safeDisplayName = normalizedDisplayName(originalFileName, detectedFormat)
            val info = when (detectedFormat) {
                ModelSourceFormat.STL -> convertStl(sourceCopy, temporary)
                ModelSourceFormat.OBJ -> sourceCopy.inputStream().use { source ->
                    writeNormalizedBinaryStl(parseObj(source), temporary)
                }
                ModelSourceFormat.THREE_MF -> writeNormalizedBinaryStl(parseThreeMf(sourceCopy), temporary)
            }
            replaceAtomically(temporary, destination)
            return ImportedModel(
                binaryStlFile = destination,
                displayName = safeDisplayName,
                sourceFormat = detectedFormat,
                originalSizeBytes = sourceCopy.length(),
                triangleCount = info.triangleCount,
                bounds = info.bounds,
            )
        } catch (error: ModelImportException) {
            throw error
        } catch (error: ModelTooLargeException) {
            throw ModelImportException(
                ModelImportError.MODEL_TOO_LARGE,
                "Модель слишком большая для обработки на этом устройстве.",
                error,
            )
        } catch (error: Exception) {
            if (error.hasCause<ModelTooLargeException>()) {
                throw ModelImportException(
                    ModelImportError.MODEL_TOO_LARGE,
                    "Модель слишком большая для обработки на этом устройстве.",
                    error,
                )
            }
            throw ModelImportException(
                ModelImportError.MALFORMED_MODEL,
                "Файл повреждён или содержит неподдерживаемую геометрию.",
                error,
            )
        } finally {
            temporary.delete()
            File(temporary.absolutePath + ".tmp").delete()
            sourceCopy.delete()
        }
    }

    private fun declaredFormat(fileName: String): ModelSourceFormat? {
        val extension = extensionOf(fileName)
        if (extension.isBlank()) return null
        return ModelSourceFormat.entries.firstOrNull { it.extension == extension }
            ?: throw ModelImportException(
                ModelImportError.UNSUPPORTED_FORMAT,
                "Формат файла не поддерживается. Выберите STL, OBJ или 3MF.",
            )
    }

    private fun convertStl(source: File, output: File): MeshInfo {
        val binaryCount = exactBinaryTriangleCount(source)
        if (binaryCount == null) {
            validateAsciiStl(source)
        } else if (binaryCount > MaximumStlTriangles) {
            throw ModelTooLargeException()
        }
        val sourceInfo = StlPlateComposer.inspect(source)
        if (sourceInfo.triangleCount > MaximumStlTriangles) throw ModelTooLargeException()
        val composed = StlPlateComposer.compose(
            placements = listOf(
                StlPlatePlacement(
                    file = source,
                    positionXmm = 0.0,
                    positionYmm = 0.0,
                ),
            ),
            output = output,
        )
        return MeshInfo(composed.triangleCount, composed.bounds)
    }

    private fun replaceAtomically(source: File, destination: File) {
        try {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (error: Exception) {
            throw ModelImportException(
                ModelImportError.CANNOT_WRITE_OUTPUT,
                "Не удалось сохранить импортированную модель.",
                error,
            )
        }
    }
}

private const val MaximumSourceBytes = 256L * 1024L * 1024L
private const val MaximumThreeMfXmlBytes = 32L * 1024L * 1024L
private const val MaximumRelationshipsXmlBytes = 1024L * 1024L
private const val MaximumTextLineBytes = 256 * 1024
private const val MaximumObjFaceVertices = 16_384
private const val MaximumVertices = 1_000_000
private const val MaximumObjTriangles = 750_000L
private const val MaximumThreeMfSourceTriangles = 750_000L
private const val MaximumThreeMfExpandedTriangles = 350_000L
private const val MaximumStlTriangles = 2_000_000L
private const val MaximumThreeMfObjects = 10_000
private const val MaximumThreeMfComponents = 50_000
private const val MaximumThreeMfBuildItems = 10_000
private const val MaximumZipEntries = 2_048
private const val MaximumRootRelationships = 256
private const val MaximumComponentDepth = 128
private const val ThreeMfCoreNamespace = "http://schemas.microsoft.com/3dmanufacturing/core/2015/02"

private fun extensionOf(fileName: String): String = fileName
    .substringAfterLast('/')
    .substringAfterLast('\\')
    .trim()
    .substringAfterLast('.', "")
    .lowercase(Locale.US)

private fun normalizedDisplayName(fileName: String, format: ModelSourceFormat): String {
    val leaf = fileName.substringAfterLast('/').substringAfterLast('\\').trim()
    if (leaf.isBlank()) return "model.${format.extension}"
    return if (extensionOf(leaf).isBlank()) "$leaf.${format.extension}" else leaf
}

private data class MeshInfo(val triangleCount: Long, val bounds: StlMeshBounds)
private class ModelTooLargeException : IllegalArgumentException()

private inline fun <reified T : Throwable> Throwable.hasCause(): Boolean {
    var current: Throwable? = this
    val seen = hashSetOf<Throwable>()
    while (current != null && seen.add(current)) {
        if (current is T) return true
        current = current.cause
    }
    return false
}

private fun detectModelFormat(file: File): ModelSourceFormat {
    if (hasZipMagic(file)) return ModelSourceFormat.THREE_MF
    if (isExactBinaryStl(file)) return ModelSourceFormat.STL

    var detected: ModelSourceFormat? = null
    file.inputStream().use { input ->
        forEachBoundedUtf8Line(input) { rawLine ->
            val line = rawLine.removePrefix("\uFEFF").substringBefore('#').trim()
            if (line.isEmpty()) return@forEachBoundedUtf8Line true
            val token = line.substringBefore(' ').substringBefore('\t').lowercase(Locale.US)
            detected = when (token) {
                "solid", "facet", "outer", "vertex", "endloop", "endfacet", "endsolid" ->
                    ModelSourceFormat.STL
                "v", "f", "o", "g", "vn", "vt", "vp", "mtllib", "usemtl", "s" ->
                    ModelSourceFormat.OBJ
                else -> null
            }
            detected == null
        }
    }
    return detected ?: throw ModelImportException(
        ModelImportError.MALFORMED_MODEL,
        "Не удалось определить формат выбранной модели.",
    )
}

private fun hasZipMagic(file: File): Boolean {
    if (file.length() < 4L) return false
    val prefix = ByteArray(4)
    FileInputStream(file).use { input ->
        if (input.read(prefix) != prefix.size) return false
    }
    return prefix[0] == 0x50.toByte() && prefix[1] == 0x4b.toByte() &&
        (prefix[2] == 0x03.toByte() || prefix[2] == 0x05.toByte() || prefix[2] == 0x07.toByte()) &&
        (prefix[3] == 0x04.toByte() || prefix[3] == 0x06.toByte() || prefix[3] == 0x08.toByte())
}

private fun exactBinaryTriangleCount(file: File): Long? {
    if (file.length() < 84L) return null
    val prefix = ByteArray(84)
    DataInputStream(BufferedInputStream(FileInputStream(file))).use { input -> input.readFully(prefix) }
    val count = ByteBuffer.wrap(prefix, 80, 4).order(ByteOrder.LITTLE_ENDIAN)
        .int.toLong() and 0xffff_ffffL
    if (count > (Long.MAX_VALUE - 84L) / 50L) return null
    return count.takeIf { 84L + it * 50L == file.length() }
}

private fun isExactBinaryStl(file: File): Boolean = exactBinaryTriangleCount(file) != null

private fun validateAsciiStl(file: File) {
    var insideSolid = false
    var insideFacet = false
    var insideLoop = false
    var verticesInFacet = 0
    var triangleCount = 0L
    var sawSolid = false
    var sawEndSolid = false

    file.inputStream().use { input ->
        forEachBoundedUtf8Line(input) { rawLine ->
            val line = rawLine.removePrefix("\uFEFF").trim()
            if (line.isEmpty()) return@forEachBoundedUtf8Line true
            val parts = line.split(WhitespaceRegex)
            when (parts.first().lowercase(Locale.US)) {
                "solid" -> {
                    require(!insideSolid && !insideFacet && !insideLoop) { "Invalid ASCII STL solid" }
                    insideSolid = true
                    sawSolid = true
                }
                "facet" -> {
                    require(insideSolid && !insideFacet && parts.size >= 5 &&
                        parts[1].equals("normal", ignoreCase = true)
                    ) { "Invalid ASCII STL facet" }
                    parts.subList(2, 5).forEach(::requireFiniteTextNumber)
                    insideFacet = true
                    verticesInFacet = 0
                }
                "outer" -> {
                    require(insideFacet && !insideLoop && parts.size == 2 &&
                        parts[1].equals("loop", ignoreCase = true)
                    ) { "Invalid ASCII STL loop" }
                    insideLoop = true
                }
                "vertex" -> {
                    require(insideFacet && insideLoop && parts.size >= 4 && verticesInFacet < 3) {
                        "Invalid ASCII STL vertex"
                    }
                    parts.subList(1, 4).forEach(::requireFiniteTextNumber)
                    verticesInFacet += 1
                }
                "endloop" -> {
                    require(insideFacet && insideLoop && verticesInFacet == 3) {
                        "Invalid ASCII STL endloop"
                    }
                    insideLoop = false
                }
                "endfacet" -> {
                    require(insideFacet && !insideLoop && verticesInFacet == 3) {
                        "Invalid ASCII STL endfacet"
                    }
                    insideFacet = false
                    triangleCount += 1
                    if (triangleCount > MaximumStlTriangles) throw ModelTooLargeException()
                }
                "endsolid" -> {
                    require(insideSolid && !insideFacet && !insideLoop) { "Invalid ASCII STL endsolid" }
                    insideSolid = false
                    sawEndSolid = true
                }
                else -> error("Unsupported ASCII STL statement")
            }
            true
        }
    }
    require(sawSolid && sawEndSolid && !insideSolid && !insideFacet && !insideLoop) {
        "Incomplete ASCII STL"
    }
    if (triangleCount == 0L) {
        throw ModelImportException(ModelImportError.EMPTY_MODEL, "В модели нет треугольников для печати.")
    }
}

private fun requireFiniteTextNumber(serialized: String) {
    val number = serialized.toDoubleOrNull()?.takeIf(Double::isFinite)
        ?: error("Invalid finite model coordinate")
    require(number.toFloat().isFinite()) { "Model coordinate exceeds STL range" }
}

private fun forEachBoundedUtf8Line(input: InputStream, consumer: (String) -> Boolean) {
    val line = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    fun emit(): Boolean {
        val bytes = line.toByteArray()
        line.reset()
        return consumer(bytes.toString(Charsets.UTF_8))
    }
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        for (index in 0 until count) {
            when (val byte = buffer[index].toInt() and 0xff) {
                '\n'.code -> if (!emit()) return
                '\r'.code -> Unit
                else -> {
                    if (line.size() >= MaximumTextLineBytes) throw ModelTooLargeException()
                    line.write(byte)
                }
            }
        }
    }
    if (line.size() > 0) emit()
}

private data class MeshVertex(val x: Double, val y: Double, val z: Double) {
    init {
        require(x.isFinite() && y.isFinite() && z.isFinite())
        require(x.toFloat().isFinite() && y.toFloat().isFinite() && z.toFloat().isFinite())
    }
}

private data class MeshTriangle(
    val first: MeshVertex,
    val second: MeshVertex,
    val third: MeshVertex,
) {
    fun vertices(): List<MeshVertex> = listOf(first, second, third)
}

private fun parseObj(input: InputStream): List<MeshTriangle> {
    val vertices = ArrayList<MeshVertex>()
    val triangles = ArrayList<MeshTriangle>()
    forEachBoundedUtf8Line(input) { rawLine ->
        val line = rawLine.removePrefix("\uFEFF").substringBefore('#').trim()
        if (line.isNotEmpty()) {
            val parts = line.split(WhitespaceRegex)
            when (parts.first()) {
                "v" -> {
                    require(parts.size >= 4) { "Invalid OBJ vertex" }
                    if (vertices.size >= MaximumVertices) throw ModelTooLargeException()
                    vertices += MeshVertex(
                        parts[1].toDoubleOrNull() ?: error("Invalid OBJ vertex"),
                        parts[2].toDoubleOrNull() ?: error("Invalid OBJ vertex"),
                        parts[3].toDoubleOrNull() ?: error("Invalid OBJ vertex"),
                    )
                }
                "f" -> {
                    require(parts.size >= 4) { "Invalid OBJ face" }
                    if (parts.size - 1 > MaximumObjFaceVertices) throw ModelTooLargeException()
                    val indices = parts.subList(1, parts.size).map { token ->
                        val serialized = token.substringBefore('/')
                        val raw = serialized.toIntOrNull() ?: error("Invalid OBJ face index")
                        require(raw != 0) { "OBJ indices are one-based" }
                        val resolved = if (raw > 0) raw - 1 else vertices.size + raw
                        require(resolved in vertices.indices) { "OBJ face index out of range" }
                        resolved
                    }
                    for (index in 1 until indices.lastIndex) {
                        if (triangles.size.toLong() >= MaximumObjTriangles) throw ModelTooLargeException()
                        triangles += MeshTriangle(
                            vertices[indices[0]],
                            vertices[indices[index]],
                            vertices[indices[index + 1]],
                        )
                    }
                }
            }
        }
        true
    }
    require(triangles.isNotEmpty()) { "OBJ contains no faces" }
    return triangles
}

private val WhitespaceRegex = Regex("\\s+")

private data class ThreeMfObject(
    val id: String,
    val vertices: MutableList<MeshVertex> = mutableListOf(),
    val triangles: MutableList<IntArray> = mutableListOf(),
    val components: MutableList<ThreeMfComponent> = mutableListOf(),
)

private data class ThreeMfComponent(val objectId: String, val transform: AffineTransform)
private data class ThreeMfBuildItem(val objectId: String, val transform: AffineTransform)

private data class ThreeMfDocument(
    val unitScaleMm: Double,
    val objects: Map<String, ThreeMfObject>,
    val buildItems: List<ThreeMfBuildItem>,
) {
    fun flatten(): List<MeshTriangle> {
        val output = ArrayList<MeshTriangle>()
        val roots = if (buildItems.isNotEmpty()) {
            buildItems
        } else {
            val referenced = objects.values.flatMap { it.components }.mapTo(hashSetOf()) { it.objectId }
            objects.keys.filterNot(referenced::contains).map { ThreeMfBuildItem(it, AffineTransform.Identity) }
        }
        roots.forEach { item -> appendObject(item.objectId, item.transform, linkedSetOf(), output) }
        require(output.isNotEmpty()) { "3MF contains no printable triangles" }
        return output
    }

    private fun appendObject(
        objectId: String,
        transform: AffineTransform,
        recursionStack: MutableSet<String>,
        output: MutableList<MeshTriangle>,
    ) {
        if (recursionStack.size >= MaximumComponentDepth) throw ModelTooLargeException()
        require(recursionStack.add(objectId)) { "3MF component cycle" }
        val modelObject = objects[objectId] ?: error("3MF references an unknown object")
        modelObject.triangles.forEach { indices ->
            if (output.size.toLong() >= MaximumThreeMfExpandedTriangles) throw ModelTooLargeException()
            val transformed = indices.map { index ->
                val point = modelObject.vertices.getOrNull(index)
                    ?: error("3MF triangle index out of range")
                transform.apply(point).scaled(unitScaleMm)
            }
            output += MeshTriangle(transformed[0], transformed[1], transformed[2])
        }
        modelObject.components.forEach { component ->
            appendObject(
                objectId = component.objectId,
                transform = transform.after(component.transform),
                recursionStack = recursionStack,
                output = output,
            )
        }
        recursionStack.remove(objectId)
    }
}

private data class AffineTransform(
    val a: Double,
    val b: Double,
    val c: Double,
    val tx: Double,
    val d: Double,
    val e: Double,
    val f: Double,
    val ty: Double,
    val g: Double,
    val h: Double,
    val i: Double,
    val tz: Double,
) {
    fun apply(point: MeshVertex): MeshVertex = MeshVertex(
        a * point.x + b * point.y + c * point.z + tx,
        d * point.x + e * point.y + f * point.z + ty,
        g * point.x + h * point.y + i * point.z + tz,
    )

    /** Apply [inner] first, then this transform. */
    fun after(inner: AffineTransform): AffineTransform = AffineTransform(
        a = a * inner.a + b * inner.d + c * inner.g,
        b = a * inner.b + b * inner.e + c * inner.h,
        c = a * inner.c + b * inner.f + c * inner.i,
        tx = a * inner.tx + b * inner.ty + c * inner.tz + tx,
        d = d * inner.a + e * inner.d + f * inner.g,
        e = d * inner.b + e * inner.e + f * inner.h,
        f = d * inner.c + e * inner.f + f * inner.i,
        ty = d * inner.tx + e * inner.ty + f * inner.tz + ty,
        g = g * inner.a + h * inner.d + i * inner.g,
        h = g * inner.b + h * inner.e + i * inner.h,
        i = g * inner.c + h * inner.f + i * inner.i,
        tz = g * inner.tx + h * inner.ty + i * inner.tz + tz,
    )

    companion object {
        val Identity = AffineTransform(
            1.0, 0.0, 0.0, 0.0,
            0.0, 1.0, 0.0, 0.0,
            0.0, 0.0, 1.0, 0.0,
        )

        fun parse(serialized: String?): AffineTransform {
            if (serialized.isNullOrBlank()) return Identity
            if (serialized.length > 1024) throw ModelTooLargeException()
            val values = serialized.trim().split(WhitespaceRegex).map { value ->
                value.toDoubleOrNull()?.takeIf(Double::isFinite)
                    ?: error("Invalid 3MF transform")
            }
            require(values.size == 12) { "A 3MF transform must contain 12 values" }
            // 3MF serializes a row-vector 4x3 matrix; adapt it to column-vector application.
            return AffineTransform(
                a = values[0], b = values[3], c = values[6], tx = values[9],
                d = values[1], e = values[4], f = values[7], ty = values[10],
                g = values[2], h = values[5], i = values[8], tz = values[11],
            )
        }
    }
}

private fun MeshVertex.scaled(multiplier: Double): MeshVertex = MeshVertex(
    x * multiplier,
    y * multiplier,
    z * multiplier,
)

private fun parseThreeMf(file: File): List<MeshTriangle> = SafeThreeMfPackage(file).use { archive ->
    val modelEntry = archive.rootModelEntry()
    archive.openBounded(modelEntry, MaximumThreeMfXmlBytes).use(::rejectXmlDoctype)
    archive.openBounded(modelEntry, MaximumThreeMfXmlBytes).use { input ->
        parseThreeMfModel(input).flatten()
    }
}

private class SafeThreeMfPackage(file: File) : Closeable {
    private val archive = ZipFile(file)
    private val entries: Map<String, ZipEntry>

    init {
        entries = try {
            val indexed = linkedMapOf<String, ZipEntry>()
            val iterator = archive.entries()
            var entryCount = 0
            while (iterator.hasMoreElements()) {
                val entry = iterator.nextElement()
                entryCount += 1
                if (entryCount > MaximumZipEntries) throw ModelTooLargeException()
                if (entry.isDirectory) continue
                val normalized = normalizePackageEntry(entry.name)
                require(indexed.put(normalized, entry) == null) { "Duplicate 3MF package entry" }
            }
            indexed
        } catch (error: Exception) {
            archive.close()
            throw error
        }
    }

    fun rootModelEntry(): ZipEntry {
        val relationships = entries["_rels/.rels"]
        if (relationships == null) {
            val models = entries.filterKeys { it.lowercase(Locale.US).endsWith(".model") }.values
            require(models.size == 1) { "3MF package has no unambiguous root model" }
            return models.single()
        }

        openBounded(relationships, MaximumRelationshipsXmlBytes).use(::rejectXmlDoctype)
        val modelRelationships = openBounded(relationships, MaximumRelationshipsXmlBytes).use { input ->
            parseRootRelationships(input)
        }.filter { relationship ->
            relationship.type.lowercase(Locale.US).endsWith("/3dmodel")
        }
        require(modelRelationships.size == 1) { "3MF package must declare exactly one root model" }
        val relationship = modelRelationships.single()
        require(!relationship.external) { "External 3MF model relationships are not allowed" }
        val target = normalizeRelationshipTarget(relationship.target)
        require(target.lowercase(Locale.US).endsWith(".model")) { "3MF root is not a model part" }
        return entries[target] ?: error("3MF root model part is missing")
    }

    fun openBounded(entry: ZipEntry, maximumBytes: Long): InputStream {
        if (entry.size > maximumBytes) throw ModelTooLargeException()
        return LimitedInputStream(archive.getInputStream(entry), maximumBytes)
    }

    override fun close() = archive.close()
}

private data class PackageRelationship(
    val type: String,
    val target: String,
    val external: Boolean,
)

private fun parseRootRelationships(input: InputStream): List<PackageRelationship> {
    val handler = object : DefaultHandler() {
        val relationships = mutableListOf<PackageRelationship>()

        override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
            val name = (localName?.ifBlank { null } ?: qName.orEmpty()).substringAfter(':')
            if (!name.equals("Relationship", ignoreCase = true)) return
            if (relationships.size >= MaximumRootRelationships) throw ModelTooLargeException()
            val type = attributes.value("Type")?.takeIf(String::isNotBlank)
                ?: error("3MF relationship type is missing")
            val target = attributes.value("Target")?.takeIf(String::isNotBlank)
                ?: error("3MF relationship target is missing")
            if (type.length > 1024 || target.length > 4096) throw ModelTooLargeException()
            relationships += PackageRelationship(
                type = type,
                target = target,
                external = attributes.value("TargetMode").equals("External", ignoreCase = true),
            )
        }
    }
    secureXmlReader(handler).parse(InputSource(input))
    return handler.relationships
}

private fun normalizePackageEntry(name: String): String {
    require(name.isNotBlank() && !name.startsWith('/') && '\\' !in name) {
        "Unsafe 3MF package entry"
    }
    val segments = name.split('/')
    require(segments.all { it.isNotBlank() && it != "." && it != ".." }) {
        "Unsafe 3MF package entry"
    }
    return segments.joinToString("/")
}

private fun normalizeRelationshipTarget(target: String): String {
    require('\\' !in target) { "Unsafe 3MF relationship target" }
    val uri = URI(target)
    require(!uri.isAbsolute && uri.authority == null && uri.query == null && uri.fragment == null) {
        "Unsafe 3MF relationship target"
    }
    val path = uri.path?.removePrefix("/").orEmpty()
    val segments = path.split('/')
    require(segments.isNotEmpty() && segments.all { it.isNotBlank() && it != "." && it != ".." }) {
        "Unsafe 3MF relationship target"
    }
    return segments.joinToString("/")
}

private fun parseThreeMfModel(input: InputStream): ThreeMfDocument {
    val handler = ThreeMfHandler()
    secureXmlReader(handler).parse(InputSource(input))
    return handler.document()
}

private fun secureXmlReader(handler: DefaultHandler): XMLReader {
    val factory = SAXParserFactory.newInstance().apply { isNamespaceAware = true }
    val reader = factory.newSAXParser().xmlReader
    // Every caller has already rejected the complete DTD-bearing XML stream. Disable entity
    // features where implemented as an additional layer; Android Expat does not expose both.
    listOf(
        "http://xml.org/sax/features/external-general-entities",
        "http://xml.org/sax/features/external-parameter-entities",
    ).forEach { feature ->
        try {
            reader.setFeature(feature, false)
        } catch (_: SAXNotRecognizedException) {
            // Safe because a declaration cannot reach this parser after rejectXmlDoctype().
        } catch (_: SAXNotSupportedException) {
            // Safe because a declaration cannot reach this parser after rejectXmlDoctype().
        }
    }
    reader.entityResolver = EntityResolver { _, _ ->
        throw SAXException("External XML entities are not allowed")
    }
    reader.contentHandler = handler
    return reader
}

private fun rejectXmlDoctype(input: InputStream) {
    val reader = xmlTextReader(input)
    val window = StringBuilder(16)
    val buffer = CharArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val count = reader.read(buffer)
        if (count < 0) break
        for (index in 0 until count) {
            window.append(buffer[index].uppercaseChar())
            if (window.length > 16) window.deleteCharAt(0)
            if (window.endsWith("<!DOCTYPE") || window.endsWith("<!ENTITY")) {
                throw SAXException("DOCTYPE and entity declarations are not allowed")
            }
        }
    }
}

private fun xmlTextReader(input: InputStream): Reader {
    val buffered = if (input is BufferedInputStream) input else BufferedInputStream(input)
    buffered.mark(4)
    val prefix = ByteArray(4)
    val count = buffered.read(prefix)
    buffered.reset()
    val charset = when {
        count >= 4 && prefix[0] == 0.toByte() && prefix[1] == 0.toByte() &&
            prefix[2] == 0xfe.toByte() && prefix[3] == 0xff.toByte() ->
            throw SAXException("UTF-32 XML is not supported")
        count >= 4 && prefix[0] == 0xff.toByte() && prefix[1] == 0xfe.toByte() &&
            prefix[2] == 0.toByte() && prefix[3] == 0.toByte() ->
            throw SAXException("UTF-32 XML is not supported")
        count >= 4 && prefix[0] == 0.toByte() && prefix[1] == 0.toByte() &&
            prefix[2] == 0.toByte() && prefix[3] == '<'.code.toByte() ->
            throw SAXException("UTF-32 XML is not supported")
        count >= 4 && prefix[0] == '<'.code.toByte() && prefix[1] == 0.toByte() &&
            prefix[2] == 0.toByte() && prefix[3] == 0.toByte() ->
            throw SAXException("UTF-32 XML is not supported")
        count >= 4 && prefix[0] == 0.toByte() && prefix[1] == 0.toByte() &&
            prefix[2] == '<'.code.toByte() && prefix[3] == 0.toByte() ->
            throw SAXException("Unsupported XML byte order")
        count >= 4 && prefix[0] == 0.toByte() && prefix[1] == '<'.code.toByte() &&
            prefix[2] == 0.toByte() && prefix[3] == 0.toByte() ->
            throw SAXException("Unsupported XML byte order")
        count >= 4 && prefix[0] == 0x4c.toByte() && prefix[1] == 0x6f.toByte() &&
            prefix[2] == 0xa7.toByte() && prefix[3] == 0x94.toByte() ->
            throw SAXException("EBCDIC XML is not supported")
        count >= 2 && prefix[0] == 0xff.toByte() && prefix[1] == 0xfe.toByte() -> Charsets.UTF_16LE
        count >= 2 && prefix[0] == 0xfe.toByte() && prefix[1] == 0xff.toByte() -> Charsets.UTF_16BE
        count >= 4 && prefix[0] == 0.toByte() && prefix[1] == '<'.code.toByte() &&
            prefix[2] == 0.toByte() && prefix[3] != 0.toByte() -> Charsets.UTF_16BE
        count >= 4 && prefix[0] == '<'.code.toByte() && prefix[1] == 0.toByte() &&
            prefix[2] != 0.toByte() && prefix[3] == 0.toByte() -> Charsets.UTF_16LE
        else -> Charsets.UTF_8
    }
    return InputStreamReader(buffered, charset)
}

private class ThreeMfHandler : DefaultHandler() {
    private val objects = linkedMapOf<String, ThreeMfObject>()
    private val buildItems = mutableListOf<ThreeMfBuildItem>()
    private var currentObject: ThreeMfObject? = null
    private var unitScaleMm = 1.0
    private var totalVertices = 0
    private var totalTriangles = 0L
    private var totalComponents = 0
    private var insideBuild = false

    override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
        if (!uri.isNullOrBlank() && uri != ThreeMfCoreNamespace) return
        when ((localName?.ifBlank { null } ?: qName.orEmpty()).substringAfter(':').lowercase(Locale.US)) {
            "model" -> unitScaleMm = unitScale(attributes.value("unit"))
            "build" -> insideBuild = true
            "object" -> {
                if (objects.size >= MaximumThreeMfObjects) throw ModelTooLargeException()
                val id = attributes.requiredBounded("id")
                require(id !in objects) { "Duplicate 3MF object id" }
                currentObject = ThreeMfObject(id).also { objects[id] = it }
            }
            "vertex" -> currentObject?.let { modelObject ->
                if (totalVertices >= MaximumVertices) throw ModelTooLargeException()
                modelObject.vertices += MeshVertex(
                    attributes.requiredDouble("x"),
                    attributes.requiredDouble("y"),
                    attributes.requiredDouble("z"),
                )
                totalVertices += 1
            }
            "triangle" -> currentObject?.let { modelObject ->
                if (totalTriangles >= MaximumThreeMfSourceTriangles) throw ModelTooLargeException()
                modelObject.triangles += intArrayOf(
                    attributes.requiredInt("v1"),
                    attributes.requiredInt("v2"),
                    attributes.requiredInt("v3"),
                )
                totalTriangles += 1
            }
            "component" -> currentObject?.let { modelObject ->
                if (totalComponents >= MaximumThreeMfComponents) throw ModelTooLargeException()
                modelObject.components += ThreeMfComponent(
                    objectId = attributes.requiredBounded("objectid"),
                    transform = AffineTransform.parse(attributes.value("transform")),
                )
                totalComponents += 1
            }
            "item" -> {
                require(insideBuild) { "3MF item must be inside build" }
                if (buildItems.size >= MaximumThreeMfBuildItems) throw ModelTooLargeException()
                buildItems += ThreeMfBuildItem(
                    objectId = attributes.requiredBounded("objectid"),
                    transform = AffineTransform.parse(attributes.value("transform")),
                )
            }
        }
    }

    override fun endElement(uri: String?, localName: String?, qName: String?) {
        if (!uri.isNullOrBlank() && uri != ThreeMfCoreNamespace) return
        when ((localName?.ifBlank { null } ?: qName.orEmpty()).substringAfter(':').lowercase(Locale.US)) {
            "object" -> currentObject = null
            "build" -> insideBuild = false
        }
    }

    fun document(): ThreeMfDocument {
        require(objects.isNotEmpty()) { "3MF contains no objects" }
        return ThreeMfDocument(unitScaleMm, objects, buildItems)
    }
}

private fun Attributes.value(name: String): String? {
    for (index in 0 until length) {
        val key = (getLocalName(index).ifBlank { getQName(index) }).substringAfter(':')
        if (key.equals(name, ignoreCase = true)) return getValue(index)
    }
    return null
}

private fun Attributes.required(name: String): String = value(name)?.takeIf(String::isNotBlank)
    ?: error("Missing 3MF attribute")

private fun Attributes.requiredBounded(name: String): String = required(name).also {
    if (it.length > 256) throw ModelTooLargeException()
}

private fun Attributes.requiredDouble(name: String): Double = required(name).toDoubleOrNull()
    ?.takeIf(Double::isFinite) ?: error("Invalid 3MF number")

private fun Attributes.requiredInt(name: String): Int = required(name).toIntOrNull()
    ?.takeIf { it >= 0 } ?: error("Invalid 3MF index")

private fun unitScale(unit: String?): Double = when (unit?.lowercase(Locale.US)) {
    null, "", "millimeter" -> 1.0
    "micron" -> 0.001
    "centimeter" -> 10.0
    "inch" -> 25.4
    "foot" -> 304.8
    "meter" -> 1000.0
    else -> error("Unsupported 3MF unit")
}

private fun writeNormalizedBinaryStl(triangles: List<MeshTriangle>, output: File): MeshInfo {
    if (triangles.isEmpty()) {
        throw ModelImportException(ModelImportError.EMPTY_MODEL, "В модели нет треугольников для печати.")
    }
    if (triangles.size.toLong() > MaximumObjTriangles) throw ModelTooLargeException()
    val sourceBounds = boundsOf(triangles)
    val offsetX = sourceBounds.centerX
    val offsetY = sourceBounds.centerY
    val offsetZ = sourceBounds.minimumZ
    BufferedOutputStream(FileOutputStream(output)).use { stream ->
        val header = ByteArray(80)
        "Feresa Slicer imported model".encodeToByteArray().copyInto(header)
        stream.write(header)
        writeLittleEndianInt(stream, triangles.size)
        triangles.forEach { triangle ->
            writeBinaryTriangle(
                stream,
                MeshTriangle(
                    triangle.first.offsetBy(-offsetX, -offsetY, -offsetZ),
                    triangle.second.offsetBy(-offsetX, -offsetY, -offsetZ),
                    triangle.third.offsetBy(-offsetX, -offsetY, -offsetZ),
                ),
            )
        }
    }
    val inspected = StlPlateComposer.inspect(output)
    require(inspected.triangleCount == triangles.size.toLong())
    return MeshInfo(inspected.triangleCount, inspected.bounds)
}

private fun MeshVertex.offsetBy(dx: Double, dy: Double, dz: Double): MeshVertex = MeshVertex(
    x + dx,
    y + dy,
    z + dz,
)

private fun boundsOf(triangles: List<MeshTriangle>): StlMeshBounds {
    var minimumX = Double.POSITIVE_INFINITY
    var maximumX = Double.NEGATIVE_INFINITY
    var minimumY = Double.POSITIVE_INFINITY
    var maximumY = Double.NEGATIVE_INFINITY
    var minimumZ = Double.POSITIVE_INFINITY
    var maximumZ = Double.NEGATIVE_INFINITY
    triangles.forEach { triangle ->
        triangle.vertices().forEach { vertex ->
            minimumX = minOf(minimumX, vertex.x)
            maximumX = maxOf(maximumX, vertex.x)
            minimumY = minOf(minimumY, vertex.y)
            maximumY = maxOf(maximumY, vertex.y)
            minimumZ = minOf(minimumZ, vertex.z)
            maximumZ = maxOf(maximumZ, vertex.z)
        }
    }
    return StlMeshBounds(minimumX, maximumX, minimumY, maximumY, minimumZ, maximumZ)
}

private fun writeLittleEndianInt(output: BufferedOutputStream, value: Int) {
    output.write(value and 0xff)
    output.write((value ushr 8) and 0xff)
    output.write((value ushr 16) and 0xff)
    output.write((value ushr 24) and 0xff)
}

private fun writeBinaryTriangle(output: BufferedOutputStream, triangle: MeshTriangle) {
    val ux = triangle.second.x - triangle.first.x
    val uy = triangle.second.y - triangle.first.y
    val uz = triangle.second.z - triangle.first.z
    val vx = triangle.third.x - triangle.first.x
    val vy = triangle.third.y - triangle.first.y
    val vz = triangle.third.z - triangle.first.z
    var nx = uy * vz - uz * vy
    var ny = uz * vx - ux * vz
    var nz = ux * vy - uy * vx
    val magnitude = sqrt(nx * nx + ny * ny + nz * nz)
    if (magnitude > 1e-12) {
        nx /= magnitude
        ny /= magnitude
        nz /= magnitude
    } else {
        nx = 0.0
        ny = 0.0
        nz = 0.0
    }
    val record = ByteBuffer.allocate(50).order(ByteOrder.LITTLE_ENDIAN)
    record.putFloat(nx.toFloat()).putFloat(ny.toFloat()).putFloat(nz.toFloat())
    triangle.vertices().forEach { vertex ->
        record.putFloat(vertex.x.toFloat()).putFloat(vertex.y.toFloat()).putFloat(vertex.z.toFloat())
    }
    record.putShort(0)
    output.write(record.array())
}

private fun copyWithLimit(input: InputStream, output: java.io.OutputStream, limit: Long) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        total += count
        if (total > limit) throw ModelTooLargeException()
        output.write(buffer, 0, count)
    }
}

private class LimitedInputStream(input: InputStream, private val limit: Long) : FilterInputStream(input) {
    private var consumed = 0L

    override fun read(): Int {
        val value = super.read()
        if (value >= 0) account(1)
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val count = super.read(buffer, offset, length)
        if (count > 0) account(count)
        return count
    }

    private fun account(count: Int) {
        consumed += count
        if (consumed > limit) throw ModelTooLargeException()
    }
}
