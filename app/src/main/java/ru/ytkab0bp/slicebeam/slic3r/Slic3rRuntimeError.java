// SPDX-License-Identifier: AGPL-3.0-only
package ru.ytkab0bp.slicebeam.slic3r;

/** Checked exception class resolved by name from the native engine. */
public class Slic3rRuntimeError extends Exception {
    public Slic3rRuntimeError() {
    }

    public Slic3rRuntimeError(String message) {
        super(message);
    }

    public Slic3rRuntimeError(String message, Throwable cause) {
        super(message, cause);
    }

    public Slic3rRuntimeError(Throwable cause) {
        super(cause);
    }
}
