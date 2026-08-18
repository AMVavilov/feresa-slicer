// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ViewerRendererLifecycleTest {
    @Test
    fun currentRendererReadyIsAccepted() {
        val state = ViewerRendererLifecycle(generation = 4)

        assertEquals(ViewerRendererLifecycle(generation = 4, ready = true), state.onReady(4))
    }

    @Test
    fun rendererLossAdvancesGenerationAndRequiresFreshReady() {
        val ready = ViewerRendererLifecycle(generation = 2, ready = true)
        val recovering = ready.onRenderProcessGone(2)

        assertEquals(ViewerRendererLifecycle(generation = 3, ready = false), recovering)
        assertEquals(recovering, recovering.onReady(2))
        assertEquals(ViewerRendererLifecycle(generation = 3, ready = true), recovering.onReady(3))
    }

    @Test
    fun staleRendererLossCannotReplaceCurrentWebView() {
        val current = ViewerRendererLifecycle(generation = 7, ready = true)

        assertEquals(current, current.onRenderProcessGone(6))
    }
}
