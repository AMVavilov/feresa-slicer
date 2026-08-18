// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.slicer

import tech.g24.feresaslicer.auth.OrcaCloudProfile

/** Resolves one Orca preset and its complete `inherits` chain into native serialized values. */
object OrcaProfileSettingsResolver {
    fun resolve(
        profile: OrcaCloudProfile,
        availableProfiles: List<OrcaCloudProfile>,
        supportedKeys: Set<String>,
    ): Map<String, String> {
        require(supportedKeys.isNotEmpty()) { "The Orca engine did not report any supported options" }
        val allProfiles = (availableProfiles + profile).distinctBy(::profileIdentity)
        val resolved = linkedMapOf<String, String>()
        resolveInto(
            target = resolved,
            profile = profile,
            availableProfiles = allProfiles,
            supportedKeys = supportedKeys,
            visiting = linkedSetOf(),
        )
        return resolved.toMap()
    }

    private fun resolveInto(
        target: MutableMap<String, String>,
        profile: OrcaCloudProfile,
        availableProfiles: List<OrcaCloudProfile>,
        supportedKeys: Set<String>,
        visiting: MutableSet<String>,
    ) {
        val identity = profileIdentity(profile)
        require(visiting.add(identity)) {
            "Cyclic Orca profile inheritance detected at '${profile.name}'"
        }
        try {
            profile.inheritedProfileName()?.let { parentName ->
                val candidates = availableProfiles.filter { candidate ->
                    candidate.type == profile.type && candidate.name == parentName
                }
                require(candidates.isNotEmpty()) {
                    "Orca profile '${profile.name}' inherits missing preset '$parentName'"
                }
                require(candidates.size == 1) {
                    "Orca profile '${profile.name}' inherits ambiguous preset '$parentName'"
                }
                resolveInto(
                    target = target,
                    profile = candidates.single(),
                    availableProfiles = availableProfiles,
                    supportedKeys = supportedKeys,
                    visiting = visiting,
                )
            }
            target.putAll(profile.settingsMap(supportedKeys))
        } finally {
            visiting.remove(identity)
        }
    }

    private fun profileIdentity(profile: OrcaCloudProfile): String =
        "${profile.type}:${profile.id.ifBlank { profile.name }}"
}
