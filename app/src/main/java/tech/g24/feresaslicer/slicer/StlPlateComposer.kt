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

/**
 * Result of Feresa's bounded, deterministic auto-orientation heuristic.
 *
 * This is intentionally a small Android-friendly heuristic, not OrcaSlicer's full auto-orient
 * implementation. It compares a limited set of dominant surface normals and axis directions,
 * preferring less estimated support, a larger bed-contact hull, and a lower result.
 */
data class StlAutoOrientationSuggestion(
    val rotationXDegrees: Double,
    val rotationYDegrees: Double,
    val rotationZDegrees: Double,
    /** Exact correction that moves the lowest transformed mesh vertex to Z=0. */
    val positionZmm: Double,
    val estimatedUnsupportedAreaMm2: Double,
    val contactHullAreaMm2: Double,
    val resultingHeightMm: Double,
    val score: Double,
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

    /**
     * Suggests a basic orientation without invoking OrcaSlicer's full auto-orient solver.
     *
     * Candidate support directions are formed from the largest normal clusters plus the six
     * principal axes. Each direction is evaluated at two deterministic bed-plane rotations. A
     * candidate is rejected when its exact transformed bounds do not fit the requested build
     * volume. Remaining candidates are ranked by estimated unsupported face area (70%), missing
     * contact coverage (20%), and resulting height (10%). Bed-contact faces are not counted as
     * unsupported. Equal candidates prefer the smallest rotation from the source orientation.
     *
     * [overhangThresholdDegrees] is measured from the downward build direction: a non-contact
     * face whose outward normal is within this angle of -Z contributes its complete area to the
     * support estimate. The returned [StlAutoOrientationSuggestion.positionZmm] is computed from
     * actual transformed vertices and therefore places the result exactly on Z=0.
     */
    fun suggestBasicAutoOrientation(
        file: File,
        bedWidthMm: Double,
        bedDepthMm: Double,
        maximumHeightMm: Double,
        overhangThresholdDegrees: Double = 45.0,
        maximumCandidateNormals: Int = 12,
        normalClusterToleranceDegrees: Double = 2.0,
    ): StlAutoOrientationSuggestion? {
        require(file.isFile) { "STL file does not exist: ${file.path}" }
        require(bedWidthMm.isFinite() && bedWidthMm > 0.0) {
            "Bed width must be finite and greater than zero"
        }
        require(bedDepthMm.isFinite() && bedDepthMm > 0.0) {
            "Bed depth must be finite and greater than zero"
        }
        require(maximumHeightMm.isFinite() && maximumHeightMm > 0.0) {
            "Maximum height must be finite and greater than zero"
        }
        require(overhangThresholdDegrees.isFinite() && overhangThresholdDegrees in 0.0..90.0) {
            "Overhang threshold must be between 0 and 90 degrees"
        }
        require(maximumCandidateNormals in 1..24) {
            "Maximum candidate normal count must be between 1 and 24"
        }
        require(
            normalClusterToleranceDegrees.isFinite() &&
                normalClusterToleranceDegrees > 0.0 &&
                normalClusterToleranceDegrees <= 30.0,
        ) {
            "Normal cluster tolerance must be greater than 0 and at most 30 degrees"
        }

        val sourceTriangles = ArrayList<Triangle>()
        val sourceBoundsAccumulator = BoundsAccumulator()
        forEachTriangle(file) { triangle ->
            sourceTriangles += triangle
            triangle.vertices.forEach(sourceBoundsAccumulator::include)
        }
        require(sourceTriangles.isNotEmpty()) { "STL contains no triangles: ${file.name}" }
        val sourceBounds = sourceBoundsAccumulator.toBounds()
        val meshCenter = Vertex(sourceBounds.centerX, sourceBounds.centerY, sourceBounds.centerZ)
        val faces = sourceTriangles.mapNotNull { triangle ->
            val cross = triangle.crossProduct()
            val doubleArea = cross.magnitude()
            if (doubleArea <= GeometryEpsilon) {
                null
            } else {
                var normal = cross * (1.0 / doubleArea)
                // STL winding is not always trustworthy. For a closed mesh, the face-to-bounds-
                // center direction is a useful deterministic outward-normal fallback.
                if ((triangle.centroid() - meshCenter).dot(normal) < 0.0) normal = normal * -1.0
                AutoOrientationFace(triangle, normal, doubleArea / 2.0)
            }
        }
        if (faces.isEmpty()) return null

        val dominantNormals = clusterDominantNormals(
            faces = faces,
            toleranceDegrees = normalClusterToleranceDegrees,
            maximumCount = maximumCandidateNormals,
        )
        val directionDeduplicationCosine = cos(Math.toRadians(0.25))
        val supportDirections = ArrayList<Vertex>(6 + dominantNormals.size)
        fun addSupportDirection(direction: Vertex) {
            val normalized = direction.normalizedOrNull() ?: return
            if (supportDirections.none { it.dot(normalized) >= directionDeduplicationCosine }) {
                supportDirections += normalized
            }
        }
        // Identity is deliberately first so exact score ties preserve the current orientation.
        addSupportDirection(Vertex(0.0, 0.0, -1.0))
        addSupportDirection(Vertex(0.0, 0.0, 1.0))
        addSupportDirection(Vertex(-1.0, 0.0, 0.0))
        addSupportDirection(Vertex(1.0, 0.0, 0.0))
        addSupportDirection(Vertex(0.0, -1.0, 0.0))
        addSupportDirection(Vertex(0.0, 1.0, 0.0))
        dominantNormals.forEach(::addSupportDirection)

        val matrices = ArrayList<Matrix3>(supportDirections.size * 2)
        supportDirections.forEach { supportDirection ->
            val base = Matrix3.fromTo(supportDirection, Vertex(0.0, 0.0, -1.0))
            listOf(base, base.preRotateZDegrees(90.0)).forEach { matrix ->
                if (matrices.none { it.isApproximatelyEqualTo(matrix) }) matrices += matrix
            }
        }

        val totalSurfaceArea = faces.sumOf(AutoOrientationFace::areaMm2)
        val downwardNormalLimit = -cos(Math.toRadians(overhangThresholdDegrees))
        var best: AutoOrientationEvaluation? = null
        matrices.forEachIndexed { index, matrix ->
            val evaluation = evaluateAutoOrientationCandidate(
                faces = faces,
                sourceBounds = sourceBounds,
                matrix = matrix,
                totalSurfaceArea = totalSurfaceArea,
                downwardNormalLimit = downwardNormalLimit,
                bedWidthMm = bedWidthMm,
                bedDepthMm = bedDepthMm,
                maximumHeightMm = maximumHeightMm,
                candidateIndex = index,
            ) ?: return@forEachIndexed
            if (best == null || evaluation.isBetterThan(requireNotNull(best))) best = evaluation
        }

        val selected = best ?: return null
        val euler = selected.matrix.toEulerDegrees()
        return StlAutoOrientationSuggestion(
            rotationXDegrees = euler.x.normalizedDegrees(),
            rotationYDegrees = euler.y.normalizedDegrees(),
            rotationZDegrees = euler.z.normalizedDegrees(),
            positionZmm = (-selected.bounds.minimumZ).zeroIfTiny(),
            estimatedUnsupportedAreaMm2 = selected.unsupportedAreaMm2,
            contactHullAreaMm2 = selected.contactHullAreaMm2,
            resultingHeightMm = selected.bounds.height,
            score = selected.score,
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

private data class AutoOrientationFace(
    val triangle: Triangle,
    val outwardNormal: Vertex,
    val areaMm2: Double,
)

private class AutoOrientationNormalCluster(first: AutoOrientationFace) {
    var totalAreaMm2: Double = first.areaMm2
        private set
    private var weightedX: Double = first.outwardNormal.x * first.areaMm2
    private var weightedY: Double = first.outwardNormal.y * first.areaMm2
    private var weightedZ: Double = first.outwardNormal.z * first.areaMm2

    val direction: Vertex
        get() = requireNotNull(Vertex(weightedX, weightedY, weightedZ).normalizedOrNull())

    fun include(face: AutoOrientationFace) {
        totalAreaMm2 += face.areaMm2
        weightedX += face.outwardNormal.x * face.areaMm2
        weightedY += face.outwardNormal.y * face.areaMm2
        weightedZ += face.outwardNormal.z * face.areaMm2
    }
}

private data class Point2(val x: Double, val y: Double)

private data class AutoOrientationEvaluation(
    val matrix: Matrix3,
    val bounds: StlMeshBounds,
    val unsupportedAreaMm2: Double,
    val contactHullAreaMm2: Double,
    val score: Double,
    val rotationDistanceRadians: Double,
    val candidateIndex: Int,
) {
    fun isBetterThan(other: AutoOrientationEvaluation): Boolean {
        compareMetric(score, other.score)?.let { return it < 0 }
        compareMetric(unsupportedAreaMm2, other.unsupportedAreaMm2)?.let { return it < 0 }
        compareMetric(contactHullAreaMm2, other.contactHullAreaMm2)?.let { return it > 0 }
        compareMetric(bounds.height, other.bounds.height)?.let { return it < 0 }
        compareMetric(rotationDistanceRadians, other.rotationDistanceRadians)?.let { return it < 0 }
        return candidateIndex < other.candidateIndex
    }
}

private fun compareMetric(first: Double, second: Double): Int? {
    val tolerance = 1e-10 * maxOf(1.0, abs(first), abs(second))
    return when {
        first < second - tolerance -> -1
        first > second + tolerance -> 1
        else -> null
    }
}

private fun clusterDominantNormals(
    faces: List<AutoOrientationFace>,
    toleranceDegrees: Double,
    maximumCount: Int,
): List<Vertex> {
    val minimumDot = cos(Math.toRadians(toleranceDegrees))
    // Sorting makes greedy grouping independent of STL triangle record order.
    val orderedFaces = faces.sortedWith(
        compareBy<AutoOrientationFace> { it.outwardNormal.x }
            .thenBy { it.outwardNormal.y }
            .thenBy { it.outwardNormal.z }
            .thenBy { it.triangle.centroid().x }
            .thenBy { it.triangle.centroid().y }
            .thenBy { it.triangle.centroid().z },
    )
    val clusters = ArrayList<AutoOrientationNormalCluster>()
    orderedFaces.forEach { face ->
        var selectedIndex = -1
        var selectedDot = minimumDot
        clusters.forEachIndexed { index, cluster ->
            val dot = cluster.direction.dot(face.outwardNormal)
            if (dot >= minimumDot && (selectedIndex < 0 || dot > selectedDot + GeometryEpsilon)) {
                selectedIndex = index
                selectedDot = dot
            }
        }
        if (selectedIndex >= 0) {
            clusters[selectedIndex].include(face)
        } else {
            clusters += AutoOrientationNormalCluster(face)
        }
    }
    return clusters
        .sortedWith(
            compareByDescending<AutoOrientationNormalCluster> { it.totalAreaMm2 }
                .thenBy { it.direction.x }
                .thenBy { it.direction.y }
                .thenBy { it.direction.z },
        )
        .take(maximumCount)
        .map(AutoOrientationNormalCluster::direction)
}

private fun evaluateAutoOrientationCandidate(
    faces: List<AutoOrientationFace>,
    sourceBounds: StlMeshBounds,
    matrix: Matrix3,
    totalSurfaceArea: Double,
    downwardNormalLimit: Double,
    bedWidthMm: Double,
    bedDepthMm: Double,
    maximumHeightMm: Double,
    candidateIndex: Int,
): AutoOrientationEvaluation? {
    val boundsAccumulator = BoundsAccumulator()
    faces.forEach { face ->
        face.triangle.vertices.forEach { source ->
            boundsAccumulator.include(matrix.apply(source.toCenteredLocal(sourceBounds)))
        }
    }
    val bounds = boundsAccumulator.toBounds()
    val fitTolerance = 1e-6
    if (
        bounds.width > bedWidthMm + fitTolerance ||
        bounds.depth > bedDepthMm + fitTolerance ||
        bounds.height > maximumHeightMm + fitTolerance
    ) {
        return null
    }

    val contactTolerance = maxOf(
        1e-5,
        maxOf(bounds.width, bounds.depth, bounds.height) * 1e-6,
    )
    val contactPoints = ArrayList<Point2>()
    var unsupportedArea = 0.0
    faces.forEach { face ->
        val transformed = face.triangle.vertices.map { source ->
            matrix.apply(source.toCenteredLocal(sourceBounds))
        }
        val touchesBedAsFace = transformed.all { vertex ->
            abs(vertex.z - bounds.minimumZ) <= contactTolerance
        }
        if (touchesBedAsFace) {
            transformed.forEach { vertex -> contactPoints += Point2(vertex.x, vertex.y) }
        } else {
            val transformedNormal = matrix.apply(face.outwardNormal)
            if (transformedNormal.z < downwardNormalLimit - GeometryEpsilon) {
                unsupportedArea += face.areaMm2
            }
        }
    }

    val contactHullArea = convexHullArea(contactPoints)
    val footprintArea = bounds.width * bounds.depth
    val unsupportedRatio = (unsupportedArea / totalSurfaceArea).coerceIn(0.0, 1.0)
    val contactCoverage = if (footprintArea > GeometryEpsilon) {
        (contactHullArea / footprintArea).coerceIn(0.0, 1.0)
    } else {
        0.0
    }
    val heightRatio = (bounds.height / maximumHeightMm).coerceIn(0.0, 1.0)
    val score =
        AutoOrientationUnsupportedWeight * unsupportedRatio +
            AutoOrientationContactWeight * (1.0 - contactCoverage) +
            AutoOrientationHeightWeight * heightRatio
    return AutoOrientationEvaluation(
        matrix = matrix,
        bounds = bounds,
        unsupportedAreaMm2 = unsupportedArea,
        contactHullAreaMm2 = contactHullArea,
        score = score,
        rotationDistanceRadians = matrix.rotationDistanceRadians(),
        candidateIndex = candidateIndex,
    )
}

private fun Vertex.toCenteredLocal(sourceBounds: StlMeshBounds): Vertex = Vertex(
    x = x - sourceBounds.centerX,
    y = y - sourceBounds.centerY,
    z = z - sourceBounds.minimumZ,
)

private fun convexHullArea(points: List<Point2>): Double {
    if (points.size < 3) return 0.0
    val sorted = points.distinct().sortedWith(compareBy<Point2> { it.x }.thenBy { it.y })
    if (sorted.size < 3) return 0.0

    fun cross(origin: Point2, first: Point2, second: Point2): Double =
        (first.x - origin.x) * (second.y - origin.y) -
            (first.y - origin.y) * (second.x - origin.x)

    val lower = ArrayList<Point2>()
    sorted.forEach { point ->
        while (lower.size >= 2 && cross(lower[lower.lastIndex - 1], lower.last(), point) <= GeometryEpsilon) {
            lower.removeAt(lower.lastIndex)
        }
        lower += point
    }
    val upper = ArrayList<Point2>()
    sorted.asReversed().forEach { point ->
        while (upper.size >= 2 && cross(upper[upper.lastIndex - 1], upper.last(), point) <= GeometryEpsilon) {
            upper.removeAt(upper.lastIndex)
        }
        upper += point
    }
    lower.removeAt(lower.lastIndex)
    upper.removeAt(upper.lastIndex)
    val hull = lower + upper
    if (hull.size < 3) return 0.0
    var twiceArea = 0.0
    hull.indices.forEach { index ->
        val current = hull[index]
        val next = hull[(index + 1) % hull.size]
        twiceArea += current.x * next.y - current.y * next.x
    }
    return abs(twiceArea) / 2.0
}

private const val AutoOrientationUnsupportedWeight = 0.70
private const val AutoOrientationContactWeight = 0.20
private const val AutoOrientationHeightWeight = 0.10

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

    fun preRotateZDegrees(degrees: Double): Matrix3 =
        fromEulerDegrees(0.0, 0.0, degrees).multiply(this)

    fun isApproximatelyEqualTo(other: Matrix3, tolerance: Double = 1e-10): Boolean =
        entries().zip(other.entries()).all { (first, second) -> abs(first - second) <= tolerance }

    fun rotationDistanceRadians(): Double =
        acos(((m00 + m11 + m22 - 1.0) / 2.0).coerceIn(-1.0, 1.0))

    private fun multiply(right: Matrix3): Matrix3 = Matrix3(
        m00 = m00 * right.m00 + m01 * right.m10 + m02 * right.m20,
        m01 = m00 * right.m01 + m01 * right.m11 + m02 * right.m21,
        m02 = m00 * right.m02 + m01 * right.m12 + m02 * right.m22,
        m10 = m10 * right.m00 + m11 * right.m10 + m12 * right.m20,
        m11 = m10 * right.m01 + m11 * right.m11 + m12 * right.m21,
        m12 = m10 * right.m02 + m11 * right.m12 + m12 * right.m22,
        m20 = m20 * right.m00 + m21 * right.m10 + m22 * right.m20,
        m21 = m20 * right.m01 + m21 * right.m11 + m22 * right.m21,
        m22 = m20 * right.m02 + m21 * right.m12 + m22 * right.m22,
    )

    private fun entries(): List<Double> = listOf(
        m00, m01, m02,
        m10, m11, m12,
        m20, m21, m22,
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

private fun Double.zeroIfTiny(): Double = if (abs(this) <= 1e-10) 0.0 else this
