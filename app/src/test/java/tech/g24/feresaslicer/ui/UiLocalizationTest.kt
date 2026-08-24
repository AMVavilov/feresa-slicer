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
        assertEquals("Russian", localizeUiText("Русский", UiLanguage.ENGLISH))
        assertEquals("English", localizeUiText("Английский", UiLanguage.ENGLISH))
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

    @Test
    fun modelPositionStatusAndValidationTextIsFullyLocalized() {
        val insideBed = "Модель находится в пределах стола 220 × 220 мм"
        val bounds = "Границы X -2.5–222.5 · Y 0.0–220.0 · В 18.0 мм"

        assertEquals(
            "Model is within the print bed 220 × 220 mm",
            localizeUiText(insideBed, UiLanguage.ENGLISH),
        )
        assertEquals(
            "Bounds X -2.5–222.5 · Y 0.0–220.0 · H 18.0 mm",
            localizeUiText(bounds, UiLanguage.ENGLISH),
        )
        assertEquals(
            "The model extends beyond the print bed",
            localizeUiText("Модель выходит за пределы печатного стола", UiLanguage.ENGLISH),
        )
        assertEquals(
            "The object is outside the build volume",
            localizeUiText("Объект выходит за пределы области печати", UiLanguage.ENGLISH),
        )
        assertEquals(
            "Bounding-box overlaps with other models: 2",
            localizeUiText(
                "Пересечение габаритов с другими моделями: 2",
                UiLanguage.ENGLISH,
            ),
        )
        assertEquals(insideBed, localizeUiText(insideBed, UiLanguage.RUSSIAN))
        assertEquals(bounds, localizeUiText(bounds, UiLanguage.RUSSIAN))
    }
}
