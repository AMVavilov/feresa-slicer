// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.auth

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewerDemoAccessTest {
    @Test
    fun acceptsOnlyDocumentedReviewerCredentials() {
        assertTrue(
            ReviewerDemoAccess.credentialsMatch(
                "play-review@feresa.local",
                "feresa-local-demo",
            ),
        )
        assertTrue(
            ReviewerDemoAccess.credentialsMatch(
                "  play-review@feresa.local  ",
                "feresa-local-demo",
            ),
        )
        assertFalse(ReviewerDemoAccess.credentialsMatch("other@feresa.local", "feresa-local-demo"))
        assertFalse(ReviewerDemoAccess.credentialsMatch("play-review@feresa.local", "wrong"))
    }

    @Test
    fun exposesExactlyOneLocalProfileForEachSupportedRole() {
        val profiles = ReviewerDemoAccess.profiles

        assertEquals(3, profiles.size)
        assertEquals(
            setOf(OrcaProfileType.PRINTER, OrcaProfileType.FILAMENT, OrcaProfileType.PROCESS),
            profiles.map(OrcaCloudProfile::type).toSet(),
        )
        assertEquals(OrcaProfileOrigin.REVIEW_DEMO, ReviewerDemoAccess.syncState().origin)
    }

    @Test
    fun profilesContainNoEndpointOrAuthenticationMaterial() {
        val forbiddenKeys = setOf(
            "print_host",
            "host_type",
            "printhost_apikey",
            "printhost_port",
            "print_host_webui",
            "printhost_user",
            "printhost_password",
        )

        ReviewerDemoAccess.profiles.forEach { profile ->
            val root = JSONObject(profile.contentJson)
            assertTrue(root.keys().asSequence().none(forbiddenKeys::contains))
            assertNull(profile.printerConnection())
        }
    }

    @Test
    fun sampleValuesAreRealOrcaSettingsRatherThanUiPlaceholders() {
        val filament = ReviewerDemoAccess.profiles.single { it.type == OrcaProfileType.FILAMENT }
        val process = ReviewerDemoAccess.profiles.single { it.type == OrcaProfileType.PROCESS }

        assertEquals("1.75", filament.setting("filament_diameter"))
        assertEquals("210", filament.setting("nozzle_temperature"))
        assertEquals("3", process.setting("wall_loops"))
        assertEquals("15%", process.setting("sparse_infill_density"))
    }
}
