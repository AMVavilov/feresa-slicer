// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.slicer

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StlPlateComposerTest {
    @Test
    fun composesTwoPlacedAsciiModelsIntoBinaryPlate() {
        val directory = Files.createTempDirectory("feresa-stl-plate").toFile()
        val first = asciiTriangle(directory, "first.stl", size = 10.0, height = 2.0)
        val second = asciiTriangle(directory, "second.stl", size = 4.0, height = 1.0)
        val output = File(directory, "plate.stl")

        val result = StlPlateComposer.compose(
            listOf(
                StlPlatePlacement(first, positionXmm = 20.0, positionYmm = 30.0),
                StlPlatePlacement(second, positionXmm = 80.0, positionYmm = 90.0, scale = 2.0),
            ),
            output,
        )

        assertTrue(output.isFile)
        assertEquals(2L, result.triangleCount)
        assertEquals(15.0, result.bounds.minimumX, 0.0001)
        assertEquals(84.0, result.bounds.maximumX, 0.0001)
        assertEquals(25.0, result.bounds.minimumY, 0.0001)
        assertEquals(94.0, result.bounds.maximumY, 0.0001)
        assertEquals(2.0, result.bounds.maximumZ, 0.0001)

        val inspected = StlPlateComposer.inspect(output)
        assertEquals(2L, inspected.triangleCount)
        assertEquals(result.bounds, inspected.bounds)
        assertTrue(result.bounds.isInsideBed(100.0, 100.0))
        assertFalse(result.bounds.isInsideBed(70.0, 70.0))
    }

    @Test
    fun rotationAndScaleUseObjectCenterAndBedCoordinates() {
        val directory = Files.createTempDirectory("feresa-stl-transform").toFile()
        val model = asciiTriangle(directory, "model.stl", size = 10.0, height = 3.0)
        val info = StlPlateComposer.inspect(model)
        val bounds = StlPlateComposer.placedBounds(
            info,
            StlPlatePlacement(
                file = model,
                positionXmm = 50.0,
                positionYmm = 60.0,
                rotationDegrees = 90.0,
                scale = 2.0,
            ),
        )

        assertEquals(40.0, bounds.minimumX, 0.0001)
        assertEquals(60.0, bounds.maximumX, 0.0001)
        assertEquals(50.0, bounds.minimumY, 0.0001)
        assertEquals(70.0, bounds.maximumY, 0.0001)
        assertEquals(6.0, bounds.maximumZ, 0.0001)
    }

    @Test
    fun xyzRotationAndNonUniformScaleAreAppliedToComposedVertices() {
        val directory = Files.createTempDirectory("feresa-stl-xyz-transform").toFile()
        val model = asciiTriangle(directory, "model.stl", size = 10.0, height = 3.0)
        val output = File(directory, "transformed.stl")
        val placement = StlPlatePlacement(
            file = model,
            positionXmm = 50.0,
            positionYmm = 60.0,
            positionZmm = 7.0,
            rotationXDegrees = 90.0,
            scaleX = 2.0,
            scaleY = 3.0,
            scaleZ = 4.0,
        )

        val conservativeBounds = StlPlateComposer.placedBounds(StlPlateComposer.inspect(model), placement)
        val exact = StlPlateComposer.compose(listOf(placement), output).bounds

        assertEquals(40.0, conservativeBounds.minimumX, 0.0001)
        assertEquals(60.0, conservativeBounds.maximumX, 0.0001)
        assertEquals(48.0, conservativeBounds.minimumY, 0.0001)
        assertEquals(60.0, conservativeBounds.maximumY, 0.0001)
        assertEquals(-8.0, conservativeBounds.minimumZ, 0.0001)
        assertEquals(22.0, conservativeBounds.maximumZ, 0.0001)
        assertEquals(40.0, exact.minimumX, 0.0001)
        assertEquals(60.0, exact.maximumX, 0.0001)
        assertEquals(48.0, exact.minimumY, 0.0001)
        assertEquals(60.0, exact.maximumY, 0.0001)
        assertEquals(-8.0, exact.minimumZ, 0.0001)
        assertEquals(22.0, exact.maximumZ, 0.0001)
    }

    @Test
    fun exactPlacedBoundsUsesMeshVerticesAndPreservesAbsoluteZ() {
        val directory = Files.createTempDirectory("feresa-stl-exact-bounds").toFile()
        val model = File(directory, "asymmetric.stl").apply {
            writeText(
                """
                solid asymmetric
                  facet normal 0 0 1
                    outer loop
                      vertex 0 0 0
                      vertex 10 0 10
                      vertex 0 10 10
                    endloop
                  endfacet
                endsolid asymmetric
                """.trimIndent(),
            )
        }
        val placement = StlPlatePlacement(
            file = model,
            positionXmm = 50.0,
            positionYmm = 50.0,
            rotationYDegrees = 45.0,
        )

        val conservative = StlPlateComposer.placedBounds(StlPlateComposer.inspect(model), placement)
        val exact = StlPlateComposer.exactPlacedBounds(placement)
        val raised = StlPlateComposer.exactPlacedBounds(placement.copy(positionZmm = 10.0))
        val onBed = StlPlateComposer.exactPlacedBounds(
            placement.copy(positionZmm = placement.positionZmm - exact.minimumZ),
        )

        assertEquals(-3.535534, conservative.minimumZ, 0.0001)
        assertEquals(3.535534, exact.minimumZ, 0.0001)
        assertEquals(13.535534, raised.minimumZ, 0.0001)
        assertEquals(0.0, onBed.minimumZ, 0.0001)
    }

    @Test
    fun layFlatSuggestionMakesLargestSlopedFaceHorizontalAndPlacesItOnBed() {
        val directory = Files.createTempDirectory("feresa-stl-lay-flat").toFile()
        val model = asciiTriangle(directory, "sloped.stl", size = 10.0, height = 10.0)
        val orientation = requireNotNull(StlPlateComposer.suggestLayFlat(model))
        val output = File(directory, "flat.stl")

        val result = StlPlateComposer.compose(
            listOf(
                StlPlatePlacement(
                    file = model,
                    positionXmm = 50.0,
                    positionYmm = 50.0,
                    positionZmm = orientation.positionZmm,
                    rotationXDegrees = orientation.rotationXDegrees,
                    rotationYDegrees = orientation.rotationYDegrees,
                    rotationDegrees = orientation.rotationZDegrees,
                ),
            ),
            output,
        )

        assertEquals(0.0, result.bounds.minimumZ, 0.0001)
        assertEquals(0.0, result.bounds.height, 0.0001)
        assertEquals(70.710678, orientation.supportingFaceAreaMm2, 0.0001)
    }

    private fun asciiTriangle(directory: File, name: String, size: Double, height: Double): File =
        File(directory, name).apply {
            writeText(
                """
                solid model
                  facet normal 0 0 1
                    outer loop
                      vertex 0 0 0
                      vertex $size 0 0
                      vertex 0 $size $height
                    endloop
                  endfacet
                endsolid model
                """.trimIndent(),
            )
        }
}
