// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.ui

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import tech.g24.feresaslicer.auth.OrcaProfileType

@RunWith(AndroidJUnit4::class)
class SlicerSettingsStoreInstrumentedTest {
    @Test
    fun settingsAreEncryptedAndRestoredByANewStoreInstance() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferencesName = "feresa_slicer_settings_test"
        val settingsKey = "encrypted_slicer_settings_test"
        val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val marker = "persisted-settings-marker"
        val original = PersistedSlicerSettings(
            printSettings = PrintSettingsState(
                layerHeight = "0.31",
                wallLoops = "7",
                filenameFormat = "$marker.gcode",
            ),
            dirtyProcessSettingKeys = setOf("layer_height", "wall_loops", "filename_format"),
            filamentProfileName = "Saved PETG",
            filamentProfileRef = PersistedProfileRef(
                origin = PersistedProfileOrigin.CLOUD,
                type = OrcaProfileType.FILAMENT,
                id = "petg-profile-id",
                name = "Saved PETG",
                accountId = "account-1",
            ),
        )

        try {
            SlicerSettingsStore(
                context = context,
                preferencesName = preferencesName,
                settingsKey = settingsKey,
                keyAlias = "feresa_slicer_settings_instrumented_test",
            ).write(original)

            val encryptedEnvelope = preferences.getString(settingsKey, null)
            assertNotNull(encryptedEnvelope)
            assertFalse(encryptedEnvelope.orEmpty().contains(marker))
            assertFalse(encryptedEnvelope.orEmpty().contains("0.31"))

            val restored = SlicerSettingsStore(
                context = context,
                preferencesName = preferencesName,
                settingsKey = settingsKey,
                keyAlias = "feresa_slicer_settings_instrumented_test",
            ).read()
            assertEquals(original, restored)
        } finally {
            preferences.edit().clear().commit()
        }
    }
}
