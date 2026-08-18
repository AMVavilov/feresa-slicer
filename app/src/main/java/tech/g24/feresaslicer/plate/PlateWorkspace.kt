// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.plate

import java.io.File
import java.util.UUID
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Stable identity of one placed object, independent from its list position or source file. */
@JvmInline
value class PlateObjectId(val value: String) {
    init {
        require(value.isNotBlank()) { "Plate object id must not be blank" }
    }

    override fun toString(): String = value

    companion object {
        fun newId(): PlateObjectId = PlateObjectId(UUID.randomUUID().toString())
    }
}

/** Axis-aligned bounds in millimetres in either source-local or plate coordinates. */
data class PlateBounds(
    val minimumX: Double,
    val minimumY: Double,
    val minimumZ: Double,
    val maximumX: Double,
    val maximumY: Double,
    val maximumZ: Double,
) {
    init {
        listOf(minimumX, minimumY, minimumZ, maximumX, maximumY, maximumZ).forEach {
            require(it.isFinite()) { "Plate bounds must contain only finite values" }
        }
        require(minimumX <= maximumX) { "minimumX must not exceed maximumX" }
        require(minimumY <= maximumY) { "minimumY must not exceed maximumY" }
        require(minimumZ <= maximumZ) { "minimumZ must not exceed maximumZ" }
    }

    val width: Double get() = maximumX - minimumX
    val depth: Double get() = maximumY - minimumY
    val height: Double get() = maximumZ - minimumZ
    val centerX: Double get() = (minimumX + maximumX) / 2.0
    val centerY: Double get() = (minimumY + maximumY) / 2.0
    val centerZ: Double get() = (minimumZ + maximumZ) / 2.0

    fun union(other: PlateBounds): PlateBounds = PlateBounds(
        minimumX = min(minimumX, other.minimumX),
        minimumY = min(minimumY, other.minimumY),
        minimumZ = min(minimumZ, other.minimumZ),
        maximumX = max(maximumX, other.maximumX),
        maximumY = max(maximumY, other.maximumY),
        maximumZ = max(maximumZ, other.maximumZ),
    )

    /**
     * Applies the same S -> Rx -> Ry -> Rz -> T transform convention as the native STL composer.
     * Rotation is around the source coordinate origin; importers normalize STL bounds around
     * their XY centre and put their lowest source point on local Z=0.
     *
     * Transforming all eight corners gives a conservative world-space AABB. The result is exact
     * for an axis-aligned source box, but can be larger than the actual rotated mesh.
     */
    fun transformedBy(transform: PlateObjectTransform): PlateBounds {
        val rotation = PlateRotation.from(transform)
        val xValues = doubleArrayOf(minimumX, maximumX)
        val yValues = doubleArrayOf(minimumY, maximumY)
        val zValues = doubleArrayOf(minimumZ, maximumZ)
        var transformedMinimumX = Double.POSITIVE_INFINITY
        var transformedMinimumY = Double.POSITIVE_INFINITY
        var transformedMinimumZ = Double.POSITIVE_INFINITY
        var transformedMaximumX = Double.NEGATIVE_INFINITY
        var transformedMaximumY = Double.NEGATIVE_INFINITY
        var transformedMaximumZ = Double.NEGATIVE_INFINITY
        xValues.forEach { sourceX ->
            yValues.forEach { sourceY ->
                zValues.forEach { sourceZ ->
                    val rotated = rotation.apply(
                        x = sourceX * transform.effectiveScaleX,
                        y = sourceY * transform.effectiveScaleY,
                        z = sourceZ * transform.effectiveScaleZ,
                    )
                    val x = rotated.x + transform.positionXmm
                    val y = rotated.y + transform.positionYmm
                    val z = rotated.z + transform.positionZmm
                    transformedMinimumX = min(transformedMinimumX, x)
                    transformedMinimumY = min(transformedMinimumY, y)
                    transformedMinimumZ = min(transformedMinimumZ, z)
                    transformedMaximumX = max(transformedMaximumX, x)
                    transformedMaximumY = max(transformedMaximumY, y)
                    transformedMaximumZ = max(transformedMaximumZ, z)
                }
            }
        }
        return PlateBounds(
            minimumX = transformedMinimumX,
            minimumY = transformedMinimumY,
            minimumZ = transformedMinimumZ,
            maximumX = transformedMaximumX,
            maximumY = transformedMaximumY,
            maximumZ = transformedMaximumZ,
        )
    }
}

/** Immutable source metadata and untransformed geometry bounds for one imported model. */
data class PlateModelSource(
    val file: File,
    val displayName: String = file.name,
    val localBounds: PlateBounds,
    val sourceFormat: String = "STL",
    val triangleCount: Long? = null,
    val originalSizeBytes: Long? = null,
) {
    init {
        require(file.path.isNotBlank()) { "Model source path must not be blank" }
        require(displayName.isNotBlank()) { "Model display name must not be blank" }
        require(sourceFormat.isNotBlank()) { "Model source format must not be blank" }
        require(triangleCount == null || triangleCount > 0L) {
            "Model triangle count must be positive when known"
        }
        require(originalSizeBytes == null || originalSizeBytes >= 0L) {
            "Model source size must be non-negative when known"
        }
    }

    val filePath: String get() = file.absolutePath
    val sourceName: String get() = displayName
}

/**
 * Per-object plate transform.
 *
 * [rotationDegrees] and [scale] intentionally remain the legacy Z-rotation and uniform scale
 * properties so existing saved state and call sites keep working. The effective axis scale is
 * `scale * scaleAxis`; new UI should expose [rotationXDegrees], [rotationYDegrees],
 * [rotationZDegrees], and [effectiveScaleX]/Y/Z.
 */
data class PlateObjectTransform(
    val positionXmm: Double = 0.0,
    val positionYmm: Double = 0.0,
    val positionZmm: Double = 0.0,
    val rotationDegrees: Double = 0.0,
    val scale: Double = 1.0,
    val rotationXDegrees: Double = 0.0,
    val rotationYDegrees: Double = 0.0,
    val scaleX: Double = 1.0,
    val scaleY: Double = 1.0,
    val scaleZ: Double = 1.0,
) {
    init {
        require(positionXmm.isFinite()) { "positionXmm must be finite" }
        require(positionYmm.isFinite()) { "positionYmm must be finite" }
        require(positionZmm.isFinite()) { "positionZmm must be finite" }
        require(rotationDegrees.isFinite()) { "rotationDegrees must be finite" }
        require(rotationXDegrees.isFinite()) { "rotationXDegrees must be finite" }
        require(rotationYDegrees.isFinite()) { "rotationYDegrees must be finite" }
        require(scale.isFinite() && scale > 0.0) { "scale must be finite and greater than zero" }
        require(scaleX.isFinite() && scaleX > 0.0) { "scaleX must be finite and greater than zero" }
        require(scaleY.isFinite() && scaleY > 0.0) { "scaleY must be finite and greater than zero" }
        require(scaleZ.isFinite() && scaleZ > 0.0) { "scaleZ must be finite and greater than zero" }
    }

    /** Canonical Z-axis rotation. Retains [rotationDegrees] as its serialized backing field. */
    val rotationZDegrees: Double get() = rotationDegrees

    val effectiveScaleX: Double get() = scale * scaleX
    val effectiveScaleY: Double get() = scale * scaleY
    val effectiveScaleZ: Double get() = scale * scaleZ
}

enum class PlateAxis { X, Y, Z }

data class PlateObject(
    val id: PlateObjectId,
    val source: PlateModelSource,
    val transform: PlateObjectTransform = PlateObjectTransform(),
) {
    val filePath: String get() = source.filePath
    val sourceName: String get() = source.sourceName
    val sourceBounds: PlateBounds get() = source.localBounds
    val plateBounds: PlateBounds get() = source.localBounds.transformedBy(transform)
}

/** Rectangular Orca build volume with its origin at the front-left-bottom corner. */
data class RectangularBuildVolume(
    val widthMm: Double,
    val depthMm: Double,
    val heightMm: Double,
) {
    init {
        require(widthMm.isFinite() && widthMm > 0.0) { "Build volume width must be positive" }
        require(depthMm.isFinite() && depthMm > 0.0) { "Build volume depth must be positive" }
        require(heightMm.isFinite() && heightMm > 0.0) { "Build volume height must be positive" }
    }

    val centerX: Double get() = widthMm / 2.0
    val centerY: Double get() = depthMm / 2.0
}

enum class BuildVolumeViolation {
    LEFT,
    RIGHT,
    FRONT,
    BACK,
    BELOW_BED,
    ABOVE_MAXIMUM_HEIGHT,
}

data class ObjectBedValidation(
    val objectId: PlateObjectId,
    val bounds: PlateBounds,
    val violations: Set<BuildVolumeViolation>,
) {
    val insideBuildVolume: Boolean get() = violations.isEmpty()
}

data class PlateBedValidation(
    val aggregateBounds: PlateBounds?,
    val objects: List<ObjectBedValidation>,
) {
    val insideBuildVolume: Boolean get() = objects.all(ObjectBedValidation::insideBuildVolume)

    fun objectResult(id: PlateObjectId): ObjectBedValidation? = objects.firstOrNull {
        it.objectId == id
    }
}

/** Conservative axis-aligned overlap between two placed objects. */
data class PlateCollision(
    val firstObjectId: PlateObjectId,
    val secondObjectId: PlateObjectId,
    val firstBounds: PlateBounds,
    val secondBounds: PlateBounds,
)

/** Result of deterministic shelf packing. Unplaced objects keep their previous transforms. */
data class PlateAutoArrangeResult(
    val workspace: PlateWorkspace,
    val unplacedObjectIds: List<PlateObjectId>,
) {
    val allPlaced: Boolean get() = unplacedObjectIds.isEmpty()
}

/**
 * Immutable domain state for all objects on one plate. Every mutation returns a new workspace,
 * while [PlateObjectId] remains stable across reorder, selection, and transform changes.
 */
class PlateWorkspace private constructor(
    objects: List<PlateObject>,
    val selectedObjectId: PlateObjectId?,
) {
    val objects: List<PlateObject> = objects.toList()

    init {
        require(this.objects.map(PlateObject::id).distinct().size == this.objects.size) {
            "Plate object ids must be unique"
        }
        require(selectedObjectId == null || this.objects.any { it.id == selectedObjectId }) {
            "Selected object must exist on the plate"
        }
    }

    val selectedObject: PlateObject? get() = selectedObjectId?.let(::objectOrNull)
    val aggregateBounds: PlateBounds? get() = objects
        .map(PlateObject::plateBounds)
        .reduceOrNull(PlateBounds::union)

    fun objectOrNull(id: PlateObjectId): PlateObject? = objects.firstOrNull { it.id == id }

    fun add(
        source: PlateModelSource,
        id: PlateObjectId = PlateObjectId.newId(),
        transform: PlateObjectTransform = PlateObjectTransform(),
        select: Boolean = true,
    ): PlateWorkspace = add(PlateObject(id, source, transform), select)

    fun add(model: PlateObject, select: Boolean = true): PlateWorkspace {
        require(objectOrNull(model.id) == null) { "Plate object id '${model.id}' already exists" }
        return PlateWorkspace(
            objects = objects + model,
            selectedObjectId = if (select) model.id else selectedObjectId,
        )
    }

    fun addCentered(
        source: PlateModelSource,
        buildVolume: RectangularBuildVolume,
        id: PlateObjectId = PlateObjectId.newId(),
        transform: PlateObjectTransform = PlateObjectTransform(),
        select: Boolean = true,
    ): PlateWorkspace = add(source, id, transform, select).center(id, buildVolume)

    fun remove(id: PlateObjectId): PlateWorkspace {
        val removedIndex = indexOf(id)
        val remaining = objects.toMutableList().also { it.removeAt(removedIndex) }
        val nextSelection = if (selectedObjectId == id) {
            remaining.getOrNull(removedIndex.coerceAtMost(remaining.lastIndex))?.id
        } else {
            selectedObjectId
        }
        return PlateWorkspace(remaining, nextSelection)
    }

    fun select(id: PlateObjectId?): PlateWorkspace {
        require(id == null || objectOrNull(id) != null) { "Cannot select unknown plate object '$id'" }
        return if (id == selectedObjectId) this else PlateWorkspace(objects, id)
    }

    fun update(id: PlateObjectId, update: (PlateObject) -> PlateObject): PlateWorkspace {
        val index = indexOf(id)
        val updated = update(objects[index])
        require(updated.id == id) { "Updating a plate object must preserve its stable id" }
        return PlateWorkspace(objects.toMutableList().also { it[index] = updated }, selectedObjectId)
    }

    fun updateTransform(
        id: PlateObjectId,
        update: (PlateObjectTransform) -> PlateObjectTransform,
    ): PlateWorkspace = update(id) { model -> model.copy(transform = update(model.transform)) }

    /** Renames only this placed object; other objects may continue sharing the same backing file. */
    fun rename(id: PlateObjectId, displayName: String): PlateWorkspace {
        val normalized = displayName.trim()
        require(normalized.isNotEmpty()) { "Model display name must not be blank" }
        return update(id) { model -> model.copy(source = model.source.copy(displayName = normalized)) }
    }

    /**
     * Duplicates an object with a fresh stable id and an independent display name. Geometry bytes
     * are intentionally shared through the immutable source [File].
     */
    fun duplicate(
        id: PlateObjectId,
        newId: PlateObjectId = PlateObjectId.newId(),
        displayName: String? = null,
        offsetXmm: Double = 6.0,
        offsetYmm: Double = 6.0,
        select: Boolean = true,
    ): PlateWorkspace {
        require(offsetXmm.isFinite() && offsetYmm.isFinite()) { "Duplicate offset must be finite" }
        val original = objectOrThrow(id)
        val duplicateName = displayName?.trim()?.also {
            require(it.isNotEmpty()) { "Model display name must not be blank" }
        } ?: nextCopyName(original.source.displayName)
        return add(
            model = original.copy(
                id = newId,
                source = original.source.copy(displayName = duplicateName),
                transform = original.transform.copy(
                    positionXmm = original.transform.positionXmm + offsetXmm,
                    positionYmm = original.transform.positionYmm + offsetYmm,
                ),
            ),
            select = select,
        )
    }

    /** Centres the object's current transformed bounds in XY without changing its Z placement. */
    fun center(id: PlateObjectId, buildVolume: RectangularBuildVolume): PlateWorkspace =
        updateTransform(id) { transform ->
            val bounds = objectOrThrow(id).source.localBounds.transformedBy(transform)
            transform.copy(
                positionXmm = transform.positionXmm + buildVolume.centerX - bounds.centerX,
                positionYmm = transform.positionYmm + buildVolume.centerY - bounds.centerY,
            )
        }

    /** Adds a counter-clockwise Z rotation and normalizes its value to [0, 360). */
    fun rotate(id: PlateObjectId, deltaDegrees: Double): PlateWorkspace {
        require(deltaDegrees.isFinite()) { "Rotation delta must be finite" }
        return updateTransform(id) { transform ->
            transform.copy(rotationDegrees = (transform.rotationDegrees + deltaDegrees).normalizedDegrees())
        }
    }

    /** Adds a rotation around [axis], normalizing the resulting value to [0, 360). */
    fun rotate(id: PlateObjectId, axis: PlateAxis, deltaDegrees: Double): PlateWorkspace {
        require(deltaDegrees.isFinite()) { "Rotation delta must be finite" }
        return updateTransform(id) { transform ->
            when (axis) {
                PlateAxis.X -> transform.copy(
                    rotationXDegrees = (transform.rotationXDegrees + deltaDegrees).normalizedDegrees(),
                )
                PlateAxis.Y -> transform.copy(
                    rotationYDegrees = (transform.rotationYDegrees + deltaDegrees).normalizedDegrees(),
                )
                PlateAxis.Z -> transform.copy(
                    rotationDegrees = (transform.rotationDegrees + deltaDegrees).normalizedDegrees(),
                )
            }
        }
    }

    fun setRotation(id: PlateObjectId, axis: PlateAxis, degrees: Double): PlateWorkspace {
        require(degrees.isFinite()) { "Rotation must be finite" }
        return updateTransform(id) { transform ->
            when (axis) {
                PlateAxis.X -> transform.copy(rotationXDegrees = degrees.normalizedDegrees())
                PlateAxis.Y -> transform.copy(rotationYDegrees = degrees.normalizedDegrees())
                PlateAxis.Z -> transform.copy(rotationDegrees = degrees.normalizedDegrees())
            }
        }
    }

    /** Multiplies the object's current uniform scale by [factor]. */
    fun scale(id: PlateObjectId, factor: Double): PlateWorkspace {
        require(factor.isFinite() && factor > 0.0) { "Scale factor must be positive" }
        return updateTransform(id) { transform -> transform.copy(scale = transform.scale * factor) }
    }

    fun setScale(id: PlateObjectId, scale: Double): PlateWorkspace =
        updateTransform(id) { transform -> transform.copy(scale = scale) }

    /** Sets the effective (uniform multiplier included) scale for one axis. */
    fun setAxisScale(id: PlateObjectId, axis: PlateAxis, effectiveScale: Double): PlateWorkspace {
        require(effectiveScale.isFinite() && effectiveScale > 0.0) {
            "Axis scale must be finite and greater than zero"
        }
        return updateTransform(id) { transform ->
            val factor = effectiveScale / transform.scale
            when (axis) {
                PlateAxis.X -> transform.copy(scaleX = factor)
                PlateAxis.Y -> transform.copy(scaleY = factor)
                PlateAxis.Z -> transform.copy(scaleZ = factor)
            }
        }
    }

    /** Moves the lowest corner of the current effective XYZ-transformed bounds onto Z=0. */
    fun moveToBed(id: PlateObjectId): PlateWorkspace = updateTransform(id) { transform ->
        val bounds = objectOrThrow(id).source.localBounds.transformedBy(transform)
        transform.copy(positionZmm = transform.positionZmm - bounds.minimumZ)
    }

    /** Reports deterministic pairwise AABB collisions in object-list order. */
    fun collisions(
        clearanceMm: Double = 0.0,
        includeZ: Boolean = true,
    ): List<PlateCollision> {
        require(clearanceMm.isFinite() && clearanceMm >= 0.0) {
            "Collision clearance must be finite and non-negative"
        }
        return buildList {
            objects.indices.forEach { firstIndex ->
                val first = objects[firstIndex]
                for (secondIndex in firstIndex + 1 until objects.size) {
                    val second = objects[secondIndex]
                    if (first.plateBounds.overlaps(second.plateBounds, clearanceMm, includeZ)) {
                        add(
                            PlateCollision(
                                firstObjectId = first.id,
                                secondObjectId = second.id,
                                firstBounds = first.plateBounds,
                                secondBounds = second.plateBounds,
                            ),
                        )
                    }
                }
            }
        }
    }

    /**
     * Deterministically packs transformed AABBs into front-to-back shelves. Objects that cannot
     * fit are returned in [PlateAutoArrangeResult.unplacedObjectIds] and keep their old transform;
     * no object is ever left partially outside the build volume by this operation.
     */
    fun autoArrange(
        buildVolume: RectangularBuildVolume,
        spacingMm: Double = 6.0,
        moveToBed: Boolean = true,
    ): PlateAutoArrangeResult {
        require(spacingMm.isFinite() && spacingMm >= 0.0) {
            "Arrangement spacing must be finite and non-negative"
        }
        data class Candidate(val model: PlateObject, val zeroBounds: PlateBounds)

        val candidates = objects.map { model ->
            val zeroTransform = model.transform.copy(positionXmm = 0.0, positionYmm = 0.0)
            Candidate(model, model.source.localBounds.transformedBy(zeroTransform))
        }.sortedWith(
            compareByDescending<Candidate> { it.zeroBounds.depth }
                .thenByDescending { it.zeroBounds.width }
                .thenBy { it.model.id.value },
        )
        val arranged = mutableMapOf<PlateObjectId, PlateObject>()
        val unplaced = mutableListOf<PlateObjectId>()
        var cursorX = 0.0
        var cursorY = 0.0
        var rowDepth = 0.0

        candidates.forEach { candidate ->
            val width = candidate.zeroBounds.width
            val depth = candidate.zeroBounds.depth
            val height = candidate.zeroBounds.height
            if (width > buildVolume.widthMm + DefaultBedToleranceMm ||
                depth > buildVolume.depthMm + DefaultBedToleranceMm ||
                height > buildVolume.heightMm + DefaultBedToleranceMm
            ) {
                unplaced += candidate.model.id
                return@forEach
            }
            if (cursorX > 0.0 && cursorX + width > buildVolume.widthMm + DefaultBedToleranceMm) {
                cursorX = 0.0
                cursorY += rowDepth + spacingMm
                rowDepth = 0.0
            }
            if (cursorY + depth > buildVolume.depthMm + DefaultBedToleranceMm) {
                unplaced += candidate.model.id
                return@forEach
            }

            var transform = candidate.model.transform.copy(
                positionXmm = cursorX - candidate.zeroBounds.minimumX,
                positionYmm = cursorY - candidate.zeroBounds.minimumY,
            )
            if (moveToBed) {
                val bounds = candidate.model.source.localBounds.transformedBy(transform)
                transform = transform.copy(positionZmm = transform.positionZmm - bounds.minimumZ)
            }
            val placedBounds = candidate.model.source.localBounds.transformedBy(transform)
            val fits = placedBounds.minimumX >= -DefaultBedToleranceMm &&
                placedBounds.maximumX <= buildVolume.widthMm + DefaultBedToleranceMm &&
                placedBounds.minimumY >= -DefaultBedToleranceMm &&
                placedBounds.maximumY <= buildVolume.depthMm + DefaultBedToleranceMm &&
                placedBounds.minimumZ >= -DefaultBedToleranceMm &&
                placedBounds.maximumZ <= buildVolume.heightMm + DefaultBedToleranceMm
            if (!fits) {
                unplaced += candidate.model.id
                return@forEach
            }
            arranged[candidate.model.id] = candidate.model.copy(transform = transform)
            cursorX += width + spacingMm
            rowDepth = max(rowDepth, depth)
        }

        val updatedObjects = objects.map { arranged[it.id] ?: it }
        return PlateAutoArrangeResult(
            workspace = PlateWorkspace(updatedObjects, selectedObjectId),
            unplacedObjectIds = unplaced,
        )
    }

    fun validate(
        buildVolume: RectangularBuildVolume,
        toleranceMm: Double = DefaultBedToleranceMm,
    ): PlateBedValidation {
        require(toleranceMm.isFinite() && toleranceMm >= 0.0) {
            "Bed validation tolerance must be finite and non-negative"
        }
        val results = objects.map { model ->
            val bounds = model.plateBounds
            val violations = buildSet {
                if (bounds.minimumX < -toleranceMm) add(BuildVolumeViolation.LEFT)
                if (bounds.maximumX > buildVolume.widthMm + toleranceMm) add(BuildVolumeViolation.RIGHT)
                if (bounds.minimumY < -toleranceMm) add(BuildVolumeViolation.FRONT)
                if (bounds.maximumY > buildVolume.depthMm + toleranceMm) add(BuildVolumeViolation.BACK)
                if (bounds.minimumZ < -toleranceMm) add(BuildVolumeViolation.BELOW_BED)
                if (bounds.maximumZ > buildVolume.heightMm + toleranceMm) {
                    add(BuildVolumeViolation.ABOVE_MAXIMUM_HEIGHT)
                }
            }
            ObjectBedValidation(model.id, bounds, violations)
        }
        return PlateBedValidation(aggregateBounds, results)
    }

    private fun indexOf(id: PlateObjectId): Int = objects.indexOfFirst { it.id == id }.also {
        require(it >= 0) { "Unknown plate object '$id'" }
    }

    private fun objectOrThrow(id: PlateObjectId): PlateObject =
        objectOrNull(id) ?: throw IllegalArgumentException("Unknown plate object '$id'")

    private fun nextCopyName(originalName: String): String {
        val dot = originalName.lastIndexOf('.')
        val hasExtension = dot > 0 && dot < originalName.lastIndex
        val stem = if (hasExtension) originalName.substring(0, dot) else originalName
        val extension = if (hasExtension) originalName.substring(dot) else ""
        val usedNames = objects.map { it.source.displayName }.toSet()
        var number = 1
        while (true) {
            val suffix = if (number == 1) " копия" else " копия $number"
            val candidate = "$stem$suffix$extension"
            if (candidate !in usedNames) return candidate
            number += 1
        }
    }

    override fun equals(other: Any?): Boolean = other is PlateWorkspace &&
        objects == other.objects && selectedObjectId == other.selectedObjectId

    override fun hashCode(): Int = 31 * objects.hashCode() + (selectedObjectId?.hashCode() ?: 0)

    override fun toString(): String =
        "PlateWorkspace(objects=$objects, selectedObjectId=$selectedObjectId)"

    companion object {
        const val DefaultBedToleranceMm: Double = 0.001

        fun empty(): PlateWorkspace = PlateWorkspace(emptyList(), null)

        fun of(
            objects: List<PlateObject>,
            selectedObjectId: PlateObjectId? = null,
        ): PlateWorkspace = PlateWorkspace(objects, selectedObjectId)
    }
}

private fun Double.normalizedDegrees(): Double {
    val normalized = this % 360.0
    return if (normalized < 0.0) normalized + 360.0 else normalized
}

private fun PlateBounds.overlaps(
    other: PlateBounds,
    clearanceMm: Double,
    includeZ: Boolean,
): Boolean {
    val xyOverlap = maximumX + clearanceMm > other.minimumX &&
        minimumX - clearanceMm < other.maximumX &&
        maximumY + clearanceMm > other.minimumY &&
        minimumY - clearanceMm < other.maximumY
    val zOverlap = !includeZ || (
        maximumZ + clearanceMm > other.minimumZ &&
            minimumZ - clearanceMm < other.maximumZ
        )
    return xyOverlap && zOverlap
}

private data class PlatePoint(val x: Double, val y: Double, val z: Double)

/** Compact precomputed Euler rotation using Rz * Ry * Rx. */
private data class PlateRotation(
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
    fun apply(x: Double, y: Double, z: Double): PlatePoint = PlatePoint(
        x = m00 * x + m01 * y + m02 * z,
        y = m10 * x + m11 * y + m12 * z,
        z = m20 * x + m21 * y + m22 * z,
    )

    companion object {
        fun from(transform: PlateObjectTransform): PlateRotation {
            val x = Math.toRadians(transform.rotationXDegrees.normalizedDegrees())
            val y = Math.toRadians(transform.rotationYDegrees.normalizedDegrees())
            val z = Math.toRadians(transform.rotationZDegrees.normalizedDegrees())
            val cx = cos(x)
            val sx = sin(x)
            val cy = cos(y)
            val sy = sin(y)
            val cz = cos(z)
            val sz = sin(z)
            return PlateRotation(
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
    }
}
