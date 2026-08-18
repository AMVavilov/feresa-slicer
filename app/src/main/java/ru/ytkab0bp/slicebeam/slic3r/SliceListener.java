// SPDX-License-Identifier: AGPL-3.0-only
package ru.ytkab0bp.slicebeam.slic3r;

/** Exact callback descriptor consumed by OrcaSlicer Mobile's JNI_OnLoad. */
public interface SliceListener {
    void onProgress(int progress, String text);
}
