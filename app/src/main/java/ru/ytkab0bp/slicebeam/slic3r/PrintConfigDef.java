// SPDX-License-Identifier: AGPL-3.0-only
package ru.ytkab0bp.slicebeam.slic3r;

import androidx.annotation.Keep;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Runtime mirror populated from OrcaSlicer's native {@code PrintConfigDef}. */
@Keep
public final class PrintConfigDef {
    private static volatile PrintConfigDef instance;

    /** Native calls {@link #addOption(String, ConfigOptionDef)}; insertion order is retained. */
    public Map<String, ConfigOptionDef> options = new LinkedHashMap<>();

    @Keep
    public PrintConfigDef() {
    }

    /**
     * Loads definitions once from the pinned native engine. The singleton is published only after
     * JNI has populated it completely, so another thread cannot observe a partial definition map.
     */
    public static PrintConfigDef getInstance() {
        PrintConfigDef cached = instance;
        if (cached != null) {
            return cached;
        }

        synchronized (PrintConfigDef.class) {
            cached = instance;
            if (cached == null) {
                if (!Native.isLoaded()) {
                    throw new IllegalStateException(
                            "OrcaSlicer Mobile native engine is not loaded",
                            Native.getLoadError()
                    );
                }
                PrintConfigDef loaded = new PrintConfigDef();
                Native.get_print_config_def(loaded);
                loaded.options = Collections.unmodifiableMap(
                        new LinkedHashMap<>(loaded.options)
                );
                instance = loaded;
                cached = loaded;
            }
        }
        return cached;
    }

    @Keep
    static Object resolveEnum(String className, String value) {
        String binaryName = className.replace('/', '.');
        try {
            Class<?> enumClass = Class.forName(binaryName);
            if (!enumClass.isEnum()) {
                throw new IllegalArgumentException(binaryName + " is not an enum");
            }
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object resolved = Enum.valueOf((Class) enumClass, value);
            return resolved;
        } catch (ClassNotFoundException failure) {
            throw new IllegalArgumentException("Unknown Orca enum class " + binaryName, failure);
        }
    }

    @Keep
    void addOption(String key, ConfigOptionDef definition) {
        options.put(key, definition);
    }
}
