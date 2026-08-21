// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.auth

data class OrcaAccount(
    val id: String,
    val email: String,
    val displayName: String,
)

enum class OrcaAuthProvider(val wireValue: String, val label: String) {
    GOOGLE("google", "Google"),
    GITHUB("github", "GitHub"),
}

enum class OrcaAuthMode {
    CLOUD,
    REVIEW_DEMO,
}

sealed interface OrcaAuthState {
    data object Loading : OrcaAuthState
    data object SignedOut : OrcaAuthState
    data class WaitingForBrowser(val provider: OrcaAuthProvider) : OrcaAuthState
    data class SignedIn(
        val account: OrcaAccount,
        val mode: OrcaAuthMode = OrcaAuthMode.CLOUD,
    ) : OrcaAuthState
    data class Error(val message: String) : OrcaAuthState
}
