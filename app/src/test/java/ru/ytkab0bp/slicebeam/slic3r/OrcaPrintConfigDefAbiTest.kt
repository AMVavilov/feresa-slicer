// SPDX-License-Identifier: AGPL-3.0-only
package ru.ytkab0bp.slicebeam.slic3r

import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrcaPrintConfigDefAbiTest {
    @Test
    fun `get print config definition descriptor matches release 0_4_6`() {
        val method = Native::class.java.getDeclaredMethod(
            "get_print_config_def",
            PrintConfigDef::class.java,
        )

        assertEquals(java.lang.Void.TYPE, method.returnType)
        assertTrue(Modifier.isPublic(method.modifiers))
        assertTrue(Modifier.isStatic(method.modifiers))
        assertTrue(Modifier.isNative(method.modifiers))
    }

    @Test
    fun `native callbacks on PrintConfigDef keep exact descriptors`() {
        assertEquals(
            java.lang.Void.TYPE,
            PrintConfigDef::class.java.getDeclaredMethod(
                "addOption",
                String::class.java,
                ConfigOptionDef::class.java,
            ).returnType,
        )
        assertEquals(
            Any::class.java,
            PrintConfigDef::class.java.getDeclaredMethod(
                "resolveEnum",
                String::class.java,
                String::class.java,
            ).returnType,
        )
    }

    @Test
    fun `ConfigOptionDef exposes every field resolved by JNI`() {
        val expected = linkedMapOf(
            "key" to String::class.java,
            "type" to ConfigOptionDef.ConfigOptionType::class.java,
            "guiType" to ConfigOptionDef.GUIType::class.java,
            "label" to String::class.java,
            "fullLabel" to String::class.java,
            "printerTechnology" to ConfigOptionDef.PrinterTechnology::class.java,
            "category" to String::class.java,
            "tooltip" to String::class.java,
            "sidetext" to String::class.java,
            "multiline" to java.lang.Boolean.TYPE,
            "fullWidth" to java.lang.Boolean.TYPE,
            "readonly" to java.lang.Boolean.TYPE,
            "height" to java.lang.Integer.TYPE,
            "width" to java.lang.Integer.TYPE,
            "min" to java.lang.Float.TYPE,
            "max" to java.lang.Float.TYPE,
            "mode" to ConfigOptionDef.ConfigOptionMode::class.java,
            "defaultValue" to String::class.java,
            "enumLabels" to Array<String>::class.java,
            "enumValues" to Array<String>::class.java,
        )

        expected.forEach { (name, type) ->
            assertEquals(name, type, ConfigOptionDef::class.java.getField(name).type)
        }
        ConfigOptionDef::class.java.getDeclaredConstructor().newInstance()
    }

    @Test
    fun `enum resolver accepts slash-separated JNI class names`() {
        assertEquals(
            ConfigOptionDef.ConfigOptionType.FLOAT_OR_PERCENT,
            PrintConfigDef.resolveEnum(
                "ru/ytkab0bp/slicebeam/slic3r/ConfigOptionDef\$ConfigOptionType",
                "FLOAT_OR_PERCENT",
            ),
        )
    }
}
