// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.slicer

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class StlMeshBounds(
    val minimumX: Double,
    val maximumX: Double,
    val minimumY: Double,
    val maximumY: Double,
    val minimumZ: Double,
    val maximumZ: Double,
) {
    val centerX: Double get() = (minimumX + maximumX) / 2.0
    val centerY: Double get() = (minimumY + maximumY) / 2.0
    val centerZ: Double get() = (minimumZ + maximumZ) / 2.0
    val width: Double get() = maximumX - minimumX
    val depth: Double get() = maximumY - minimumY
    val height: Double get() = maximumZ - minimumZ

    fun isInsideBed(widthMm: Double, depthMm: Double, toleranceMm: Double = 0.001): Boolean =
        minimumX >= -toleranceMm &&
            minimumY >= -toleranceMm &&
            maximumX <= widthMm + toleranceMm &&
            maximumY <= depthMm + toleranceMm
}

data class StlMeshInfo(
    val triangleCount: Long,
    val bounds: StlMeshBounds,
)

data class StlPlatePlacement(
    val file: File,
    val positionXmm: Double,
    val positionYmm: Double,
    val rotationDegrees: Double = 0.0,
    val scale: Double = 1.0,
    val positionZmm: Double = 0.0,
    val rotationXDegrees: Double = 0.0,
    val rotationYDegrees: Double = 0.0,
    val scaleX: Double = 1.0,
    val scaleY: Double = 1.0,
    val scaleZ: Double = 1.0,
) {
    val rotationZDegrees: Double get() = rotationDegrees
    val effectiveScaleX: Double get() = scale * scaleX
    val effectiveScaleY: Double get() = scale * scaleY
    val effectiveScaleZ: Double get() = scale * scaleZ
}

/** Euler orientation and exact Z correction suggested for placing a large STL face on the bed. */
data class StlLayFlatOrientation(
    val rotationXDegrees: Double,
    val rotationYDegrees: Double,
    val rotationZDegrees: Double,
    val positionZmm: Double,
    val supportingFaceAreaMm2: Double,
)

data class StlPlateComposition(
    val file: File,
    val triangleCount: Long,
    val bounds: StlMeshBounds,
)

/**
 * Builds one binary STL from all objects on the current plate.
 *
 * The pinned Orca mobile JNI opens one model file per slice. Composing the plate before handing it
 * to Orca keeps every visible object printable without falling back to the legacy slicer. Source
 * objects are centered on their own XY bounds, placed on local Z=0, then transformed using
 * S -> Rx -> Ry -> Rz -> T. Legacy `rotationDegrees` and `scale` remain Z rotation and a uniform
 * multiplier so old plates compose identically.
 */
object StlPlateComposer {
    fun inspect(file: File): StlMeshInfo {
        require(file.isFile) { "STL file does not exist: ${file.path}" }
        val accumulator = BoundsAccumulator()
        var triangleCount = 0L
        forEachTriangle(file) { triangle ->
            triangle.vertices.forEach(accumulator::include)
            triangleCount += 1
        }
        require(triangleCount > 0) { "STL contains no triangles: ${file.name}" }
        return StlMeshInfo(triangleCount, accumulator.toBounds())
    }

    fun placedBounds(info: StlMeshInfo, placement: StlPlatePlacement): StlMeshBounds {
        validatePlacement(placement)
        val source = info.bounds
        val rotation = Matrix3.fromEuler(placement)
        val accumulator = BoundsAccumulator()
        val xValues = doubleArrayOf(source.minimumX, source.maximumX)
        val yValues = doubleArrayOf(source.minimumY, source.maximumY)
        val zValues = doubleArrayOf(source.minimumZ, source.maximumZ)
        xValues.forEach { x ->
            yValues.forEach { y ->
                zValues.forEach { z ->
                    val local = Vertex(
                        x = (x - source.centerX) * placement.effectiveScaleX,
                        y = (y - source.centerY) * placement.effectiveScaleY,
                        z = (z - source.minimumZ) * placement.effectiveScaleZ,
                    )
                    val rotated = rotation.apply(local)
                    accumulator.include(
                        Vertex(
                            x = rotated.x + placement.positionXmm,
                            y = rotated.y + placement.positionYmm,
                            z = rotated.z + placement.positionZmm,
                        ),
                    )
                }
            }
        }
        return accumulator.toBounds()
    }

    /**
     * Scans the actual triangle vertices and returns their exact transformed world-space bounds.
     * Unlike [placedBounds], this does not expand a rotated source AABB and is therefore suitable
     * for an exact "move to bed" Z correction.
     */
    fun exactPlacedBounds(placement: StlPlatePlacement): StlMeshBounds {
        validatePlacement(placement)
        val info = inspect(placement.file)
        val rotation = Matrix3.fromEuler(placement)
        val accumulator = BoundsAccumulator()
        forEachTriangle(placement.file) { triangle ->
            triangle.vertices.forEach { source ->
                accumulator.include(transformVertex(source, info.bounds, placement, rotation))
            }
        }
        return accumulator.toBounds()
    }

    /**
     * Suggests an orientation that makes the largest non-degenerate triangle horizontal. The
     * triangle normal is oriented away from the bulk of the mesh and then mapped to -Z, which puts
     * the mesh body above its supporting face for consistently wound watertight STLs. The returned
     * exact [StlLayFlatOrientation.positionZmm] moves the lowest transformed vertex to Z=0.
     */
    fun suggestLayFlat(file: File): StlLayFlatOrientation? {
        require(file.isFile) { "STL file does not exist: ${file.path}" }
        var supportingTriangle: Triangle? = null
        var supportingCross: Vertex? = null
        var largestDoubleArea = 0.0
        forEachTriangle(file) { triangle ->
            val cross = triangle.crossProduct()
            val doubleArea = cross.magnitude()
            if (doubleArea > largestDoubleArea + GeometryEpsilon) {
                largestDoubleArea = doubleArea
                supportingTriangle = triangle
                supportingCross = cross
            }
        }
        val face = supportingTriangle ?: return null
        var normal = supportingCross?.normalizedOrNull() ?: return null
        val faceCenter = face.centroid()
        var signedDistanceSum = 0.0
        var vertexCount = 0L
        val info = inspect(file)
        forEachTriangle(file) { triangle ->
            triangle.vertices.forEach { vertex ->
                signedDistanceSum += (vertex - faceCenter).dot(normal)
                vertexCount += 1
            }
        }
        if (vertexCount > 0L && signedDistanceSum > 0.0) normal = normal * -1.0

        val rotation = Matrix3.fromTo(normal, Vertex(0.0, 0.0, -1.0))
        val euler = rotation.toEulerDegrees()
        var minimumZ = Double.POSITIVE_INFINITY
        forEachTriangle(file) { triangle ->
            triangle.vertices.forEach { source ->
                val local = Vertex(
                    x = source.x - info.bounds.centerX,
                    y = source.y - info.bounds.centerY,
                    z = source.z - info.bounds.minimumZ,
                )
                minimumZ = minOf(minimumZ, rotation.apply(local).z)
            }
        }
        if (!minimumZ.isFinite()) return null
        return StlLayFlatOrientation(
            rotationXDegrees = euler.x.normalizedDegrees(),
            rotationYDegrees = euler.y.normalizedDegrees(),
            rotationZDegrees = euler.z.normalizedDegrees(),
            positionZmm = -minimumZ,
            supportingFaceAreaMm2 = largestDoubleArea / 2.0,
        )
    }

    fun compose(placements: List<StlPlatePlacement>, output: File): StlPlateComposition {
        require(placements.isNotEmpty()) { "The print plate contains no models" }
        val inspected = placements.map { placement ->
            validatePlacement(placement)
            placement to inspect(placement.file)
        }
        val totalTriangles = inspected.sumOf { it.second.triangleCount }
        require(totalTriangles in 1..Int.MAX_VALUE.toLong()) {
            "STL triangle count is too large for this Android build"
        }

        output.absoluteFile.parentFile?.let { parent ->
            require(parent.exists() || parent.mkdirs()) { "Cannot create plate output directory: $parent" }
        }
        val temporary = File(output.absolutePath + ".tmp")
        if (temporary.exists()) require(temporary.delete()) { "Cannot replace temporary STL: $temporary" }

        val outputBounds = BoundsAccumulator()
        runCatching {
            BufferedOutputStream(FileOutputStream(temporary)).use { stream ->
                val header = ByteArray(80)
                "Feresa Slicer multi-model plate".encodeToByteArray().copyInto(header)
                stream.write(header)
                writeLittleEndianInt(stream, totalTriangles.toInt())

                inspected.forEach { (placement, info) ->
                    val rotation = Matrix3.fromEuler(placement)
                    forEachTriangle(placement.file) { sourceTriangle ->
                        val transformed = Triangle(
                            sourceTriangle.vertices.map { source ->
                                transformVertex(source, info.bounds, placement, rotation)
                                    .also(outputBounds::include)
                            },
                        )
                        writeBinaryTriangle(stream, transformed)
                    }
                }
            }
            if (output.exists()) require(output.delete()) { "Cannot replace composed STL: $output" }
            require(temporary.renameTo(output)) { "Cannot finalize composed STL: $output" }
        }.onFailure {
            temporary.delete()
        }.getOrThrow()

        return StlPlateComposition(
            file = output,
            triangleCount = totalTriangles,
            bounds = outputBounds.toBounds(),
        )
    }
}

private fun validatePlacement(placement: StlPlatePlacement) {
    require(
        placement.positionXmm.isFinite() &&
            placement.positionYmm.isFinite() &&
            placement.positionZmm.isFinite(),
    ) {
        "Model position must be finite"
    }
    require(placement.rotationDegrees.isFinite()) { "Model rotation must be finite" }
    require(placement.rotationXDegrees.isFinite() && placement.rotationYDegrees.isFinite()) {
        "Model XYZ rotation must be finite"
    }
    require(placement.scale.isFinite() && placement.scale > 0.0) {
        "Model scale must be finite and greater than zero"
    }
    require(
        placement.scaleX.isFinite() && placement.scaleX > 0.0 &&
            placement.scaleY.isFinite() && placement.scaleY > 0.0 &&
            placement.scaleZ.isFinite() && placement.scaleZ > 0.0,
    ) {
        "Model XYZ scale must be finite and greater than zero"
    }
}

private fun transformVertex(
    source: Vertex,
    sourceBounds: StlMeshBounds,
    placement: StlPlatePlacement,
    rotation: Matrix3,
): Vertex {
    val local = Vertex(
        x = (source.x - sourceBounds.centerX) * placement.effectiveScaleX,
        y = (source.y - sourceBounds.centerY) * placement.effectiveScaleY,
        z = (source.z - sourceBounds.minimumZ) * placement.effectiveScaleZ,
    )
    val rotated = rotation.apply(local)
    return Vertex(
        x = placement.positionXmm + rotated.x,
        y = placement.positionYmm + rotated.y,
        z = placement.positionZmm + rotated.z,
    )
}

private const val GeometryEpsilon = 1e-12

private data class Vertex(val x: Double, val y: Double, val z: Double) {
    operator fun minus(other: Vertex): Vertex = Vertex(x - other.x, y - other.y, z - other.z)
    operator fun times(factor: Double): Vertex = Vertex(x * factor, y * factor, z * factor)
    fun dot(other: Vertex): Double = x * other.x + y * other.y + z * other.z
    fun cross(other: Vertex): Vertex = Vertex(
        x = y * other.z - z * other.y,
        y = z * other.x - x * other.z,
        z = x * other.y - y * other.x,
    )
    fun magnitude(): Double = sqrt(dot(this))
    fun normalizedOrNull(): Vertex? {
        val magnitude = magnitude()
        return if (magnitude > GeometryEpsilon) this * (1.0 / magnitude) else null
    }
}

private data class Triangle(val vertices: List<Vertex>) {
    init {
        require(vertices.size == 3)
        require(vertices.all { it.x.isFinite() && it.y.isFinite() && it.z.isFinite() }) {
            "STL contains a non-finite vertex"
        }
    }

    fun crossProduct(): Vertex = (vertices[1] - vertices[0]).cross(vertices[2] - vertices[0])

    fun centroid(): Vertex = Vertex(
        x = (vertices[0].x + vertices[1].x + vertices[2].x) / 3.0,
        y = (vertices[0].y + vertices[1].y + vertices[2].y) / 3.0,
        z = (vertices[0].z + vertices[1].z + vertices[2].z) / 3.0,
    )
}

/** Row-major 3x3 rotation matrix. Euler convention is Rz * Ry * Rx. */
private data class Matrix3(
    val m00: Double,
    val m01: Double,
    val m02: Double,
    val m10: Double,
    val m11: Double,
    val m12: Double,
    val m20: Double,
    val m21: Double,
    val m22: Double,
) {
    fun apply(vertex: Vertex): Vertex = Vertex(
        x = m00 * vertex.x + m01 * vertex.y + m02 * vertex.z,
        y = m10 * vertex.x + m11 * vertex.y + m12 * vertex.z,
        z = m20 * vertex.x + m21 * vertex.y + m22 * vertex.z,
    )

    fun toEulerDegrees(): Vertex {
        val clamped = (-m20).coerceIn(-1.0, 1.0)
        val y = asin(clamped)
        val cosineY = cos(y)
        val x: Double
        val z: Double
        if (abs(cosineY) > 1e-8) {
            x = atan2(m21, m22)
            z = atan2(m10, m00)
        } else {
            // Gimbal lock: choose Z=0 and preserve the represented rotation through X.
            x = atan2(-m12, m11)
            z = 0.0
        }
        return Vertex(Math.toDegrees(x), Math.toDegrees(y), Math.toDegrees(z))
    }

    companion object {
        fun fromEuler(placement: StlPlatePlacement): Matrix3 = fromEulerDegrees(
            xDegrees = placement.rotationXDegrees,
            yDegrees = placement.rotationYDegrees,
            zDegrees = placement.rotationZDegrees,
        )

        fun fromEulerDegrees(xDegrees: Double, yDegrees: Double, zDegrees: Double): Matrix3 {
            val x = Math.toRadians(xDegrees)
            val y = Math.toRadians(yDegrees)
            val z = Math.toRadians(zDegrees)
            val cx = cos(x)
            val sx = sin(x)
            val cy = cos(y)
            val sy = sin(y)
            val cz = cos(z)
            val sz = sin(z)
            return Matrix3(
                m00 = cz * cy,
                m01 = cz * sy * sx - sz * cx,
                m02 = cz * sy * cx + sz * sx,
                m10 = sz * cy,
                m11 = sz * sy * sx + cz * cx,
                m12 = sz * sy * cx - cz * sx,
                m20 = -sy,
                m21 = cy * sx,
                m22 = cy * cx,
            )
        }

        fun fromTo(source: Vertex, target: Vertex): Matrix3 {
            val from = requireNotNull(source.normalizedOrNull()) { "Source direction is zero" }
            val to = requireNotNull(target.normalizedOrNull()) { "Target direction is zero" }
            val cosine = from.dot(to).coerceIn(-1.0, 1.0)
            if (cosine > 1.0 - GeometryEpsilon) return identity()
            if (cosine < -1.0 + GeometryEpsilon) {
                val reference = if (abs(from.x) < 0.9) Vertex(1.0, 0.0, 0.0) else Vertex(0.0, 1.0, 0.0)
                val axis = requireNotNull(from.cross(reference).normalizedOrNull())
                return axisAngle(axis, PI)
            }
            val axis = requireNotNull(from.cross(to).normalizedOrNull())
            return axisAngle(axis, acos(cosine))
        }

        private fun axisAngle(axis: Vertex, radians: Double): Matrix3 {
            val x = axis.x
            val y = axis.y
            val z = axis.z
            val cosine = cos(radians)
            val sine = sin(radians)
            val oneMinusCosine = 1.0 - cosine
            return Matrix3(
                m00 = cosine + x * x * oneMinusCosine,
                m01 = x * y * oneMinusCosine - z * sine,
                m02 = x * z * oneMinusCosine + y * sine,
                m10 = y * x * oneMinusCosine + z * sine,
                m11 = cosine + y * y * oneMinusCosine,
                m12 = y * z * oneMinusCosine - x * sine,
                m20 = z * x * oneMinusCosine - y * sine,
                m21 = z * y * oneMinusCosine + x * sine,
                m22 = cosine + z * z * oneMinusCosine,
            )
        }

        private fun identity(): Matrix3 = Matrix3(
            1.0, 0.0, 0.0,
            0.0, 1.0, 0.0,
            0.0, 0.0, 1.0,
        )
    }
}

private class BoundsAccumulator {
    private var minimumX = Double.POSITIVE_INFINITY
    private var maximumX = Double.NEGATIVE_INFINITY
    private var minimumY = Double.POSITIVE_INFINITY
    private var maximumY = Double.NEGATIVE_INFINITY
    private var minimumZ = Double.POSITIVE_INFINITY
    private var maximumZ = Double.NEGATIVE_INFINITY

    fun include(vertex: Vertex) {
        minimumX = minOf(minimumX, vertex.x)
        maximumX = maxOf(maximumX, vertex.x)
        minimumY = minOf(minimumY, vertex.y)
        maximumY = maxOf(maximumY, vertex.y)
        minimumZ = minOf(minimumZ, vertex.z)
        maximumZ = maxOf(maximumZ, vertex.z)
    }

    fun toBounds(): StlMeshBounds {
        require(minimumX.isFinite() && maximumX.isFinite()) { "STL bounds are empty" }
        return StlMeshBounds(minimumX, maximumX, minimumY, maximumY, minimumZ, maximumZ)
    }
}

private fun forEachTriangle(file: File, consumer: (Triangle) -> Unit) {
    if (isBinaryStl(file)) {
        forEachBinaryTriangle(file, consumer)
    } else {
        forEachAsciiTriangle(file, consumer)
    }
}

private fun isBinaryStl(file: File): Boolean {
    if (file.length() < 84L) return false
    val prefix = ByteArray(84)
    DataInputStream(BufferedInputStream(FileInputStream(file))).use { input -> input.readFully(prefix) }
    val count = ByteBuffer.wrap(prefix, 80, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xffff_ffffL
    if (count > (Long.MAX_VALUE - 84L) / 50L) return false
    return 84L + count * 50L == file.length()
}

private fun forEachBinaryTriangle(file: File, consumer: (Triangle) -> Unit) {
    DataInputStream(BufferedInputStream(FileInputStream(file))).use { input ->
        val prefix = ByteArray(84)
        input.readFully(prefix)
        val count = ByteBuffer.wrap(prefix, 80, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xffff_ffffL
        require(count <= Int.MAX_VALUE.toLong()) { "STL triangle count is too large for this Android build" }
        val record = ByteArray(50)
        repeat(count.toInt()) {
            input.readFully(record)
            val buffer = ByteBuffer.wrap(record).order(ByteOrder.LITTLE_ENDIAN)
            buffer.position(12)
            consumer(
                Triangle(
                    List(3) {
                        Vertex(
                            x = buffer.float.toDouble(),
                            y = buffer.float.toDouble(),
                            z = buffer.float.toDouble(),
                        )
                    },
                ),
            )
        }
    }
}

private fun forEachAsciiTriangle(file: File, consumer: (Triangle) -> Unit) {
    val vertices = ArrayList<Vertex>(3)
    file.bufferedReader().useLines { lines ->
        lines.forEach { rawLine ->
            val parts = rawLine.trim().split(Regex("\\s+"))
            if (parts.size < 4 || !parts[0].equals("vertex", ignoreCase = true)) return@forEach
            val vertex = Vertex(
                x = parts[1].toDoubleOrNull() ?: error("Invalid STL vertex X in ${file.name}"),
                y = parts[2].toDoubleOrNull() ?: error("Invalid STL vertex Y in ${file.name}"),
                z = parts[3].toDoubleOrNull() ?: error("Invalid STL vertex Z in ${file.name}"),
            )
            require(vertex.x.isFinite() && vertex.y.isFinite() && vertex.z.isFinite()) {
                "STL contains a non-finite vertex"
            }
            vertices += vertex
            if (vertices.size == 3) {
                consumer(Triangle(vertices.toList()))
                vertices.clear()
            }
        }
    }
    require(vertices.isEmpty()) { "ASCII STL has an incomplete triangle: ${file.name}" }
}

private fun writeLittleEndianInt(output: BufferedOutputStream, value: Int) {
    output.write(value and 0xff)
    output.write((value ushr 8) and 0xff)
    output.write((value ushr 16) and 0xff)
    output.write((value ushr 24) and 0xff)
}

private fun writeBinaryTriangle(output: BufferedOutputStream, triangle: Triangle) {
    val (first, second, third) = triangle.vertices
    val ux = second.x - first.x
    val uy = second.y - first.y
    val uz = second.z - first.z
    val vx = third.x - first.x
    val vy = third.y - first.y
    val vz = third.z - first.z
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
    triangle.vertices.forEach { vertex ->
        record.putFloat(vertex.x.toFloat()).putFloat(vertex.y.toFloat()).putFloat(vertex.z.toFloat())
    }
    record.putShort(0)
    output.write(record.array())
}

private fun Double.normalizedDegrees(): Double {
    val normalized = this % 360.0
    return if (normalized < 0.0) normalized + 360.0 else normalized
}
