// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.ui

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class UiLocalizationTest {
    @Test
    fun russianLocaleKeepsRussianInterface() {
        assertEquals(UiLanguage.RUSSIAN, resolveUiLanguage(Locale.forLanguageTag("ru-RU")))
        assertEquals("Настройки приложения", localizeUiText("Настройки приложения", UiLanguage.RUSSIAN))
    }

    @Test
    fun nonRussianLocaleFallsBackToEnglish() {
        assertEquals(UiLanguage.ENGLISH, resolveUiLanguage(Locale.forLanguageTag("en-US")))
        assertEquals(UiLanguage.ENGLISH, resolveUiLanguage(Locale.forLanguageTag("ka-GE")))
        assertEquals("App settings", localizeUiText("Настройки приложения", UiLanguage.ENGLISH))
        assertEquals("English", localizeUiText("Русский", UiLanguage.ENGLISH))
    }

    @Test
    fun dynamicInterfaceTextIsTranslated() {
        val translated = localizeUiText(
            "Слои 1–24 из 24 · Z 4.80 мм",
            UiLanguage.ENGLISH,
        )
        assertEquals("Layers 1–24 of 24 · Z 4.80 mm", translated)
        assertFalse(localizeUiText("Размер: 281.1 КБ", UiLanguage.ENGLISH).contains("Размер"))
    }
}
