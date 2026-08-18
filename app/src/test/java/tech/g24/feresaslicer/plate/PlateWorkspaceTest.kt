// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.plate

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PlateWorkspaceTest {
    @Test
    fun addSelectUpdateAndRemovePreserveStableIdentity() {
        val firstId = PlateObjectId("model-a")
        val secondId = PlateObjectId("model-b")
        val first = source("a.stl")
        val second = source("b.stl")

        val oneObject = PlateWorkspace.empty().add(first, firstId)
        val twoObjects = oneObject.add(second, secondId)
        val moved = twoObjects.updateTransform(firstId) {
            it.copy(positionXmm = 42.0, positionYmm = 21.0)
        }

        assertEquals(firstId, oneObject.selectedObjectId)
        assertEquals(secondId, twoObjects.selectedObjectId)
        assertEquals(firstId, moved.objects.first().id)
        assertSame(first, moved.objects.first().source)
        assertEquals(42.0, moved.objectOrNull(firstId)?.transform?.positionXmm ?: -1.0, Epsilon)

        val selectedFirst = moved.select(firstId)
        val removedFirst = selectedFirst.remove(firstId)
        assertEquals(secondId, removedFirst.selectedObjectId)
        assertEquals(listOf(secondId), removedFirst.objects.map(PlateObject::id))
        assertNull(removedFirst.remove(secondId).selectedObjectId)

        assertThrows(IllegalArgumentException::class.java) {
            twoObjects.add(first, firstId)
        }
        assertThrows(IllegalArgumentException::class.java) {
            twoObjects.select(PlateObjectId("missing"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            twoObjects.update(firstId) { it.copy(id = PlateObjectId("changed")) }
        }
    }

    @Test
    fun centerRotateAndScaleOperateOnOnlyTheRequestedObject() {
        val firstId = PlateObjectId("first")
        val secondId = PlateObjectId("second")
        val volume = RectangularBuildVolume(widthMm = 200.0, depthMm = 100.0, heightMm = 50.0)
        val localBounds = PlateBounds(-10.0, -5.0, 0.0, 10.0, 5.0, 2.0)
        val first = PlateModelSource(File("first.stl"), "First", localBounds)
        val second = PlateModelSource(File("second.stl"), "Second", localBounds)
        val initial = PlateWorkspace.empty()
            .add(first, firstId, select = false)
            .add(second, secondId, PlateObjectTransform(positionXmm = 25.0), select = false)

        val transformed = initial
            .center(firstId, volume)
            .rotate(firstId, 450.0)
            .scale(firstId, 2.0)

        val firstObject = requireNotNull(transformed.objectOrNull(firstId))
        val secondObject = requireNotNull(transformed.objectOrNull(secondId))
        assertEquals(100.0, firstObject.transform.positionXmm, Epsilon)
        assertEquals(50.0, firstObject.transform.positionYmm, Epsilon)
        assertEquals(90.0, firstObject.transform.rotationDegrees, Epsilon)
        assertEquals(2.0, firstObject.transform.scale, Epsilon)
        assertEquals(20.0, firstObject.plateBounds.width, Epsilon)
        assertEquals(40.0, firstObject.plateBounds.depth, Epsilon)
        assertEquals(25.0, secondObject.transform.positionXmm, Epsilon)
        assertEquals(1.0, secondObject.transform.scale, Epsilon)
    }

    @Test
    fun aggregateBoundsAndPerObjectBedViolationsAreCalculated() {
        val insideId = PlateObjectId("inside")
        val outsideId = PlateObjectId("outside")
        val tooTallId = PlateObjectId("too-tall")
        val source = PlateModelSource(
            File("part.stl"),
            "Part",
            PlateBounds(-10.0, -5.0, 0.0, 10.0, 5.0, 4.0),
        )
        val workspace = PlateWorkspace.empty()
            .add(
                source,
                insideId,
                PlateObjectTransform(positionXmm = 20.0, positionYmm = 20.0),
                select = false,
            )
            .add(
                source,
                outsideId,
                PlateObjectTransform(positionXmm = 96.0, positionYmm = 95.0),
                select = false,
            )
            .add(
                source,
                tooTallId,
                PlateObjectTransform(positionXmm = 50.0, positionYmm = 50.0, positionZmm = 8.0),
                select = false,
            )

        val validation = workspace.validate(
            RectangularBuildVolume(widthMm = 100.0, depthMm = 100.0, heightMm = 10.0),
        )

        assertFalse(validation.insideBuildVolume)
        assertTrue(requireNotNull(validation.objectResult(insideId)).insideBuildVolume)
        assertEquals(
            setOf(BuildVolumeViolation.RIGHT),
            requireNotNull(validation.objectResult(outsideId)).violations,
        )
        assertEquals(
            setOf(BuildVolumeViolation.ABOVE_MAXIMUM_HEIGHT),
            requireNotNull(validation.objectResult(tooTallId)).violations,
        )
        val aggregate = requireNotNull(validation.aggregateBounds)
        assertEquals(10.0, aggregate.minimumX, Epsilon)
        assertEquals(106.0, aggregate.maximumX, Epsilon)
        assertEquals(15.0, aggregate.minimumY, Epsilon)
        assertEquals(100.0, aggregate.maximumY, Epsilon)
        assertEquals(12.0, aggregate.maximumZ, Epsilon)
    }

    @Test
    fun emptyPlateIsValidAndHasNoAggregateBounds() {
        val workspace = PlateWorkspace.empty()
        val validation = workspace.validate(RectangularBuildVolume(220.0, 220.0, 250.0))

        assertTrue(validation.insideBuildVolume)
        assertTrue(validation.objects.isEmpty())
        assertNull(validation.aggregateBounds)
        assertNull(workspace.aggregateBounds)
    }

    @Test
    fun xyzRotationAndNonUniformScaleProduceEffectiveWorldBounds() {
        val bounds = PlateBounds(-1.0, -2.0, 0.0, 1.0, 2.0, 3.0)
        val transform = PlateObjectTransform(
            positionXmm = 10.0,
            positionYmm = 20.0,
            positionZmm = 30.0,
            rotationXDegrees = 90.0,
            scaleX = 2.0,
            scaleY = 3.0,
            scaleZ = 4.0,
        )

        val transformed = bounds.transformedBy(transform)

        assertEquals(8.0, transformed.minimumX, Epsilon)
        assertEquals(12.0, transformed.maximumX, Epsilon)
        assertEquals(8.0, transformed.minimumY, Epsilon)
        assertEquals(20.0, transformed.maximumY, Epsilon)
        assertEquals(24.0, transformed.minimumZ, Epsilon)
        assertEquals(36.0, transformed.maximumZ, Epsilon)
        assertEquals(2.0, transform.effectiveScaleX, Epsilon)
        assertEquals(3.0, transform.effectiveScaleY, Epsilon)
        assertEquals(4.0, transform.effectiveScaleZ, Epsilon)
    }

    @Test
    fun duplicateGetsFreshIdentityAndIndependentNameWhileSharingGeometryFile() {
        val source = source("part.stl")
        val originalId = PlateObjectId("original")
        val duplicateId = PlateObjectId("duplicate")
        val initial = PlateWorkspace.empty().add(
            source,
            originalId,
            PlateObjectTransform(positionXmm = 20.0, positionYmm = 30.0),
        )

        val duplicated = initial.duplicate(originalId, newId = duplicateId)
        val renamed = duplicated.rename(duplicateId, "правый элемент.stl")
        val original = requireNotNull(renamed.objectOrNull(originalId))
        val copy = requireNotNull(renamed.objectOrNull(duplicateId))

        assertEquals(duplicateId, renamed.selectedObjectId)
        assertEquals("part.stl", original.source.displayName)
        assertEquals("правый элемент.stl", copy.source.displayName)
        assertSame(original.source.file, copy.source.file)
        assertEquals(26.0, copy.transform.positionXmm, Epsilon)
        assertEquals(36.0, copy.transform.positionYmm, Epsilon)
    }

    @Test
    fun collisionReportUsesClearanceAndCanIncludeOrIgnoreZ() {
        val source = source("part.stl")
        val firstId = PlateObjectId("first")
        val secondId = PlateObjectId("second")
        val workspace = PlateWorkspace.empty()
            .add(
                source,
                firstId,
                PlateObjectTransform(positionXmm = 10.0, positionYmm = 10.0),
                select = false,
            )
            .add(
                source,
                secondId,
                PlateObjectTransform(positionXmm = 21.0, positionYmm = 10.0, positionZmm = 20.0),
                select = false,
            )

        assertTrue(workspace.collisions().isEmpty())
        assertTrue(workspace.collisions(clearanceMm = 2.0).isEmpty())
        val footprintCollision = workspace.collisions(clearanceMm = 2.0, includeZ = false)
        assertEquals(1, footprintCollision.size)
        assertEquals(firstId, footprintCollision.single().firstObjectId)
        assertEquals(secondId, footprintCollision.single().secondObjectId)
    }

    @Test
    fun autoArrangePacksObjectsOnBedAndLeavesUnplaceableObjectsUntouched() {
        val volume = RectangularBuildVolume(100.0, 70.0, 50.0)
        val firstId = PlateObjectId("first")
        val secondId = PlateObjectId("second")
        val hugeId = PlateObjectId("huge")
        val first = PlateModelSource(
            File("first.stl"),
            "First",
            PlateBounds(-20.0, -15.0, 0.0, 20.0, 15.0, 10.0),
        )
        val second = PlateModelSource(
            File("second.stl"),
            "Second",
            PlateBounds(-15.0, -10.0, 0.0, 15.0, 10.0, 8.0),
        )
        val huge = PlateModelSource(
            File("huge.stl"),
            "Huge",
            PlateBounds(-60.0, -10.0, 0.0, 60.0, 10.0, 8.0),
        )
        val hugeTransform = PlateObjectTransform(positionXmm = 200.0, positionYmm = 200.0)
        val workspace = PlateWorkspace.empty()
            .add(first, firstId, PlateObjectTransform(positionZmm = 12.0), select = false)
            .add(second, secondId, PlateObjectTransform(rotationXDegrees = 180.0), select = false)
            .add(huge, hugeId, hugeTransform, select = false)

        val result = workspace.autoArrange(volume, spacingMm = 6.0)

        assertEquals(listOf(hugeId), result.unplacedObjectIds)
        assertFalse(result.allPlaced)
        assertEquals(hugeTransform, requireNotNull(result.workspace.objectOrNull(hugeId)).transform)
        assertTrue(requireNotNull(result.workspace.objectOrNull(firstId)).plateBounds.minimumZ >= -Epsilon)
        assertTrue(requireNotNull(result.workspace.objectOrNull(secondId)).plateBounds.minimumZ >= -Epsilon)
        assertTrue(result.workspace.validate(volume).objectResult(firstId)?.insideBuildVolume == true)
        assertTrue(result.workspace.validate(volume).objectResult(secondId)?.insideBuildVolume == true)
        assertTrue(result.workspace.collisions(clearanceMm = 5.9).none {
            it.firstObjectId != hugeId && it.secondObjectId != hugeId
        })
    }

    @Test
    fun moveToBedUsesCurrentXyzRotationBounds() {
        val id = PlateObjectId("rotated")
        val workspace = PlateWorkspace.empty().add(
            source("rotated.stl"),
            id,
            PlateObjectTransform(rotationXDegrees = 90.0),
        )

        val moved = workspace.moveToBed(id)

        assertEquals(0.0, requireNotNull(moved.objectOrNull(id)).plateBounds.minimumZ, Epsilon)
        assertEquals(5.0, requireNotNull(moved.objectOrNull(id)).transform.positionZmm, Epsilon)
    }

    private fun source(name: String) = PlateModelSource(
        file = File(name),
        displayName = name,
        localBounds = PlateBounds(-5.0, -5.0, 0.0, 5.0, 5.0, 5.0),
    )

    private companion object {
        const val Epsilon = 1e-9
    }
}
