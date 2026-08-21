// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.auth

import java.security.MessageDigest

/**
 * Local-only access used by Google Play reviewers.
 *
 * The credentials are intentionally public and are not an authorization boundary. A successful
 * match only exposes bundled sample profiles; it never creates an OrcaCloud session, stores a
 * token, or grants access to user data or a printer endpoint.
 */
internal object ReviewerDemoAccess {
    const val Username = "play-review@feresa.local"
    const val Password = "feresa-local-demo"

    val account = OrcaAccount(
        id = "local-google-play-review",
        email = Username,
        displayName = "Google Play reviewer demo",
    )

    val profiles: List<OrcaCloudProfile> = listOf(
        profile(
            id = "review-demo-printer",
            name = "Demo · Kingroon KP3S 3.0",
            type = OrcaProfileType.PRINTER,
            contentJson = """
                {
                  "name":"Demo · Kingroon KP3S 3.0",
                  "type":"machine",
                  "inherits":"Kingroon KP3S 3.0 0.4 nozzle"
                }
            """.trimIndent(),
        ),
        profile(
            id = "review-demo-filament",
            name = "Demo · Generic PLA",
            type = OrcaProfileType.FILAMENT,
            contentJson = """
                {
                  "name":"Demo · Generic PLA",
                  "type":"filament",
                  "filament_diameter":["1.75"],
                  "nozzle_temperature":["210"],
                  "nozzle_temperature_initial_layer":["215"],
                  "hot_plate_temp":["60"],
                  "hot_plate_temp_initial_layer":["60"]
                }
            """.trimIndent(),
        ),
        profile(
            id = "review-demo-process",
            name = "Demo · 0.20 mm Standard",
            type = OrcaProfileType.PROCESS,
            contentJson = """
                {
                  "name":"Demo · 0.20 mm Standard",
                  "type":"process",
                  "inherits":"0.20mm Standard @Kingroon KP3S 3.0",
                  "layer_height":"0.20",
                  "wall_loops":"3",
                  "sparse_infill_density":"15%",
                  "outer_wall_speed":"45"
                }
            """.trimIndent(),
        ),
    )

    fun credentialsMatch(username: String, password: String): Boolean {
        val usernameMatches = constantTimeEquals(username.trim(), Username)
        val passwordMatches = constantTimeEquals(password, Password)
        return usernameMatches && passwordMatches
    }

    fun syncState(): OrcaProfileSyncState = OrcaProfileSyncState(
        profiles = profiles,
        origin = OrcaProfileOrigin.REVIEW_DEMO,
    )

    private fun profile(
        id: String,
        name: String,
        type: OrcaProfileType,
        contentJson: String,
    ) = OrcaCloudProfile(
        id = id,
        name = name,
        type = type,
        contentJson = contentJson,
        updatedTime = 0L,
    )

    private fun constantTimeEquals(left: String, right: String): Boolean = MessageDigest.isEqual(
        left.toByteArray(Charsets.UTF_8),
        right.toByteArray(Charsets.UTF_8),
    )
}
