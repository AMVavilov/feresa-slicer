// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.auth

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import tech.g24.feresaslicer.slicer.OrcaDefaultConfigProvider
import tech.g24.feresaslicer.slicer.OrcaProfileSettingsResolver
import tech.g24.feresaslicer.slicer.OrcaSelectedProfiles
import tech.g24.feresaslicer.slicer.OrcaSystemPresetCatalog

@RunWith(AndroidJUnit4::class)
class ReviewerDemoInstrumentedTest {
    @Test
    fun demoProfilesResolveAgainstBundledOrcaCatalog() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val catalog = OrcaSystemPresetCatalog.load(context)
        val profiles = ReviewerDemoAccess.profiles
        val supportedKeys = OrcaDefaultConfigProvider.fffOptionKeys()

        profiles.forEach { profile ->
            val selection = when (profile.type) {
                OrcaProfileType.PRINTER -> OrcaSelectedProfiles(printer = profile)
                OrcaProfileType.FILAMENT -> OrcaSelectedProfiles(filament = profile)
                OrcaProfileType.PROCESS -> OrcaSelectedProfiles(process = profile)
                OrcaProfileType.OTHER -> error("Unexpected reviewer profile type")
            }.copy(availableCloudProfiles = profiles)
            val hydrated = catalog.augment(selection)
            val resolved = OrcaProfileSettingsResolver.resolve(
                profile = profile,
                availableProfiles = hydrated.availableCloudProfiles,
                supportedKeys = supportedKeys,
            )
            assertTrue("Reviewer profile ${profile.name} resolved no settings", resolved.isNotEmpty())
        }
    }

    @Test
    fun demoSignOutClearsOnlyEphemeralReviewerState() {
        val application = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as Application
        EncryptedRefreshTokenStore(application).clear()
        val viewModel = OrcaAuthViewModel(application)

        viewModel.enterReviewerDemo("play-review@feresa.local", "feresa-local-demo")
        val signedIn = viewModel.state.value as OrcaAuthState.SignedIn
        assertEquals(OrcaAuthMode.REVIEW_DEMO, signedIn.mode)
        assertEquals(OrcaProfileOrigin.REVIEW_DEMO, viewModel.profileState.value.origin)

        viewModel.syncProfiles()
        assertEquals(3, viewModel.profileState.value.profiles.size)

        viewModel.signOut()
        assertEquals(OrcaAuthState.SignedOut, viewModel.state.value)
        assertTrue(viewModel.profileState.value.profiles.isEmpty())
        assertEquals(OrcaProfileOrigin.NONE, viewModel.profileState.value.origin)
    }
}
