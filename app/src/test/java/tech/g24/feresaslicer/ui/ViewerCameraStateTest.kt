// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ViewerCameraStateTest {
    @Test
    fun cameraStateRoundTripsThroughViewerJson() {
        val original = ViewerCameraState(
            position = ViewerCameraVector(18.25, 42.5, -7.75),
            target = ViewerCameraVector(2.0, 3.0, 4.0),
            up = ViewerCameraVector(0.0, 1.0, 0.0),
            fieldOfViewDegrees = 41.5,
            mode = ViewerCameraMode.PRESET,
            preset = CameraViewPreset.FRONT,
            source = "preset",
            interactionActive = false,
        )

        assertEquals(original, parseViewerCameraState(original.toViewerJson().toString()))
    }

    @Test
    fun freeCameraDoesNotRequirePreset() {
        val state = parseViewerCameraState(
            """{
                "position":[1,2,3],
                "target":[0,0,0],
                "up":[0,1,0],
                "fieldOfViewDegrees":38,
                "mode":"free",
                "preset":null,
                "source":"manual",
                "interactionActive":true
            }""".trimIndent(),
        )

        assertEquals(ViewerCameraMode.FREE, state.mode)
        assertEquals(null, state.preset)
        assertEquals("manual", state.source)
        assertEquals(true, state.interactionActive)
    }

    @Test
    fun presetModeRejectsMissingPreset() {
        assertThrows(IllegalArgumentException::class.java) {
            parseViewerCameraState(
                """{
                    "position":[1,2,3],
                    "target":[0,0,0],
                    "up":[0,1,0],
                    "mode":"preset",
                    "preset":null
                }""".trimIndent(),
            )
        }
    }

    @Test
    fun cameraStateRejectsNonFiniteCoordinates() {
        assertThrows(IllegalArgumentException::class.java) {
            parseViewerCameraState(
                """{
                    "position":[1,2,1e400],
                    "target":[0,0,0],
                    "up":[0,1,0],
                    "mode":"free",
                    "preset":null
                }""".trimIndent(),
            )
        }
    }
}
