// SPDX-License-Identifier: AGPL-3.0-only
package ru.ytkab0bp.slicebeam.slic3r;

import androidx.annotation.Keep;

/**
 * Exact Java field ABI consumed by OrcaSlicer Mobile's {@code get_print_config_def} JNI entry
 * point at commit {@code 6fc2e14b9a222301f4432cee26d7ab37d3be86d0}.
 *
 * <p>Native code resolves every field and nested enum by name and descriptor. Do not rename or
 * narrow these fields without rebuilding the pinned native library.</p>
 */
@Keep
public final class ConfigOptionDef {
    public String key;
    public ConfigOptionType type = ConfigOptionType.NONE;
    public GUIType guiType;
    public String label;
    public String fullLabel;
    public PrinterTechnology printerTechnology = PrinterTechnology.UNKNOWN;
    public String category;
    public String tooltip;
    public String sidetext;
    public boolean multiline;
    public boolean fullWidth;
    public boolean readonly;
    public int height = -1;
    public int width = -1;
    public float min = Float.MIN_VALUE;
    public float max = Float.MAX_VALUE;
    public ConfigOptionMode mode = ConfigOptionMode.SIMPLE;
    public String defaultValue;
    public String[] enumLabels;
    public String[] enumValues;

    /** Public for pure JVM contract tests; JNI only requires the exact no-argument descriptor. */
    @Keep
    public ConfigOptionDef() {
    }

    public String getLabel() {
        return label == null || label.isEmpty() ? fullLabel : label;
    }

    public String getFullLabel() {
        return fullLabel == null || fullLabel.isEmpty() ? label : fullLabel;
    }

    public enum ConfigOptionType {
        NONE,
        FLOAT,
        FLOATS(true),
        INT,
        INTS(true),
        STRING,
        STRINGS(true),
        PERCENT,
        PERCENTS(true),
        FLOAT_OR_PERCENT,
        FLOATS_OR_PERCENTS(true),
        POINT,
        POINTS(true),
        POINT3,
        BOOL,
        BOOLS(true),
        ENUM,
        ENUMS;

        public final boolean list;

        ConfigOptionType() {
            this(false);
        }

        ConfigOptionType(boolean list) {
            this.list = list;
        }
    }

    public enum GUIType {
        UNDEFINED,
        I_ENUM_OPEN,
        F_ENUM_OPEN,
        SELECT_OPEN,
        COLOR,
        SLIDER,
        LEGEND,
        ONE_STRING,
        SELECT_CLOSE,
        PASSWORD
    }

    public enum PrinterTechnology {
        FFF,
        SLA,
        UNKNOWN,
        ANY
    }

    public enum ConfigOptionMode {
        SIMPLE,
        ADVANCED,
        EXPERT,
        UNDEFINED
    }
}
